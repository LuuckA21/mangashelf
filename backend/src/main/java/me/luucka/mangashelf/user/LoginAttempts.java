package me.luucka.mangashelf.user;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down repeated failed logins for the same account.
 *
 * <p>Without this the login endpoint answers as fast as it is asked, and a
 * short password falls to a script in minutes. Counting per username rather
 * than per address keeps it working behind a proxy, where every request
 * arrives from the same IP.
 *
 * <p>State is in memory: a restart forgives everyone, which is acceptable
 * for an instance with a handful of accounts and avoids a table whose only
 * purpose is to be written on every wrong password.
 */
@Component
public class LoginAttempts {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCKOUT = Duration.ofMinutes(15);

    private record Record(int failures, Instant until) {
    }

    private final Map<String, Record> byUser = new ConcurrentHashMap<>();

    public boolean isBlocked(String login) {
        Record record = byUser.get(key(login));
        if (record == null || record.until() == null) return false;
        if (Instant.now().isAfter(record.until())) {
            byUser.remove(key(login));
            return false;
        }
        return true;
    }

    public void recordFailure(String login) {
        byUser.compute(key(login), (k, current) -> {
            int failures = current == null ? 1 : current.failures() + 1;
            return new Record(failures,
                    failures >= MAX_FAILURES ? Instant.now().plus(LOCKOUT) : null);
        });
    }

    /** A successful login clears the count, so a typo costs nothing later. */
    public void recordSuccess(String login) {
        byUser.remove(key(login));
    }

    private String key(String login) {
        return login == null ? "" : login.trim().toLowerCase();
    }
}
