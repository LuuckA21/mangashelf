package me.luucka.mangashelf.user;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptsTest {

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
