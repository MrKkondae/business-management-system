package com.bms.backend.common.application.authentication;

import com.bms.backend.common.domain.authentication.NormalizedLoginId;
import com.bms.backend.global.security.LoginIdentifierProtector;
import com.bms.backend.system.application.authentication.AuthenticationAuditStore;
import com.bms.backend.system.application.authentication.AuthenticationAuthorizationSnapshot;
import com.bms.backend.system.application.authentication.AuthenticationStateStore;
import com.bms.backend.system.application.authentication.AuthenticationUser;
import com.bms.backend.system.application.authentication.AuthenticationUserQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class LoginAuthenticationService {

    private final AuthenticationUserQuery authenticationUserQuery;
    private final AuthenticationStateStore authenticationStateStore;
    private final AuthenticationPasswordVerifier passwordVerifier;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final LoginIdentifierProtector loginIdentifierProtector;
    private final AuthenticationAuditStore authenticationAuditStore;
    private final Clock clock;

    @Transactional
    public LoginAuthenticationResult authenticate(
            String loginId, String password, LoginAttemptContext context) {
        NormalizedLoginId normalizedLoginId = NormalizedLoginId.from(loginId);
        String protectedLoginId = loginIdentifierProtector.protect(normalizedLoginId);
        Instant attemptedInstant = clock.instant();
        LocalDateTime authenticatedAt =
                LocalDateTime.ofInstant(attemptedInstant, ZoneOffset.UTC);
        LoginRateLimitDecision rateLimit = loginAttemptLimiter.acquire(
                protectedLoginId, context.clientIpAddress(), attemptedInstant);
        if (!rateLimit.allowed()) {
            authenticationAuditStore.recordRateLimited(context, authenticatedAt);
            return LoginAuthenticationResult.rateLimited(rateLimit.retryAfterSeconds());
        }

        var candidate =
                authenticationUserQuery.findLoginCandidate(normalizedLoginId);

        if (candidate.isEmpty()) {
            passwordVerifier.verifyAgainstDummyHash(password);
            authenticationAuditStore.recordFailedLogin(
                    null,
                    protectedLoginId,
                    LoginFailureReason.USER_NOT_FOUND,
                    context,
                    authenticatedAt);
            return LoginAuthenticationResult.failed();
        }

        AuthenticationUser user = candidate.orElseThrow();
        boolean passwordMatched = passwordVerifier.matches(password, user.passwordHash());
        if (!user.isActive()) {
            authenticationAuditStore.recordFailedLogin(
                    user.userId(),
                    null,
                    LoginFailureReason.ACCOUNT_INACTIVE,
                    context,
                    authenticatedAt);
            return LoginAuthenticationResult.failed();
        }
        if (!passwordMatched) {
            var failedUpdate = authenticationStateStore.recordFailedLogin(
                    user.userId(),
                    user.securityVersion(),
                    user.passwordChangedAt(),
                    authenticatedAt);
            if (failedUpdate.isPresent()
                    && failedUpdate.orElseThrow().accountDeactivated()) {
                authenticationAuditStore.recordAccountDeactivated(
                        user.userId(), context, authenticatedAt);
            }
            authenticationAuditStore.recordFailedLogin(
                    user.userId(),
                    null,
                    LoginFailureReason.BAD_CREDENTIALS,
                    context,
                    authenticatedAt);
            return LoginAuthenticationResult.failed();
        }
        if (temporaryPasswordExpired(user, authenticatedAt)) {
            authenticationAuditStore.recordFailedLogin(
                    user.userId(),
                    null,
                    LoginFailureReason.TEMP_PWD_EXPIRED,
                    context,
                    authenticatedAt);
            return LoginAuthenticationResult.failed();
        }

        var currentState = authenticationStateStore.recordSuccessfulLogin(
                user.userId(),
                user.securityVersion(),
                user.passwordChangedAt(),
                authenticatedAt);
        if (currentState.isEmpty()) {
            authenticationAuditStore.recordFailedLogin(
                    user.userId(),
                    null,
                    LoginFailureReason.BAD_CREDENTIALS,
                    context,
                    authenticatedAt);
            return LoginAuthenticationResult.failed();
        }

        var state = currentState.orElseThrow();
        var authorization = state.passwordChangeRequired()
                ? new AuthenticationAuthorizationSnapshot(List.of(), List.of())
                : authenticationUserQuery.findAuthorizationSnapshot(user.userId());
        String accessLogId = authenticationAuditStore.recordSuccessfulLogin(
                user.userId(), context, authenticatedAt);
        resetRateLimitAfterCommit(protectedLoginId, context.clientIpAddress());
        return LoginAuthenticationResult.success(new AuthenticatedLogin(
                user.userId(),
                user.loginId().value(),
                user.displayName(),
                accessLogId,
                authorization,
                state.passwordChangeRequired(),
                user.temporaryPasswordExpiresAt(),
                state.passwordChangedAt(),
                state.securityVersion(),
                authenticatedAt));
    }

    private boolean temporaryPasswordExpired(
            AuthenticationUser user, LocalDateTime authenticatedAt) {
        if (!user.passwordChangeRequired()) {
            return false;
        }
        LocalDateTime expiresAt = user.temporaryPasswordExpiresAt();
        return expiresAt == null || !expiresAt.isAfter(authenticatedAt);
    }

    private void resetRateLimitAfterCommit(
            String protectedLoginId, String clientIpAddress) {
        Runnable reset =
                () -> loginAttemptLimiter.resetSuccessfulLogin(
                        protectedLoginId, clientIpAddress);
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            reset.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        reset.run();
                    }
                });
    }
}
