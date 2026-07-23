package com.bms.backend.common.application.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bms.backend.common.domain.authentication.NormalizedLoginId;
import com.bms.backend.global.security.LoginIdentifierProtector;
import com.bms.backend.system.application.authentication.AccountStatus;
import com.bms.backend.system.application.authentication.AuthenticationAuditStore;
import com.bms.backend.system.application.authentication.AuthenticationAuthorizationSnapshot;
import com.bms.backend.system.application.authentication.AuthenticationMenu;
import com.bms.backend.system.application.authentication.AuthenticationRole;
import com.bms.backend.system.application.authentication.AuthenticationSessionState;
import com.bms.backend.system.application.authentication.AuthenticationStateStore;
import com.bms.backend.system.application.authentication.AuthenticationUser;
import com.bms.backend.system.application.authentication.AuthenticationUserQuery;
import com.bms.backend.system.application.authentication.FailedLoginUpdate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginAuthenticationServiceTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 23, 9, 0);
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-23T09:00:00Z"), ZoneOffset.UTC);
    private static final LoginAttemptContext CONTEXT =
            new LoginAttemptContext("TRACE-01", "203.0.113.10", "JUnit");
    private static final String PROTECTED_LOGIN_ID = "hmac-sha256:test";

    private AuthenticationUserQuery userQuery;
    private AuthenticationStateStore stateStore;
    private AuthenticationPasswordVerifier passwordVerifier;
    private LoginAttemptLimiter attemptLimiter;
    private LoginIdentifierProtector identifierProtector;
    private AuthenticationAuditStore auditStore;
    private LoginAuthenticationService service;

    @BeforeEach
    void setUp() {
        userQuery = org.mockito.Mockito.mock(AuthenticationUserQuery.class);
        stateStore = org.mockito.Mockito.mock(AuthenticationStateStore.class);
        passwordVerifier = org.mockito.Mockito.mock(AuthenticationPasswordVerifier.class);
        attemptLimiter = org.mockito.Mockito.mock(LoginAttemptLimiter.class);
        identifierProtector = org.mockito.Mockito.mock(LoginIdentifierProtector.class);
        auditStore = org.mockito.Mockito.mock(AuthenticationAuditStore.class);
        when(identifierProtector.protect(any())).thenReturn(PROTECTED_LOGIN_ID);
        when(attemptLimiter.acquire(any(), any(), any()))
                .thenReturn(LoginRateLimitDecision.allow());
        service = new LoginAuthenticationService(
                userQuery,
                stateStore,
                passwordVerifier,
                attemptLimiter,
                identifierProtector,
                auditStore,
                CLOCK);
    }

    @Test
    void rejectsRateLimitedAttemptsBeforeUserLookupAndPasswordVerification() {
        when(attemptLimiter.acquire(PROTECTED_LOGIN_ID, CONTEXT.clientIpAddress(), CLOCK.instant()))
                .thenReturn(LoginRateLimitDecision.reject(60));

        var result = service.authenticate("admin", "plain-secret", CONTEXT);

        assertThat(result.status()).isEqualTo(LoginAuthenticationResult.Status.RATE_LIMITED);
        assertThat(result.retryAfterSeconds()).isEqualTo(60);
        verify(auditStore).recordRateLimited(CONTEXT, NOW);
        verify(userQuery, never()).findLoginCandidate(any());
        verify(passwordVerifier, never()).matches(any(), any());
        verify(passwordVerifier, never()).verifyAgainstDummyHash(any());
        verify(stateStore, never()).recordFailedLogin(any(), anyInt(), any(), any());
    }

    @Test
    void verifiesUnknownUsersAgainstDummyHashAndWritesProtectedAudit() {
        when(userQuery.findLoginCandidate(NormalizedLoginId.from("unknown")))
                .thenReturn(Optional.empty());

        var result = service.authenticate(" Unknown ", "plain-secret", CONTEXT);

        assertThat(result.status()).isEqualTo(LoginAuthenticationResult.Status.FAILED);
        verify(passwordVerifier).verifyAgainstDummyHash("plain-secret");
        verify(auditStore).recordFailedLogin(
                null, PROTECTED_LOGIN_ID, LoginFailureReason.USER_NOT_FOUND, CONTEXT, NOW);
        verify(stateStore, never()).recordFailedLogin(any(), anyInt(), any(), any());
    }

    @Test
    void recordsARealUsersPasswordFailure() {
        AuthenticationUser user = activeUser(false, null);
        when(userQuery.findLoginCandidate(NormalizedLoginId.from("admin")))
                .thenReturn(Optional.of(user));
        when(passwordVerifier.matches("wrong-secret", user.passwordHash())).thenReturn(false);
        when(stateStore.recordFailedLogin("USER-01", 3, user.passwordChangedAt(), NOW))
                .thenReturn(Optional.of(new FailedLoginUpdate(3, false, 3)));

        var result = service.authenticate("ADMIN", "wrong-secret", CONTEXT);

        assertThat(result.status()).isEqualTo(LoginAuthenticationResult.Status.FAILED);
        verify(stateStore).recordFailedLogin("USER-01", 3, user.passwordChangedAt(), NOW);
        verify(auditStore).recordFailedLogin(
                "USER-01", null, LoginFailureReason.BAD_CREDENTIALS, CONTEXT, NOW);
    }

    @Test
    void writesADeactivationSecurityEventOnTheSixthFailure() {
        AuthenticationUser user = activeUser(false, null);
        when(userQuery.findLoginCandidate(NormalizedLoginId.from("admin")))
                .thenReturn(Optional.of(user));
        when(passwordVerifier.matches("wrong-secret", user.passwordHash())).thenReturn(false);
        when(stateStore.recordFailedLogin("USER-01", 3, user.passwordChangedAt(), NOW))
                .thenReturn(Optional.of(new FailedLoginUpdate(6, true, 4)));

        service.authenticate("admin", "wrong-secret", CONTEXT);

        verify(auditStore).recordAccountDeactivated("USER-01", CONTEXT, NOW);
        verify(auditStore).recordFailedLogin(
                "USER-01", null, LoginFailureReason.BAD_CREDENTIALS, CONTEXT, NOW);
    }

    @Test
    void doesNotChangeFailureCountForInactiveAccounts() {
        AuthenticationUser inactive = new AuthenticationUser(
                "USER-01",
                "EMP-01",
                NormalizedLoginId.from("admin"),
                "관리자",
                "$argon2id$stored-hash",
                AccountStatus.INACTIVE,
                6,
                NOW.minusDays(1),
                false,
                null,
                4);
        when(userQuery.findLoginCandidate(NormalizedLoginId.from("admin")))
                .thenReturn(Optional.of(inactive));
        when(passwordVerifier.matches("plain-secret", inactive.passwordHash())).thenReturn(true);

        var result = service.authenticate("admin", "plain-secret", CONTEXT);

        assertThat(result.status()).isEqualTo(LoginAuthenticationResult.Status.FAILED);
        verify(auditStore).recordFailedLogin(
                "USER-01", null, LoginFailureReason.ACCOUNT_INACTIVE, CONTEXT, NOW);
        verify(stateStore, never()).recordFailedLogin(any(), anyInt(), any(), any());
        verify(stateStore, never()).recordSuccessfulLogin(any(), anyInt(), any(), any());
    }

    @Test
    void rejectsAnExpiredTemporaryPasswordWithoutIncreasingFailures() {
        AuthenticationUser user = activeUser(true, NOW);
        when(userQuery.findLoginCandidate(NormalizedLoginId.from("admin")))
                .thenReturn(Optional.of(user));
        when(passwordVerifier.matches("plain-secret", user.passwordHash())).thenReturn(true);

        var result = service.authenticate("admin", "plain-secret", CONTEXT);

        assertThat(result.status()).isEqualTo(LoginAuthenticationResult.Status.FAILED);
        verify(auditStore).recordFailedLogin(
                "USER-01", null, LoginFailureReason.TEMP_PWD_EXPIRED, CONTEXT, NOW);
        verify(stateStore, never()).recordFailedLogin(any(), anyInt(), any(), any());
        verify(stateStore, never()).recordSuccessfulLogin(any(), anyInt(), any(), any());
    }

    @Test
    void returnsASecretFreeSnapshotAndAccessLogIdAfterSuccess() {
        AuthenticationUser user = activeUser(false, null);
        AuthenticationSessionState state = new AuthenticationSessionState(
                user.userId(),
                AccountStatus.ACTIVE,
                false,
                user.securityVersion(),
                false,
                user.passwordChangedAt());
        AuthenticationAuthorizationSnapshot authorization =
                new AuthenticationAuthorizationSnapshot(
                        List.of(new AuthenticationRole("ROLE-01", "시스템관리자")),
                        List.of(new AuthenticationMenu(
                                "MENU-01", null, "시스템 관리", null, 10)));
        when(userQuery.findLoginCandidate(NormalizedLoginId.from("admin")))
                .thenReturn(Optional.of(user));
        when(passwordVerifier.matches("plain-secret", user.passwordHash())).thenReturn(true);
        when(stateStore.recordSuccessfulLogin(
                        user.userId(), user.securityVersion(), user.passwordChangedAt(), NOW))
                .thenReturn(Optional.of(state));
        when(userQuery.findAuthorizationSnapshot(user.userId())).thenReturn(authorization);
        when(auditStore.recordSuccessfulLogin(user.userId(), CONTEXT, NOW))
                .thenReturn("ACCESS-01");

        var result = service.authenticate("admin", "plain-secret", CONTEXT);
        AuthenticatedLogin authenticated = result.authenticatedLogin();

        assertThat(result.status()).isEqualTo(LoginAuthenticationResult.Status.SUCCESS);
        assertThat(authenticated.userId()).isEqualTo("USER-01");
        assertThat(authenticated.loginId()).isEqualTo("admin");
        assertThat(authenticated.accessLogId()).isEqualTo("ACCESS-01");
        assertThat(authenticated.authorization()).isEqualTo(authorization);
        assertThat(authenticated.securityVersion()).isEqualTo(3);
        assertThat(authenticated.authenticatedAt()).isEqualTo(NOW);
        assertThat(authenticated.toString()).doesNotContain(user.passwordHash());
        verify(attemptLimiter).resetSuccessfulLogin(
                PROTECTED_LOGIN_ID, CONTEXT.clientIpAddress());
    }

    @Test
    void returnsNoRolesOrMenusForAValidTemporaryPassword() {
        AuthenticationUser user = activeUser(true, NOW.plusHours(1));
        AuthenticationSessionState state = new AuthenticationSessionState(
                user.userId(),
                AccountStatus.ACTIVE,
                false,
                user.securityVersion(),
                true,
                user.passwordChangedAt());
        when(userQuery.findLoginCandidate(NormalizedLoginId.from("admin")))
                .thenReturn(Optional.of(user));
        when(passwordVerifier.matches("temporary-secret", user.passwordHash()))
                .thenReturn(true);
        when(stateStore.recordSuccessfulLogin(
                        user.userId(), user.securityVersion(), user.passwordChangedAt(), NOW))
                .thenReturn(Optional.of(state));
        when(auditStore.recordSuccessfulLogin(user.userId(), CONTEXT, NOW))
                .thenReturn("ACCESS-02");

        AuthenticatedLogin authenticated =
                service.authenticate("admin", "temporary-secret", CONTEXT)
                        .authenticatedLogin();

        assertThat(authenticated.passwordChangeRequired()).isTrue();
        assertThat(authenticated.authorization().roles()).isEmpty();
        assertThat(authenticated.authorization().menus()).isEmpty();
        verify(userQuery, never()).findAuthorizationSnapshot(any());
    }

    @Test
    void auditsCredentialsChangedDuringTheLoginAttemptAsFailure() {
        AuthenticationUser user = activeUser(false, null);
        when(userQuery.findLoginCandidate(NormalizedLoginId.from("admin")))
                .thenReturn(Optional.of(user));
        when(passwordVerifier.matches("plain-secret", user.passwordHash())).thenReturn(true);
        when(stateStore.recordSuccessfulLogin(
                        user.userId(), user.securityVersion(), user.passwordChangedAt(), NOW))
                .thenReturn(Optional.empty());

        var result = service.authenticate("admin", "plain-secret", CONTEXT);

        assertThat(result.status()).isEqualTo(LoginAuthenticationResult.Status.FAILED);
        verify(auditStore).recordFailedLogin(
                "USER-01", null, LoginFailureReason.BAD_CREDENTIALS, CONTEXT, NOW);
        verify(userQuery, never()).findAuthorizationSnapshot(any());
    }

    private AuthenticationUser activeUser(
            boolean passwordChangeRequired, LocalDateTime temporaryPasswordExpiresAt) {
        return new AuthenticationUser(
                "USER-01",
                "EMP-01",
                NormalizedLoginId.from("admin"),
                "관리자",
                "$argon2id$stored-hash",
                AccountStatus.ACTIVE,
                2,
                NOW.minusDays(1),
                passwordChangeRequired,
                temporaryPasswordExpiresAt,
                3);
    }
}
