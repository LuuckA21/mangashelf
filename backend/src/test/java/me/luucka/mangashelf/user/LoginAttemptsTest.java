package me.luucka.mangashelf.user;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptsTest {

    @Test
    void concurrentGuessesReserveTheAccountLimitBeforeAnyCheckFinishes() throws Exception {
        LoginAttempts attempts = new LoginAttempts();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(32)) {
            List<Future<LoginAttempts.Attempt>> futures = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                final int address = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    return attempts.tryAcquire("user:1", "address:" + address);
                }));
            }
            start.countDown();
            List<LoginAttempts.Attempt> admitted = new ArrayList<>();
            for (var future : futures) {
                var attempt = future.get(5, TimeUnit.SECONDS);
                if (attempt != null) admitted.add(attempt);
            }
            assertThat(admitted).hasSize(5);
            admitted.forEach(LoginAttempts.Attempt::close);
            assertThat(attempts.tryAcquire("USER:1", "another-address")).isNull();
        }
    }

    @Test
    void inFlightGuessesCountAlongsideEarlierFailuresForAnAddress() {
        LoginAttempts attempts = new LoginAttempts();
        for (int i = 0; i < 29; i++) attempts.recordFailure("old:" + i, "address");
        try (var last = attempts.tryAcquire("new:1", "address")) {
            assertThat(last).isNotNull();
            assertThat(attempts.tryAcquire("new:2", "address")).isNull();
        }
        assertThat(attempts.isBlocked("new:3", "address")).isTrue();
    }

    @Test
    void globalCapacityIsBoundedAndSuccessfulPermitsAreReleasedOnlyOnce() {
        LoginAttempts attempts = new LoginAttempts();
        List<LoginAttempts.Attempt> admitted = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            var attempt = attempts.tryAcquire("user:" + i, "address:" + i);
            assertThat(attempt).isNotNull();
            admitted.add(attempt);
        }
        assertThat(attempts.tryAcquire("extra", "extra")).isNull();
        admitted.forEach(attempt -> {
            attempt.succeeded();
            attempt.close();
            attempt.close();
        });
        assertThat(attempts.trackedUsers()).isZero();
        assertThat(attempts.trackedAddresses()).isZero();
        try (var next = attempts.tryAcquire("extra", "extra")) {
            assertThat(next).isNotNull();
            next.succeeded();
        }
    }

    @Test
    void activeGuessesSurviveCacheExpiryAndAnExceptionReleasesCapacity() {
        MutableTicker ticker = new MutableTicker();
        LoginAttempts attempts = new LoginAttempts(ticker);
        List<LoginAttempts.Attempt> admitted = new ArrayList<>();
        for (int i = 0; i < 5; i++) admitted.add(attempts.tryAcquire("account", "address"));
        ticker.advance(Duration.ofMinutes(16));
        assertThat(attempts.tryAcquire("account", "address")).isNull();
        admitted.forEach(LoginAttempts.Attempt::close);
        ticker.advance(Duration.ofMinutes(16));
        try (var next = attempts.tryAcquire("account", "address")) {
            assertThat(next).isNotNull();
            next.succeeded();
        }
    }

    @Test
    void fiveFailuresBlockAnAccountForFifteenMinutes() {
        MutableTicker ticker = new MutableTicker();
        LoginAttempts attempts = new LoginAttempts(ticker);

        for (int failure = 0; failure < 5; failure++) {
            attempts.recordFailure(" Luca ");
        }

        assertThat(attempts.isBlocked("luca")).isTrue();
        ticker.advance(Duration.ofMinutes(16));
        assertThat(attempts.isBlocked("LUCA")).isFalse();
        assertThat(attempts.trackedUsers()).isZero();
    }

    @Test
    void aSuccessfulLoginClearsEarlierFailures() {
        LoginAttempts attempts = new LoginAttempts(Ticker.systemTicker());
        for (int failure = 0; failure < 4; failure++) {
            attempts.recordFailure("luca");
        }

        attempts.recordSuccess("LUCA");

        assertThat(attempts.trackedUsers()).isZero();
        assertThat(attempts.isBlocked("luca")).isFalse();
    }

    @Test
    void anOldTypoExpiresEvenBeforeTheLockThreshold() {
        MutableTicker ticker = new MutableTicker();
        LoginAttempts attempts = new LoginAttempts(ticker);
        attempts.recordFailure("mistyped-user");

        ticker.advance(Duration.ofMinutes(16));

        assertThat(attempts.trackedUsers()).isZero();
    }

    @Test
    void randomUsernamesCannotGrowTheCacheWithoutLimit() {
        LoginAttempts attempts = new LoginAttempts(Ticker.systemTicker());

        for (int user = 0; user < 12_000; user++) {
            attempts.recordFailure("unknown-" + user);
        }

        assertThat(attempts.trackedUsers()).isLessThanOrEqualTo(10_000);
    }

    @Test
    void addressLimitAlsoStopsSprayingAcrossAccounts() {
        LoginAttempts attempts = new LoginAttempts(Ticker.systemTicker());
        for (int failure = 0; failure < 30; failure++) {
            attempts.recordFailure("user:" + failure, "203.0.113.10");
        }

        assertThat(attempts.isBlocked("different-account", "203.0.113.10")).isTrue();
        assertThat(attempts.isBlocked("different-account", "203.0.113.11")).isFalse();
        assertThat(attempts.trackedAddresses()).isEqualTo(1);
    }

    private static final class MutableTicker implements Ticker {
        private long nanos;

        @Override
        public long read() {
            return nanos;
        }

        void advance(Duration duration) {
            nanos += duration.toNanos();
        }
    }
}
