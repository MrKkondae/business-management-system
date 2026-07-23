package com.bms.backend.common.application.authentication;

import java.time.Instant;

public interface LoginAttemptLimiter {

    LoginRateLimitDecision acquire(
            String protectedLoginId, String clientIpAddress, Instant attemptedAt);

    void resetSuccessfulLogin(String protectedLoginId, String clientIpAddress);
}
