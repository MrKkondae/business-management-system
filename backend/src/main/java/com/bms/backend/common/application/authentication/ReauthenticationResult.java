package com.bms.backend.common.application.authentication;

import java.time.Instant;
import java.util.Objects;

public record ReauthenticationResult(Status status, Instant reauthenticatedAt, int retryAfterSeconds) {

    public ReauthenticationResult {
        Objects.requireNonNull(status, "status");
        if (status == Status.SUCCESS && reauthenticatedAt == null) {
            throw new IllegalArgumentException("AUTH_REAUTHENTICATED_AT_REQUIRED");
        }
        if (status != Status.SUCCESS && reauthenticatedAt != null) {
            throw new IllegalArgumentException("AUTH_REAUTHENTICATED_AT_NOT_ALLOWED");
        }
        if (status == Status.RATE_LIMITED && retryAfterSeconds < 1) {
            throw new IllegalArgumentException("AUTH_RETRY_AFTER_REQUIRED");
        }
        if (status != Status.RATE_LIMITED && retryAfterSeconds != 0) {
            throw new IllegalArgumentException("AUTH_RETRY_AFTER_NOT_ALLOWED");
        }
    }

    public enum Status {
        SUCCESS,
        FAILED,
        RATE_LIMITED
    }

    public static ReauthenticationResult success(Instant occurredAt) {
        return new ReauthenticationResult(Status.SUCCESS, occurredAt, 0);
    }

    public static ReauthenticationResult failed() {
        return new ReauthenticationResult(Status.FAILED, null, 0);
    }

    public static ReauthenticationResult rateLimited(int retryAfterSeconds) {
        return new ReauthenticationResult(Status.RATE_LIMITED, null, retryAfterSeconds);
    }
}
