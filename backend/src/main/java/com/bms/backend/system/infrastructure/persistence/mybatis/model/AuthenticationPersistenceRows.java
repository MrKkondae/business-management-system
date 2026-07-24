package com.bms.backend.system.infrastructure.persistence.mybatis.model;

import java.time.LocalDateTime;

public final class AuthenticationPersistenceRows {

    private AuthenticationPersistenceRows() {}

    public record User(
            String userId,
            String employeeId,
            String loginId,
            String displayName,
            String passwordHash,
            String accountStatusCode,
            int loginFailureCount,
            LocalDateTime passwordChangedAt,
            String passwordChangeRequiredFlag,
            LocalDateTime temporaryPasswordExpiresAt,
            int securityVersion) {}

    public record SessionState(
            String userId,
            String accountStatusCode,
            String deletedFlag,
            int securityVersion,
            String passwordChangeRequiredFlag,
            LocalDateTime passwordChangedAt) {}

    public record ReauthenticationCredential(
            String userId,
            String passwordHash,
            String accountStatusCode,
            String deletedFlag,
            int securityVersion,
            String passwordChangeRequiredFlag,
            LocalDateTime passwordChangedAt) {}

    public record LockedUser(
            String userId,
            String accountStatusCode,
            String deletedFlag,
            int securityVersion,
            String passwordChangeRequiredFlag,
            LocalDateTime passwordChangedAt,
            int loginFailureCount) {}

    public record InitialRegistrationAccount(
            String userId,
            String loginId,
            String displayName,
            String passwordHash,
            String accountStatusCode,
            String deletedFlag,
            String passwordChangeRequiredFlag,
            LocalDateTime passwordChangedAt,
            LocalDateTime temporaryPasswordExpiresAt,
            int securityVersion) {}
}
