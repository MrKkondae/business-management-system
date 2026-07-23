package com.bms.backend.system.application.authentication;

import com.bms.backend.common.domain.authentication.NormalizedLoginId;
import java.time.LocalDateTime;
import java.util.Objects;

public final class AuthenticationUser {

    private final String userId;
    private final String employeeId;
    private final NormalizedLoginId loginId;
    private final String displayName;
    private final String passwordHash;
    private final AccountStatus accountStatus;
    private final int loginFailureCount;
    private final LocalDateTime passwordChangedAt;
    private final boolean passwordChangeRequired;
    private final LocalDateTime temporaryPasswordExpiresAt;
    private final int securityVersion;

    public AuthenticationUser(
            String userId,
            String employeeId,
            NormalizedLoginId loginId,
            String displayName,
            String passwordHash,
            AccountStatus accountStatus,
            int loginFailureCount,
            LocalDateTime passwordChangedAt,
            boolean passwordChangeRequired,
            LocalDateTime temporaryPasswordExpiresAt,
            int securityVersion) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.employeeId = employeeId;
        this.loginId = Objects.requireNonNull(loginId, "loginId");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.accountStatus = Objects.requireNonNull(accountStatus, "accountStatus");
        this.loginFailureCount = loginFailureCount;
        this.passwordChangedAt = Objects.requireNonNull(passwordChangedAt, "passwordChangedAt");
        this.passwordChangeRequired = passwordChangeRequired;
        this.temporaryPasswordExpiresAt = temporaryPasswordExpiresAt;
        this.securityVersion = securityVersion;
    }

    public String userId() {
        return userId;
    }

    public String employeeId() {
        return employeeId;
    }

    public NormalizedLoginId loginId() {
        return loginId;
    }

    public String displayName() {
        return displayName;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public AccountStatus accountStatus() {
        return accountStatus;
    }

    public int loginFailureCount() {
        return loginFailureCount;
    }

    public LocalDateTime passwordChangedAt() {
        return passwordChangedAt;
    }

    public boolean passwordChangeRequired() {
        return passwordChangeRequired;
    }

    public LocalDateTime temporaryPasswordExpiresAt() {
        return temporaryPasswordExpiresAt;
    }

    public int securityVersion() {
        return securityVersion;
    }

    public boolean isActive() {
        return accountStatus == AccountStatus.ACTIVE;
    }
}
