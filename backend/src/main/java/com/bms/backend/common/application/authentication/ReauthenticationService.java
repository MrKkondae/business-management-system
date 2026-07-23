package com.bms.backend.common.application.authentication;

import com.bms.backend.system.application.authentication.AuthenticationAuditStore;
import com.bms.backend.system.application.authentication.AuthenticationUserQuery;
import com.bms.backend.system.application.authentication.ReauthenticationAuditOutcome;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReauthenticationService {

    private final AuthenticationUserQuery userQuery;
    private final AuthenticationPasswordVerifier passwordVerifier;
    private final ReauthenticationAttemptLimiter attemptLimiter;
    private final AuthenticationAuditStore auditStore;
    private final Clock clock;

    public ReauthenticationService(
            AuthenticationUserQuery userQuery,
            AuthenticationPasswordVerifier passwordVerifier,
            ReauthenticationAttemptLimiter attemptLimiter,
            AuthenticationAuditStore auditStore,
            Clock clock) {
        this.userQuery = userQuery;
        this.passwordVerifier = passwordVerifier;
        this.attemptLimiter = attemptLimiter;
        this.auditStore = auditStore;
        this.clock = clock;
    }

    @Transactional
    public ReauthenticationResult reauthenticate(
            LoginSession session, String password, LoginAttemptContext context) {
        Instant attemptedAt = clock.instant();
        LocalDateTime occurredAt = LocalDateTime.ofInstant(attemptedAt, ZoneOffset.UTC);
        LoginRateLimitDecision rateLimit =
                attemptLimiter.acquire(session.accessLogId(), attemptedAt);
        if (!rateLimit.allowed()) {
            auditStore.recordReauthentication(
                    session.userId(),
                    ReauthenticationAuditOutcome.RATE_LIMITED,
                    context,
                    occurredAt);
            return ReauthenticationResult.rateLimited(rateLimit.retryAfterSeconds());
        }

        var credential = userQuery.findReauthenticationCredential(session.userId());
        boolean passwordMatched;
        if (credential.isPresent()) {
            passwordMatched = passwordVerifier.matches(
                    password, credential.orElseThrow().passwordHash());
        } else {
            passwordVerifier.verifyAgainstDummyHash(password);
            passwordMatched = false;
        }
        boolean matched = credential.isPresent()
                && credential.orElseThrow().matches(session)
                && passwordMatched;
        if (!matched) {
            auditStore.recordReauthentication(
                    session.userId(),
                    ReauthenticationAuditOutcome.FAILED,
                    context,
                    occurredAt);
            return ReauthenticationResult.failed();
        }

        auditStore.recordReauthentication(
                session.userId(),
                ReauthenticationAuditOutcome.SUCCEEDED,
                context,
                occurredAt);
        return ReauthenticationResult.success(attemptedAt);
    }
}
