package com.bms.backend.common.application.authentication;

import com.bms.backend.system.application.authentication.AuthenticationAuthorizationSnapshot;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

public record InitialRegistrationResult(
        AuthenticationAuthorizationSnapshot authorization,
        LocalDateTime passwordChangedAt,
        int securityVersion,
        Instant completedAt) {

    public InitialRegistrationResult {
        Objects.requireNonNull(authorization, "authorization");
        Objects.requireNonNull(passwordChangedAt, "passwordChangedAt");
        Objects.requireNonNull(completedAt, "completedAt");
        if (securityVersion < 1) {
            throw new IllegalArgumentException("AUTH_SECURITY_VERSION_INVALID");
        }
    }
}
