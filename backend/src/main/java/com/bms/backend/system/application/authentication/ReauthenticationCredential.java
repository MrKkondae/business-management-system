package com.bms.backend.system.application.authentication;

import com.bms.backend.common.application.authentication.LoginSession;
import java.time.LocalDateTime;
import java.util.Objects;

public record ReauthenticationCredential(
        String userId,
        String passwordHash,
        AccountStatus accountStatus,
        boolean deleted,
        int securityVersion,
        boolean passwordChangeRequired,
        LocalDateTime passwordChangedAt) {

    public ReauthenticationCredential {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(accountStatus, "accountStatus");
        Objects.requireNonNull(passwordChangedAt, "passwordChangedAt");
    }

    public boolean matches(LoginSession session) {
        return !deleted
                && accountStatus == AccountStatus.ACTIVE
                && userId.equals(session.userId())
                && securityVersion == session.securityVersion()
                && passwordChangeRequired == session.passwordChangeRequired()
                && passwordChangedAt.equals(session.passwordChangedAt());
    }

    @Override
    public String toString() {
        return "ReauthenticationCredential[userId=%s, passwordHash=***]"
                .formatted(userId);
    }
}
