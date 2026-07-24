package com.bms.backend.common.application.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bms.backend.global.error.ApiErrorCode;
import com.bms.backend.global.error.ApplicationException;
import com.bms.backend.system.application.authentication.AccountStatus;
import com.bms.backend.system.application.authentication.AuthenticationAuditStore;
import com.bms.backend.system.application.authentication.AuthenticationAuthorizationSnapshot;
import com.bms.backend.system.application.authentication.AuthenticationRole;
import com.bms.backend.system.application.authentication.AuthenticationUserQuery;
import com.bms.backend.system.application.authentication.InitialRegistrationAccount;
import com.bms.backend.system.application.authentication.InitialRegistrationStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InitialRegistrationServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-24T01:00:00Z");
    private static final LocalDateTime PASSWORD_CHANGED_AT =
            LocalDateTime.of(2026, 7, 23, 1, 0);
    private static final LoginAttemptContext CONTEXT =
            new LoginAttemptContext("TRACE-01", "203.0.113.10", "JUnit");

    private InitialRegistrationStore registrationStore;
    private AuthenticationUserQuery userQuery;
    private AuthenticationPasswordVerifier passwordVerifier;
    private NewPasswordPolicy passwordPolicy;
    private AuthenticationAuditStore auditStore;
    private InitialRegistrationService service;

    @BeforeEach
    void setUp() {
        registrationStore = org.mockito.Mockito.mock(InitialRegistrationStore.class);
        userQuery = org.mockito.Mockito.mock(AuthenticationUserQuery.class);
        passwordVerifier = org.mockito.Mockito.mock(AuthenticationPasswordVerifier.class);
        passwordPolicy = org.mockito.Mockito.mock(NewPasswordPolicy.class);
        auditStore = org.mockito.Mockito.mock(AuthenticationAuditStore.class);
        service = new InitialRegistrationService(
                registrationStore,
                userQuery,
                passwordVerifier,
                passwordPolicy,
                auditStore,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void completesPasswordAndContactUpdateThenReturnsPromotionState() {
        LoginSession session = limitedSession();
        InitialRegistrationAccount account = account(true, NOW.plusSeconds(60));
        var authorization = new AuthenticationAuthorizationSnapshot(
                List.of(new AuthenticationRole("ROLE-01", "시스템관리자")), List.of());
        when(registrationStore.lockAccount("USER-01")).thenReturn(Optional.of(account));
        when(passwordPolicy.isSatisfiedBy("River!Glass82", "admin", "관리자"))
                .thenReturn(true);
        when(passwordVerifier.matches("River!Glass82", "TEMP-HASH")).thenReturn(false);
        when(passwordVerifier.encode("River!Glass82")).thenReturn("NEW-HASH");
        when(registrationStore.complete(
                        account,
                        "NEW-HASH",
                        "admin@example.com",
                        "010-1234-5678",
                        LocalDateTime.of(2026, 7, 24, 1, 0)))
                .thenReturn(4);
        when(userQuery.findAuthorizationSnapshot("USER-01")).thenReturn(authorization);

        InitialRegistrationResult result = service.complete(
                session,
                new InitialRegistrationCommand(
                        "River!Glass82",
                        "River!Glass82",
                        " admin@example.com ",
                        " 010-1234-5678 "),
                CONTEXT);

        assertThat(result.authorization()).isEqualTo(authorization);
        assertThat(result.securityVersion()).isEqualTo(4);
        assertThat(result.passwordChangedAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 24, 1, 0));
        assertThat(result.completedAt()).isEqualTo(NOW);
        verify(auditStore).recordInitialRegistrationCompleted(
                "USER-01", CONTEXT, LocalDateTime.of(2026, 7, 24, 1, 0));
    }

    @Test
    void rejectsConfirmationPolicyAndCurrentTemporaryPasswordReuse() {
        assertThatThrownBy(() -> service.complete(
                        limitedSession(),
                        new InitialRegistrationCommand(
                                "River!Glass82", "Different!Glass82", null, null),
                        CONTEXT))
                .isInstanceOf(IllegalArgumentException.class);
        verify(registrationStore, never()).lockAccount(any());

        InitialRegistrationAccount account = account(true, NOW.plusSeconds(60));
        when(registrationStore.lockAccount("USER-01")).thenReturn(Optional.of(account));
        when(passwordPolicy.isSatisfiedBy("River!Glass82", "admin", "관리자"))
                .thenReturn(true);
        when(passwordVerifier.matches("River!Glass82", "TEMP-HASH")).thenReturn(true);

        assertThatThrownBy(() -> service.complete(
                        limitedSession(),
                        new InitialRegistrationCommand(
                                "River!Glass82", "River!Glass82", null, null),
                        CONTEXT))
                .isInstanceOf(IllegalArgumentException.class);
        verify(passwordVerifier, never()).encode(any());
        verify(registrationStore, never())
                .complete(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsExpiredOrChangedLimitedSessionState() {
        when(registrationStore.lockAccount("USER-01"))
                .thenReturn(Optional.of(account(true, NOW)));

        assertThatThrownBy(() -> service.complete(
                        limitedSession(),
                        new InitialRegistrationCommand(
                                "River!Glass82", "River!Glass82", null, null),
                        CONTEXT))
                .isInstanceOfSatisfying(
                        ApplicationException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ApiErrorCode.AUTH_AUTHENTICATION_REQUIRED));
        verify(passwordVerifier, never()).encode(any());
    }

    @Test
    void rejectsInitialRegistrationFromANormalSession() {
        LoginSession normal = limitedSession().promoted(
                new AuthenticationAuthorizationSnapshot(List.of(), List.of()),
                LocalDateTime.of(2026, 7, 24, 1, 0),
                4,
                NOW);

        assertThatThrownBy(() -> service.complete(
                        normal,
                        new InitialRegistrationCommand(
                                "River!Glass82", "River!Glass82", null, null),
                        CONTEXT))
                .isInstanceOfSatisfying(
                        ApplicationException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ApiErrorCode.COMMON_INVALID_STATE));
    }

    @Test
    void secretBearingObjectsHidePasswordsAndHashes() {
        assertThat(new InitialRegistrationCommand(
                                "River!Glass82",
                                "River!Glass82",
                                "admin@example.com",
                                "010-1234-5678")
                        .toString())
                .doesNotContain(
                        "River!Glass82", "admin@example.com", "010-1234-5678");
        assertThat(account(true, NOW.plusSeconds(60)).toString())
                .doesNotContain("TEMP-HASH");
    }

    private LoginSession limitedSession() {
        return new LoginSession(
                "USER-01",
                "admin",
                "관리자",
                "ACCESS-01",
                new AuthenticationAuthorizationSnapshot(List.of(), List.of()),
                true,
                PASSWORD_CHANGED_AT,
                3,
                NOW.minusSeconds(60),
                NOW.plusSeconds(300),
                NOW.minusSeconds(60));
    }

    private InitialRegistrationAccount account(
            boolean passwordChangeRequired, Instant temporaryPasswordExpiresAt) {
        return new InitialRegistrationAccount(
                "USER-01",
                "admin",
                "관리자",
                "TEMP-HASH",
                AccountStatus.ACTIVE,
                false,
                passwordChangeRequired,
                PASSWORD_CHANGED_AT,
                LocalDateTime.ofInstant(temporaryPasswordExpiresAt, ZoneOffset.UTC),
                3);
    }
}
