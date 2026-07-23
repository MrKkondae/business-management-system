package com.bms.backend.common.application.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class InMemoryLoginAttemptLimiterTests {

    private static final Instant NOW = Instant.parse("2026-07-23T09:00:00Z");

    @Test
    void blocksTheSixthAttemptForTheSameLoginAndIpForSixtySeconds() {
        InMemoryLoginAttemptLimiter limiter = new InMemoryLoginAttemptLimiter();

        for (int count = 0; count < 5; count++) {
            assertThat(limiter.acquire("login-hmac", "203.0.113.10", NOW).allowed())
                    .isTrue();
        }

        assertThat(limiter.acquire("login-hmac", "203.0.113.10", NOW))
                .isEqualTo(LoginRateLimitDecision.reject(60));
        assertThat(limiter.acquire(
                        "login-hmac", "203.0.113.10", NOW.plusSeconds(59)))
                .isEqualTo(LoginRateLimitDecision.reject(1));
        assertThat(limiter.acquire(
                        "login-hmac", "203.0.113.10", NOW.plusSeconds(60)).allowed())
                .isTrue();
    }

    @Test
    void blocksTheThirtyFirstAttemptFromOneIpAcrossDifferentLoginIds() {
        InMemoryLoginAttemptLimiter limiter = new InMemoryLoginAttemptLimiter();

        for (int count = 0; count < 30; count++) {
            assertThat(limiter.acquire("login-" + count, "203.0.113.10", NOW).allowed())
                    .isTrue();
        }

        assertThat(limiter.acquire("login-30", "203.0.113.10", NOW))
                .isEqualTo(LoginRateLimitDecision.reject(60));
    }

    @Test
    void successfulLoginResetsOnlyTheLoginAndIpBucket() {
        InMemoryLoginAttemptLimiter limiter = new InMemoryLoginAttemptLimiter();
        for (int count = 0; count < 5; count++) {
            limiter.acquire("successful-login", "203.0.113.10", NOW);
        }

        limiter.resetSuccessfulLogin("successful-login", "203.0.113.10");

        assertThat(limiter.acquire("successful-login", "203.0.113.10", NOW).allowed())
                .isTrue();
        for (int count = 0; count < 24; count++) {
            assertThat(limiter.acquire("other-" + count, "203.0.113.10", NOW).allowed())
                    .isTrue();
        }
        assertThat(limiter.acquire("global-limit", "203.0.113.10", NOW).allowed())
                .isFalse();
    }

    @Test
    void enforcesTheLimitAtomicallyUnderConcurrentRequests() throws Exception {
        InMemoryLoginAttemptLimiter limiter = new InMemoryLoginAttemptLimiter();
        ExecutorService executor = Executors.newFixedThreadPool(12);
        CountDownLatch start = new CountDownLatch(1);
        List<java.util.concurrent.Future<LoginRateLimitDecision>> futures =
                new ArrayList<>();
        try {
            for (int count = 0; count < 20; count++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return limiter.acquire("login-hmac", "203.0.113.10", NOW);
                }));
            }
            start.countDown();

            long allowed = 0;
            for (var future : futures) {
                if (future.get(5, TimeUnit.SECONDS).allowed()) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(5);
        } finally {
            executor.shutdownNow();
        }
    }
}
