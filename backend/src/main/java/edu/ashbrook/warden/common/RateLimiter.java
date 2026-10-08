package edu.ashbrook.warden.common;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * In-memory token buckets that refill continuously.
 * ponytail: single-instance only; move the buckets to the database or a shared cache if Warden ever runs on two nodes.
 */
@Component
public class RateLimiter {

    private static final long IDLE_EVICTION_NANOS = Duration.ofMinutes(5).toNanos();

    private record Bucket(double tokens, long updatedNanos) {
    }

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /** Takes one token from {@code key}'s bucket ({@code capacity} tokens, fully refilled every {@code refillPeriod}). */
    public boolean tryAcquire(String key, int capacity, Duration refillPeriod) {
        long now = System.nanoTime();
        boolean[] acquired = {false};
        buckets.compute(key, (k, bucket) -> {
            double tokens = bucket == null ? capacity
                    : Math.min(capacity, bucket.tokens() + (now - bucket.updatedNanos()) * capacity / (double) refillPeriod.toNanos());
            if (tokens >= 1) {
                acquired[0] = true;
                tokens -= 1;
            }
            return new Bucket(tokens, now);
        });
        return acquired[0];
    }

    /** Buckets idle for five minutes are full again, so dropping them changes nothing but memory. */
    @Scheduled(fixedDelay = 300_000)
    void evictIdle() {
        long cutoff = System.nanoTime() - IDLE_EVICTION_NANOS;
        buckets.values().removeIf(b -> b.updatedNanos() < cutoff);
    }
}
