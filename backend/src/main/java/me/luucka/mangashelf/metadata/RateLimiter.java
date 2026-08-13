package me.luucka.mangashelf.metadata;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Blocks callers so that no more than a fixed number of requests leave in
 * any sixty-second window.
 *
 * <p>A sliding window rather than a simple counter reset: AniList also runs
 * a burst limiter, and a counter that clears on the minute boundary would
 * let the whole allowance go out in one second and trip it.
 */
public class RateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private final int permitsPerWindow;
    private final Deque<Long> issued = new ArrayDeque<>();

    public RateLimiter(int permitsPerWindow) {
        this.permitsPerWindow = permitsPerWindow;
    }

    /** Waits until a permit is free, then takes it. */
    public synchronized void acquire() {
        while (true) {
            long now = System.currentTimeMillis();
            while (!issued.isEmpty() && now - issued.peekFirst() >= WINDOW_MILLIS) {
                issued.pollFirst();
            }
            if (issued.size() < permitsPerWindow) {
                issued.addLast(now);
                return;
            }
            long waitFor = WINDOW_MILLIS - (now - issued.peekFirst()) + 50;
            try {
                wait(waitFor);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrotto in attesa del rate limiter", e);
            }
        }
    }
}
