package me.luucka.mangashelf.user;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PreDestroy;
import me.luucka.mangashelf.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Bounded per-instance limits and queue; no email addresses or tokens in logs. */
@Component
public class AccountEmailRequests {
    private static final Logger LOG = LoggerFactory.getLogger(AccountEmailRequests.class);
    private final Cache<String, Integer> addresses = Caffeine.newBuilder().maximumSize(10_000)
            .expireAfterWrite(Duration.ofHours(1)).build();
    private final Cache<String, Boolean> recipients = Caffeine.newBuilder().maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(1)).build();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100), new ThreadPoolExecutor.AbortPolicy());
    private final AccountEmailService service;
    private final int maximumAttempts;

    public AccountEmailRequests(AccountEmailService service,
                                @Value("${app.security.email-attempts-per-hour:20}") int maximumAttempts) {
        if (maximumAttempts < 1) throw new IllegalArgumentException("Email attempt limit must be positive");
        this.service = service;
        this.maximumAttempts = maximumAttempts;
    }

    public void limit(String address) {
        boolean[] allowed = {false};
        addresses.asMap().compute(address, (key, count) -> {
            if (count == null || count < maximumAttempts) {
                allowed[0] = true;
                return count == null ? 1 : count + 1;
            }
            return count;
        });
        if (!allowed[0]) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "too_many_attempts");
    }

    public void submit(String email, boolean verification) {
        String key = AccountEmailService.hash(email);
        if (recipients.asMap().putIfAbsent(key, true) != null) return;
        try {
            executor.execute(() -> {
                try {
                    service.requestEmail(email, verification);
                } catch (RuntimeException ex) {
                    // SMTP exception messages may include addresses and message contents.
                    LOG.warn("Account email delivery failed; check SMTP connectivity and configuration");
                }
            });
        } catch (RejectedExecutionException ex) {
            recipients.invalidate(key);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "email_unavailable");
        }
    }

    @PreDestroy
    public void close() {
        executor.shutdown();
    }
}
