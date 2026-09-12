package me.luucka.mangashelf.metadata;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RateLimiterTest {
    @Test
    void fullWindowFailsImmediatelyAndAllowsRequestsAfterExpiry() {
        AtomicLong now = new AtomicLong();
        RateLimiter limiter = new RateLimiter(1, now::get);
        limiter.acquire();
        AniListException error = catchThrowableOfType(limiter::acquire, AniListException.class);
        assertThat(error.getRetryAfterSeconds()).isEqualTo(60);
        now.addAndGet(Duration.ofSeconds(60).toNanos());
        assertThatCode(limiter::acquire).doesNotThrowAnyException();
    }

    @Test
    void shorterConcurrentFailureDoesNotShortenCooldown() {
        AtomicLong now = new AtomicLong();
        RateLimiter limiter = new RateLimiter(10, now::get);
        limiter.pause("anilist_rate_limited", 120);
        now.addAndGet(Duration.ofSeconds(10).toNanos());
        limiter.pause("anilist_unavailable", 30);
        AniListException error = catchThrowableOfType(limiter::acquire, AniListException.class);
        assertThat(error).hasMessage("anilist_rate_limited");
        assertThat(error.getRetryAfterSeconds()).isEqualTo(110);
    }
}
