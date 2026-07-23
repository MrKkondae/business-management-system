package com.bms.backend.common.application.authentication;

import java.time.Instant;

public interface ReauthenticationAttemptLimiter {

    LoginRateLimitDecision acquire(String sessionKey, Instant attemptedAt);
}
