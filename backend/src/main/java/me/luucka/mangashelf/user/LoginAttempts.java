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
 * short password falls to a script in minutes. Accounts are counted by their
 * canonical database identity and client addresses have a wider, independent
 * ceiling, limiting both alias-based attacks and sprays across many accounts.
 *
 * <p>State is in memory: a restart forgives everyone, which is acceptable
 * for an instance with a handful of accounts and avoids a table whose only
 * purpose is to be written on every wrong password.
 */
@Component
public class LoginAttempts {

    private static final int MAX_ACCOUNT_FAILURES = 5;
    private static final int MAX_ADDRESS_FAILURES = 30;
    private static final int MAX_TRACKED_KEYS = 10_000;
    private static final Duration LOCKOUT = Duration.ofMinutes(15);

    private record Record(int failures, long blockedUntilNanos) {
    }

    private final Cache<String, Record> byAccount;
    private final Cache<String, Record> byAddress;
    private final Ticker ticker;

    public LoginAttempts() {
        this(Ticker.systemTicker());
    }

    LoginAttempts(Ticker ticker) {
        this.ticker = ticker;
        this.byAccount = newCache();
        this.byAddress = newCache();
    }

    private Cache<String, Record> newCache() {
        return Caffeine.newBuilder()
                // Random usernames cannot grow this process for ever. At the
                // ceiling Caffeine evicts cold entries while active accounts
                // remain protected by their recent access.
                .maximumSize(MAX_TRACKED_KEYS)
                // Failed attempts below the lock threshold need forgetting
                // too, otherwise every typo would remain until a restart.
                .expireAfterWrite(LOCKOUT)
                .ticker(ticker)
                .build();
    }

    /**
     * Checks both the resolved account and the client address. The account
     * key is canonical (the database id), so alternating between username
     * and email cannot obtain a second set of guesses.
     */
    public boolean isBlocked(String accountKey, String clientAddress) {
        return blocked(byAccount, key(accountKey))
                || blocked(byAddress, key(clientAddress));
    }

    private boolean blocked(Cache<String, Record> cache, String key) {
        Record record = cache.getIfPresent(key);
        if (record == null || record.blockedUntilNanos() == 0) return false;
        if (ticker.read() >= record.blockedUntilNanos()) {
            cache.invalidate(key);
            return false;
        }
        return true;
    }

    public void recordFailure(String accountKey, String clientAddress) {
        long now = ticker.read();
        record(byAccount, key(accountKey), MAX_ACCOUNT_FAILURES, now);
        record(byAddress, key(clientAddress), MAX_ADDRESS_FAILURES, now);
    }

    private void record(Cache<String, Record> cache, String key, int maximum, long now) {
        cache.asMap().compute(key, (ignored, current) -> {
            int failures = current == null ? 1 : current.failures() + 1;
            return new Record(failures,
                    failures >= maximum ? now + LOCKOUT.toNanos() : 0);
        });
    }

    /** A successful login clears that account's count, so a typo costs nothing later. */
    public void recordSuccess(String accountKey) {
        byAccount.invalidate(key(accountKey));
    }

    // Convenience methods retained for focused tests and maintenance calls.
    boolean isBlocked(String accountKey) {
        return blocked(byAccount, key(accountKey));
    }

    void recordFailure(String accountKey) {
        record(byAccount, key(accountKey), MAX_ACCOUNT_FAILURES, ticker.read());
    }

    /** Visible to the focused unit test, not part of the login API. */
    long trackedUsers() {
        byAccount.cleanUp();
        return byAccount.estimatedSize();
    }

    long trackedAddresses() {
        byAddress.cleanUp();
        return byAddress.estimatedSize();
    }

    private String key(String login) {
        return login == null ? "" : login.trim().toLowerCase(Locale.ROOT);
    }
}
