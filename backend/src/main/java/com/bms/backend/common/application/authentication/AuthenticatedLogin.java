package com.bms.backend.common.application.authentication;

import com.bms.backend.system.application.authentication.AuthenticationAuthorizationSnapshot;
import java.time.LocalDateTime;
import java.util.Objects;

public record AuthenticatedLogin(
        String userId,
        String loginId,
        String displayName,
        String accessLogId,
        AuthenticationAuthorizationSnapshot authorization,
        boolean passwordChangeRequired,
        LocalDateTime temporaryPasswordExpiresAt,
        LocalDateTime passwordChangedAt,
        int securityVersion,
        LocalDateTime authenticatedAt) {

    public AuthenticatedLogin {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(loginId, "loginId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(accessLogId, "accessLogId");
        Objects.requireNonNull(authorization, "authorization");
        Objects.requireNonNull(passwordChangedAt, "passwordChangedAt");
        Objects.requireNonNull(authenticatedAt, "authenticatedAt");
    }
}
