package com.bms.backend.common.application.authentication;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class InMemoryReauthenticationAttemptLimiter
        implements ReauthenticationAttemptLimiter {

    static final int LIMIT = 5;
    static final Duration WINDOW = Duration.ofMinutes(1);
    static final Duration BLOCK_DURATION = Duration.ofSeconds(60);

    private final Map<String, Bucket> buckets = new HashMap<>();
    private long acquisitionCount;

    @Override
    public synchronized LoginRateLimitDecision acquire(String sessionKey, Instant attemptedAt) {
        if (++acquisitionCount % 256 == 0) {
            purge(attemptedAt);
        }
        Bucket bucket = buckets.computeIfAbsent(sessionKey, ignored -> new Bucket(attemptedAt));
        int remaining = bucket.remainingBlockSeconds(attemptedAt);
        if (remaining > 0) {
            return LoginRateLimitDecision.reject(remaining);
        }
        if (bucket.record(attemptedAt) > LIMIT) {
            bucket.blockedUntil = attemptedAt.plus(BLOCK_DURATION);
            return LoginRateLimitDecision.reject((int) BLOCK_DURATION.toSeconds());
        }
        return LoginRateLimitDecision.allow();
    }

    private void purge(Instant now) {
        Iterator<Bucket> iterator = buckets.values().iterator();
        while (iterator.hasNext()) {
            Bucket bucket = iterator.next();
            if ((bucket.blockedUntil == null || !now.isBefore(bucket.blockedUntil))
                    && !now.isBefore(bucket.windowStartedAt.plus(WINDOW))) {
                iterator.remove();
            }
        }
    }

    private static final class Bucket {
        private Instant windowStartedAt;
        private int count;
        private Instant blockedUntil;

        private Bucket(Instant windowStartedAt) {
            this.windowStartedAt = windowStartedAt;
        }

        private int record(Instant now) {
            if (!now.isBefore(windowStartedAt.plus(WINDOW))) {
                windowStartedAt = now;
                count = 0;
                blockedUntil = null;
            }
            return ++count;
        }

        private int remainingBlockSeconds(Instant now) {
            if (blockedUntil == null || !now.isBefore(blockedUntil)) {
                return 0;
            }
            long millis = Duration.between(now, blockedUntil).toMillis();
            return (int) Math.max(1, (millis + 999) / 1000);
        }
    }
}
