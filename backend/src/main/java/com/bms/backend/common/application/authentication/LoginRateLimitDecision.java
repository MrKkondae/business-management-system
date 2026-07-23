package com.bms.backend.common.application.authentication;

public record LoginRateLimitDecision(boolean allowed, int retryAfterSeconds) {

    public LoginRateLimitDecision {
        if (allowed && retryAfterSeconds != 0) {
            throw new IllegalArgumentException("LOGIN_RATE_LIMIT_ALLOWED_RETRY_INVALID");
        }
        if (!allowed && retryAfterSeconds < 1) {
            throw new IllegalArgumentException("LOGIN_RATE_LIMIT_REJECTED_RETRY_INVALID");
        }
    }

    public static LoginRateLimitDecision allow() {
        return new LoginRateLimitDecision(true, 0);
    }

    public static LoginRateLimitDecision reject(int retryAfterSeconds) {
        return new LoginRateLimitDecision(false, retryAfterSeconds);
    }
}
