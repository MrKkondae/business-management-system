package com.bms.backend.system.application.authentication;

import com.bms.backend.common.application.authentication.LoginSession;
import java.time.LocalDateTime;
import java.util.Objects;

public record InitialRegistrationAccount(
        String userId,
        String loginId,
        String displayName,
        String passwordHash,
        AccountStatus accountStatus,
        boolean deleted,
        boolean passwordChangeRequired,
        LocalDateTime passwordChangedAt,
        LocalDateTime temporaryPasswordExpiresAt,
        int securityVersion) {

    public InitialRegistrationAccount {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(loginId, "loginId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(accountStatus, "accountStatus");
        Objects.requireNonNull(passwordChangedAt, "passwordChangedAt");
    }

    public boolean isCompletableWith(LoginSession session, LocalDateTime now) {
        return userId.equals(session.userId())
                && accountStatus == AccountStatus.ACTIVE
                && !deleted
                && passwordChangeRequired
                && temporaryPasswordExpiresAt != null
                && now.isBefore(temporaryPasswordExpiresAt)
                && securityVersion == session.securityVersion()
                && passwordChangedAt.equals(session.passwordChangedAt());
    }

    @Override
    public String toString() {
        return "InitialRegistrationAccount[userId=%s, loginId=%s, displayName=%s, "
                        + "passwordHash=***, accountStatus=%s, deleted=%s, "
                        + "passwordChangeRequired=%s, passwordChangedAt=%s, "
                        + "temporaryPasswordExpiresAt=%s, securityVersion=%s]"
                .formatted(
                        userId,
                        loginId,
                        displayName,
                        accountStatus,
                        deleted,
                        passwordChangeRequired,
                        passwordChangedAt,
                        temporaryPasswordExpiresAt,
                        securityVersion);
    }
}
