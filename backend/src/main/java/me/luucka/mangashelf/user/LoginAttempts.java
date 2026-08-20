package me.luucka.mangashelf.user;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * Slows down repeated failed logins for the same account.
 *
 * <p>Without this the login endpoint answers as fast as it is asked, and a
 * short password falls to a script in minutes. Counting per submitted account
 * identifier rather than per address keeps it working behind a proxy, where
 * every request arrives from the same IP.
 *
 * <p>State is in memory: a restart forgives everyone, which is acceptable
 * for an instance with a handful of accounts and avoids a table whose only
 * purpose is to be written on every wrong password.
 */
@Component
public class LoginAttempts {

    private static final int MAX_FAILURES = 5;
    private static final int MAX_TRACKED_USERS = 10_000;
    private static final Duration LOCKOUT = Duration.ofMinutes(15);

    private record Record(int failures, long blockedUntilNanos) {
    }

    private final Cache<String, Record> byUser;
    private final Ticker ticker;

    public LoginAttempts() {
        this(Ticker.systemTicker());
    }

    LoginAttempts(Ticker ticker) {
        this.ticker = ticker;
        this.byUser = Caffeine.newBuilder()
                // Random usernames cannot grow this process for ever. At the
                // ceiling Caffeine evicts cold entries while active accounts
                // remain protected by their recent access.
                .maximumSize(MAX_TRACKED_USERS)
                // Failed attempts below the lock threshold need forgetting
                // too, otherwise every typo would remain until a restart.
                .expireAfterWrite(LOCKOUT)
                .ticker(ticker)
                .build();
    }

    public boolean isBlocked(String login) {
        String key = key(login);
        Record record = byUser.getIfPresent(key);
        if (record == null || record.blockedUntilNanos() == 0) return false;
        if (ticker.read() >= record.blockedUntilNanos()) {
            byUser.invalidate(key);
            return false;
        }
        return true;
    }

    public void recordFailure(String login) {
        long now = ticker.read();
        byUser.asMap().compute(key(login), (ignored, current) -> {
            int failures = current == null ? 1 : current.failures() + 1;
            return new Record(failures,
                    failures >= MAX_FAILURES ? now + LOCKOUT.toNanos() : 0);
        });
    }

    /** A successful login clears the count, so a typo costs nothing later. */
    public void recordSuccess(String login) {
        byUser.invalidate(key(login));
    }

    /** Visible to the focused unit test, not part of the login API. */
    long trackedUsers() {
        byUser.cleanUp();
        return byUser.estimatedSize();
    }

    private String key(String login) {
        return login == null ? "" : login.trim().toLowerCase(Locale.ROOT);
    }
}
