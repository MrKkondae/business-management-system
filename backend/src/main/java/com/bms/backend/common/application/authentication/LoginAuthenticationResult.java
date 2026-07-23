package com.bms.backend.common.application.authentication;

public record LoginAuthenticationResult(
        Status status, AuthenticatedLogin authenticatedLogin, int retryAfterSeconds) {

    public enum Status {
        SUCCESS,
        FAILED,
        RATE_LIMITED
    }

    public LoginAuthenticationResult {
        if (status == Status.SUCCESS && authenticatedLogin == null) {
            throw new IllegalArgumentException("LOGIN_SUCCESS_RESULT_REQUIRED");
        }
        if (status != Status.SUCCESS && authenticatedLogin != null) {
            throw new IllegalArgumentException("LOGIN_FAILURE_RESULT_INVALID");
        }
        if (status == Status.RATE_LIMITED && retryAfterSeconds < 1) {
            throw new IllegalArgumentException("LOGIN_RATE_LIMIT_RETRY_REQUIRED");
        }
        if (status != Status.RATE_LIMITED && retryAfterSeconds != 0) {
            throw new IllegalArgumentException("LOGIN_RETRY_NOT_ALLOWED");
        }
    }

    public static LoginAuthenticationResult success(AuthenticatedLogin authenticatedLogin) {
        return new LoginAuthenticationResult(Status.SUCCESS, authenticatedLogin, 0);
    }

    public static LoginAuthenticationResult failed() {
        return new LoginAuthenticationResult(Status.FAILED, null, 0);
    }

    public static LoginAuthenticationResult rateLimited(int retryAfterSeconds) {
        return new LoginAuthenticationResult(
                Status.RATE_LIMITED, null, retryAfterSeconds);
    }
}
