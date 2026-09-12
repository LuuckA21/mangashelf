package me.luucka.mangashelf.metadata;

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;

/** Non-blocking sliding window and cooldown shared by searches and imports. */
public class RateLimiter {
    private static final long WINDOW = Duration.ofMinutes(1).toNanos();
    private final int permitsPerWindow;
    private final Ticker ticker;
    private final Deque<Long> issued = new ArrayDeque<>();
    private Long pausedUntil;
    private String pausedCode;

    public RateLimiter(int permitsPerWindow) {
        this(permitsPerWindow, Ticker.systemTicker());
    }

    RateLimiter(int permitsPerWindow, Ticker ticker) {
        if (permitsPerWindow < 1) throw new IllegalArgumentException("Rate limit must be positive");
        this.permitsPerWindow = permitsPerWindow;
        this.ticker = ticker;
    }

    public synchronized void acquire() {
        long now = ticker.read();
        if (pausedUntil != null && pausedUntil - now > 0) {
            throw new AniListException(pausedCode, seconds(pausedUntil - now));
        }
        pausedUntil = null;
        while (!issued.isEmpty() && now - issued.peekFirst() >= WINDOW) issued.removeFirst();
        if (issued.size() >= permitsPerWindow) {
            throw new AniListException("anilist_rate_limited", seconds(WINDOW - (now - issued.peekFirst())));
        }
        issued.addLast(now);
    }

    public synchronized AniListException pause(String code, int seconds) {
        long now = ticker.read();
        long deadline = now + Duration.ofSeconds(seconds).toNanos();
        if (pausedUntil == null || deadline - pausedUntil > 0) {
            pausedUntil = deadline;
            pausedCode = code;
        }
        return new AniListException(pausedCode, seconds(pausedUntil - now));
    }

    private int seconds(long nanos) {
        return (int) Math.max(1, (nanos + 999_999_999L) / 1_000_000_000L);
    }
}
