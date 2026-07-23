package com.bms.backend.common.application.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class InMemoryReauthenticationAttemptLimiterTests {

    private static final Instant NOW = Instant.parse("2026-07-23T09:00:00Z");

    @Test
    void blocksTheSixthRequestPerSessionForSixtySeconds() {
        var limiter = new InMemoryReauthenticationAttemptLimiter();
        for (int count = 0; count < 5; count++) {
            assertThat(limiter.acquire("ACCESS-01", NOW).allowed()).isTrue();
        }

        assertThat(limiter.acquire("ACCESS-01", NOW))
                .isEqualTo(LoginRateLimitDecision.reject(60));
        assertThat(limiter.acquire("ACCESS-01", NOW.plusSeconds(59)))
                .isEqualTo(LoginRateLimitDecision.reject(1));
        assertThat(limiter.acquire("ACCESS-01", NOW.plusSeconds(60)).allowed()).isTrue();
    }

    @Test
    void differentSessionsHaveIndependentBuckets() {
        var limiter = new InMemoryReauthenticationAttemptLimiter();
        for (int count = 0; count < 6; count++) {
            limiter.acquire("ACCESS-01", NOW);
        }

        assertThat(limiter.acquire("ACCESS-02", NOW).allowed()).isTrue();
    }
}
