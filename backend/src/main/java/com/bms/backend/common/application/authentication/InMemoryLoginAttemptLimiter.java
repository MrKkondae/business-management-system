package com.bms.backend.common.application.authentication;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class InMemoryLoginAttemptLimiter implements LoginAttemptLimiter {

    static final int LOGIN_AND_IP_LIMIT = 5;
    static final int IP_LIMIT = 30;
    static final Duration WINDOW = Duration.ofMinutes(1);
    static final Duration BLOCK_DURATION = Duration.ofSeconds(60);

    private final Map<LoginAndIpKey, AttemptBucket> loginAndIpBuckets = new HashMap<>();
    private final Map<String, AttemptBucket> ipBuckets = new HashMap<>();
    private long acquisitionCount;

    @Override
    public synchronized LoginRateLimitDecision acquire(
            String protectedLoginId, String clientIpAddress, Instant attemptedAt) {
        if (++acquisitionCount % 256 == 0) {
            purgeExpired(attemptedAt);
        }

        LoginAndIpKey loginAndIpKey =
                new LoginAndIpKey(protectedLoginId, clientIpAddress);
        AttemptBucket loginAndIp =
                loginAndIpBuckets.computeIfAbsent(
                        loginAndIpKey, ignored -> new AttemptBucket(attemptedAt));
        AttemptBucket ip =
                ipBuckets.computeIfAbsent(
                        clientIpAddress, ignored -> new AttemptBucket(attemptedAt));

        int blockedSeconds = Math.max(
                loginAndIp.remainingBlockSeconds(attemptedAt),
                ip.remainingBlockSeconds(attemptedAt));
        if (blockedSeconds > 0) {
            return LoginRateLimitDecision.reject(blockedSeconds);
        }

        boolean loginAndIpExceeded =
                loginAndIp.record(attemptedAt) > LOGIN_AND_IP_LIMIT;
        boolean ipExceeded = ip.record(attemptedAt) > IP_LIMIT;
        if (loginAndIpExceeded) {
            loginAndIp.block(attemptedAt);
        }
        if (ipExceeded) {
            ip.block(attemptedAt);
        }
        if (loginAndIpExceeded || ipExceeded) {
            return LoginRateLimitDecision.reject((int) BLOCK_DURATION.toSeconds());
        }
        return LoginRateLimitDecision.allow();
    }

    @Override
    public synchronized void resetSuccessfulLogin(
            String protectedLoginId, String clientIpAddress) {
        loginAndIpBuckets.remove(new LoginAndIpKey(protectedLoginId, clientIpAddress));
    }

    private void purgeExpired(Instant now) {
        purgeExpired(loginAndIpBuckets.values().iterator(), now);
        purgeExpired(ipBuckets.values().iterator(), now);
    }

    private void purgeExpired(Iterator<AttemptBucket> buckets, Instant now) {
        while (buckets.hasNext()) {
            if (buckets.next().expiredAt(now)) {
                buckets.remove();
            }
        }
    }

    private record LoginAndIpKey(String protectedLoginId, String clientIpAddress) {}

    private static final class AttemptBucket {

        private Instant windowStartedAt;
        private int count;
        private Instant blockedUntil;

        private AttemptBucket(Instant windowStartedAt) {
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

        private void block(Instant now) {
            blockedUntil = now.plus(BLOCK_DURATION);
        }

        private int remainingBlockSeconds(Instant now) {
            if (blockedUntil == null || !now.isBefore(blockedUntil)) {
                return 0;
            }
            long millis = Duration.between(now, blockedUntil).toMillis();
            return (int) Math.max(1, (millis + 999) / 1000);
        }

        private boolean expiredAt(Instant now) {
            boolean blockExpired = blockedUntil == null || !now.isBefore(blockedUntil);
            return blockExpired && !now.isBefore(windowStartedAt.plus(WINDOW));
        }
    }
}
