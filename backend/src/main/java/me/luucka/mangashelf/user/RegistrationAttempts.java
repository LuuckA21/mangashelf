package me.luucka.mangashelf.user;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Bounds the expensive BCrypt work and account creation exposed by registration. */
@Component
public class RegistrationAttempts {

    private static final int MAX_TRACKED_ADDRESSES = 10_000;

    private final Cache<String, Integer> byAddress;
    private final int maximumAttempts;

    public RegistrationAttempts(
            @Value("${app.security.registration-attempts-per-hour:5}") int maximumAttempts) {
        if (maximumAttempts < 1) {
            throw new IllegalArgumentException(
                    "app.security.registration-attempts-per-hour must be positive");
        }
        this.maximumAttempts = maximumAttempts;
        this.byAddress = Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_ADDRESSES)
                .expireAfterWrite(Duration.ofHours(1))
                .build();
    }

    /** Consumes one attempt and returns false once the address has reached its quota. */
    public boolean tryAcquire(String clientAddress) {
        String key = clientAddress == null || clientAddress.isBlank()
                ? "unknown" : clientAddress.trim();
        int attempts = byAddress.asMap().merge(key, 1, Integer::sum);
        return attempts <= maximumAttempts;
    }
}
