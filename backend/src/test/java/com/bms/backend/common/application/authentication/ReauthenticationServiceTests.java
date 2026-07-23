package com.bms.backend.common.application.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bms.backend.system.application.authentication.AccountStatus;
import com.bms.backend.system.application.authentication.AuthenticationAuditStore;
import com.bms.backend.system.application.authentication.AuthenticationAuthorizationSnapshot;
import com.bms.backend.system.application.authentication.AuthenticationUserQuery;
import com.bms.backend.system.application.authentication.ReauthenticationAuditOutcome;
import com.bms.backend.system.application.authentication.ReauthenticationCredential;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReauthenticationServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-23T09:05:00Z");
    private static final LocalDateTime LOCAL_NOW =
            LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    private static final LoginAttemptContext CONTEXT =
            new LoginAttemptContext("TRACE-01", "203.0.113.10", "JUnit");

    private AuthenticationUserQuery userQuery;
    private AuthenticationPasswordVerifier passwordVerifier;
    private ReauthenticationAttemptLimiter limiter;
    private AuthenticationAuditStore auditStore;
    private ReauthenticationService service;

    @BeforeEach
    void setUp() {
        userQuery = org.mockito.Mockito.mock(AuthenticationUserQuery.class);
        passwordVerifier = org.mockito.Mockito.mock(AuthenticationPasswordVerifier.class);
        limiter = org.mockito.Mockito.mock(ReauthenticationAttemptLimiter.class);
        auditStore = org.mockito.Mockito.mock(AuthenticationAuditStore.class);
        when(limiter.acquire("ACCESS-01", NOW)).thenReturn(LoginRateLimitDecision.allow());
        service = new ReauthenticationService(
                userQuery,
                passwordVerifier,
                limiter,
                auditStore,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void succeedsWithoutChangingLoginFailureState() {
        when(userQuery.findReauthenticationCredential("USER-01"))
                .thenReturn(Optional.of(credential(3)));
        when(passwordVerifier.matches("current-secret", "$argon2id$hash")).thenReturn(true);

        var result = service.reauthenticate(session(), "current-secret", CONTEXT);

        assertThat(result.status()).isEqualTo(ReauthenticationResult.Status.SUCCESS);
        assertThat(result.reauthenticatedAt()).isEqualTo(NOW);
        verify(auditStore).recordReauthentication(
                "USER-01", ReauthenticationAuditOutcome.SUCCEEDED, CONTEXT, LOCAL_NOW);
    }

    @Test
    void passwordFailureKeepsTheSessionAndWritesGenericSecurityAudit() {
        when(userQuery.findReauthenticationCredential("USER-01"))
                .thenReturn(Optional.of(credential(3)));
        when(passwordVerifier.matches("wrong", "$argon2id$hash")).thenReturn(false);

        var result = service.reauthenticate(session(), "wrong", CONTEXT);

        assertThat(result.status()).isEqualTo(ReauthenticationResult.Status.FAILED);
        verify(auditStore).recordReauthentication(
                "USER-01", ReauthenticationAuditOutcome.FAILED, CONTEXT, LOCAL_NOW);
    }

    @Test
    void changedSecurityVersionFailsEvenWhenTheOldPasswordMatches() {
        when(userQuery.findReauthenticationCredential("USER-01"))
                .thenReturn(Optional.of(credential(4)));
        when(passwordVerifier.matches("old-secret", "$argon2id$hash")).thenReturn(true);

        assertThat(service.reauthenticate(session(), "old-secret", CONTEXT).status())
                .isEqualTo(ReauthenticationResult.Status.FAILED);
        verify(passwordVerifier).matches("old-secret", "$argon2id$hash");
    }

    @Test
    void missingCredentialUsesDummyHash() {
        when(userQuery.findReauthenticationCredential("USER-01"))
                .thenReturn(Optional.empty());

        assertThat(service.reauthenticate(session(), "secret", CONTEXT).status())
                .isEqualTo(ReauthenticationResult.Status.FAILED);
        verify(passwordVerifier).verifyAgainstDummyHash("secret");
    }

    @Test
    void sixthRequestIsRateLimitedBeforeDatabaseOrPasswordWork() {
        when(limiter.acquire("ACCESS-01", NOW))
                .thenReturn(LoginRateLimitDecision.reject(60));

        var result = service.reauthenticate(session(), "secret", CONTEXT);

        assertThat(result.status()).isEqualTo(ReauthenticationResult.Status.RATE_LIMITED);
        assertThat(result.retryAfterSeconds()).isEqualTo(60);
        verify(auditStore).recordReauthentication(
                "USER-01", ReauthenticationAuditOutcome.RATE_LIMITED, CONTEXT, LOCAL_NOW);
        verify(userQuery, never()).findReauthenticationCredential(any());
        verify(passwordVerifier, never()).matches(any(), any());
    }

    private LoginSession session() {
        return new LoginSession(
                "USER-01",
                "admin",
                "관리자",
                "ACCESS-01",
                new AuthenticationAuthorizationSnapshot(List.of(), List.of()),
                false,
                LocalDateTime.of(2026, 7, 22, 9, 0),
                3,
                Instant.parse("2026-07-23T09:00:00Z"),
                Instant.parse("2026-07-23T17:00:00Z"),
                Instant.parse("2026-07-23T09:00:00Z"));
    }

    private ReauthenticationCredential credential(int securityVersion) {
        return new ReauthenticationCredential(
                "USER-01",
                "$argon2id$hash",
                AccountStatus.ACTIVE,
                false,
                securityVersion,
                false,
                LocalDateTime.of(2026, 7, 22, 9, 0));
    }
}
