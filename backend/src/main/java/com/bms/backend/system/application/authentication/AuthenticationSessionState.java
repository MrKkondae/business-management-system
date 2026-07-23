package com.bms.backend.system.application.authentication;

import java.time.LocalDateTime;
import java.util.Objects;

public record AuthenticationSessionState(
        String userId,
        AccountStatus accountStatus,
        boolean deleted,
        int securityVersion,
        boolean passwordChangeRequired,
        LocalDateTime passwordChangedAt) {

    public AuthenticationSessionState {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(accountStatus, "accountStatus");
        Objects.requireNonNull(passwordChangedAt, "passwordChangedAt");
    }

    public boolean isUsableWith(int sessionSecurityVersion) {
        return !deleted
                && accountStatus == AccountStatus.ACTIVE
                && securityVersion == sessionSecurityVersion;
    }
}
