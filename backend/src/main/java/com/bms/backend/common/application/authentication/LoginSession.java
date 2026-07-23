package com.bms.backend.common.application.authentication;

import com.bms.backend.system.application.authentication.AuthenticationAuthorizationSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

public record LoginSession(
        String userId,
        String loginId,
        String displayName,
        String accessLogId,
        AuthenticationAuthorizationSnapshot authorization,
        boolean passwordChangeRequired,
        LocalDateTime passwordChangedAt,
        int securityVersion,
        Instant lastUserActivityAt,
        Instant absoluteExpiresAt,
        Instant reauthenticatedAt) {

    public static final int NORMAL_IDLE_TIMEOUT_SECONDS = 900;
    public static final int LIMITED_IDLE_TIMEOUT_SECONDS = 600;

    public LoginSession {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(loginId, "loginId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(accessLogId, "accessLogId");
        Objects.requireNonNull(authorization, "authorization");
        Objects.requireNonNull(passwordChangedAt, "passwordChangedAt");
        Objects.requireNonNull(lastUserActivityAt, "lastUserActivityAt");
        Objects.requireNonNull(absoluteExpiresAt, "absoluteExpiresAt");
        Objects.requireNonNull(reauthenticatedAt, "reauthenticatedAt");
    }

    public static LoginSession from(AuthenticatedLogin login) {
        Instant authenticatedAt = login.authenticatedAt().toInstant(ZoneOffset.UTC);
        Instant absoluteExpiresAt = authenticatedAt.plus(
                login.passwordChangeRequired() ? Duration.ofMinutes(30) : Duration.ofHours(8));
        if (login.passwordChangeRequired() && login.temporaryPasswordExpiresAt() != null) {
            Instant passwordExpiresAt =
                    login.temporaryPasswordExpiresAt().toInstant(ZoneOffset.UTC);
            if (passwordExpiresAt.isBefore(absoluteExpiresAt)) {
                absoluteExpiresAt = passwordExpiresAt;
            }
        }
        return new LoginSession(
                login.userId(),
                login.loginId(),
                login.displayName(),
                login.accessLogId(),
                login.authorization(),
                login.passwordChangeRequired(),
                login.passwordChangedAt(),
                login.securityVersion(),
                authenticatedAt,
                absoluteExpiresAt,
                authenticatedAt);
    }

    public int idleTimeoutSeconds() {
        return passwordChangeRequired
                ? LIMITED_IDLE_TIMEOUT_SECONDS
                : NORMAL_IDLE_TIMEOUT_SECONDS;
    }

    public Expiration expirationAt(Instant now) {
        if (!now.isBefore(absoluteExpiresAt)) {
            return Expiration.ABSOLUTE;
        }
        if (!now.isBefore(lastUserActivityAt.plusSeconds(idleTimeoutSeconds()))) {
            return Expiration.IDLE;
        }
        return Expiration.NONE;
    }

    public LoginSession withActivityAt(Instant activityAt) {
        return new LoginSession(
                userId,
                loginId,
                displayName,
                accessLogId,
                authorization,
                passwordChangeRequired,
                passwordChangedAt,
                securityVersion,
                activityAt,
                absoluteExpiresAt,
                reauthenticatedAt);
    }

    public LoginSession withReauthenticatedAt(Instant occurredAt) {
        return new LoginSession(
                userId,
                loginId,
                displayName,
                accessLogId,
                authorization,
                passwordChangeRequired,
                passwordChangedAt,
                securityVersion,
                lastUserActivityAt,
                absoluteExpiresAt,
                occurredAt);
    }

    public boolean isRecentlyReauthenticated(Instant now) {
        return !now.isBefore(reauthenticatedAt)
                && now.isBefore(reauthenticatedAt.plus(Duration.ofMinutes(10)));
    }

    public enum Expiration {
        NONE,
        IDLE,
        ABSOLUTE
    }
}
