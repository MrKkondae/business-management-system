package com.bms.backend.common.application.authentication;

import com.bms.backend.global.error.ApiErrorCode;
import com.bms.backend.global.error.ApplicationException;
import com.bms.backend.system.application.authentication.AuthenticationAuditStore;
import com.bms.backend.system.application.authentication.AuthenticationUserQuery;
import com.bms.backend.system.application.authentication.InitialRegistrationAccount;
import com.bms.backend.system.application.authentication.InitialRegistrationStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InitialRegistrationService {

    private final InitialRegistrationStore registrationStore;
    private final AuthenticationUserQuery userQuery;
    private final AuthenticationPasswordVerifier passwordVerifier;
    private final NewPasswordPolicy passwordPolicy;
    private final AuthenticationAuditStore auditStore;
    private final Clock clock;

    public InitialRegistrationService(
            InitialRegistrationStore registrationStore,
            AuthenticationUserQuery userQuery,
            AuthenticationPasswordVerifier passwordVerifier,
            NewPasswordPolicy passwordPolicy,
            AuthenticationAuditStore auditStore,
            Clock clock) {
        this.registrationStore = registrationStore;
        this.userQuery = userQuery;
        this.passwordVerifier = passwordVerifier;
        this.passwordPolicy = passwordPolicy;
        this.auditStore = auditStore;
        this.clock = clock;
    }

    @Transactional
    public InitialRegistrationResult complete(
            LoginSession session,
            InitialRegistrationCommand command,
            LoginAttemptContext context) {
        if (!session.passwordChangeRequired()) {
            throw new ApplicationException(ApiErrorCode.COMMON_INVALID_STATE);
        }
        validateConfirmation(command);

        Instant completedAt = clock.instant();
        LocalDateTime completedDateTime =
                LocalDateTime.ofInstant(completedAt, ZoneOffset.UTC);
        InitialRegistrationAccount account = registrationStore
                .lockAccount(session.userId())
                .filter(candidate -> candidate.isCompletableWith(session, completedDateTime))
                .orElseThrow(() ->
                        new ApplicationException(ApiErrorCode.AUTH_AUTHENTICATION_REQUIRED));

        if (!passwordPolicy.isSatisfiedBy(
                        command.newPassword(), account.loginId(), account.displayName())
                || passwordVerifier.matches(
                        command.newPassword(), account.passwordHash())) {
            throw new IllegalArgumentException("AUTH_NEW_PASSWORD_POLICY_INVALID");
        }

        String newPasswordHash = passwordVerifier.encode(command.newPassword());
        int newSecurityVersion = registrationStore.complete(
                account,
                newPasswordHash,
                optional(command.emailAddress()),
                optional(command.mobileNumber()),
                completedDateTime);
        var authorization = userQuery.findAuthorizationSnapshot(session.userId());
        auditStore.recordInitialRegistrationCompleted(
                session.userId(), context, completedDateTime);
        return new InitialRegistrationResult(
                authorization, completedDateTime, newSecurityVersion, completedAt);
    }

    private void validateConfirmation(InitialRegistrationCommand command) {
        if (command.newPassword() == null
                || !command.newPassword().equals(command.newPasswordConfirmation())) {
            throw new IllegalArgumentException("AUTH_NEW_PASSWORD_CONFIRMATION_MISMATCH");
        }
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
