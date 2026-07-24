package com.bms.backend.system.infrastructure.persistence.mybatis.adapter;

import com.bms.backend.common.application.authentication.LoginAttemptContext;
import com.bms.backend.common.application.authentication.LoginFailureReason;
import com.bms.backend.global.persistence.MonotonicUlidGenerator;
import com.bms.backend.system.application.authentication.AuthenticationAuditStore;
import com.bms.backend.system.application.authentication.ReauthenticationAuditOutcome;
import com.bms.backend.system.infrastructure.persistence.mybatis.mapper.AuthenticationAuditMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisAuthenticationAuditStore implements AuthenticationAuditStore {

    private static final String SYSTEM_ACTOR = "SYSTEM";

    private final AuthenticationAuditMapper mapper;
    private final MonotonicUlidGenerator ulidGenerator;

    @Override
    public String recordSuccessfulLogin(
            String userId, LoginAttemptContext context, LocalDateTime occurredAt) {
        String accessLogId = ulidGenerator.next();
        insertAccessLog(accessLogId, userId, null, null, true, context, occurredAt);
        return accessLogId;
    }

    @Override
    public void recordFailedLogin(
            String userId,
            String protectedLoginId,
            LoginFailureReason failureReason,
            LoginAttemptContext context,
            LocalDateTime occurredAt) {
        insertAccessLog(
                ulidGenerator.next(),
                userId,
                userId == null ? protectedLoginId : null,
                failureReason.name(),
                false,
                context,
                occurredAt);
    }

    @Override
    public void recordAccountDeactivated(
            String userId, LoginAttemptContext context, LocalDateTime occurredAt) {
        insertSystemLog(
                "USER_DEACTIVATED_LOGIN_FAILURE",
                "SUCCESS",
                userId,
                "accountStatus ACTIVE->INACTIVE; reason LOGIN_FAILURE; securityVersion incremented",
                context,
                occurredAt);
    }

    @Override
    public void recordRateLimited(LoginAttemptContext context, LocalDateTime occurredAt) {
        insertSystemLog(
                "LOGIN_RATE_LIMITED",
                "FAILURE",
                null,
                "login request rate limit exceeded",
                context,
                occurredAt);
    }

    @Override
    public void recordLogout(
            String accessLogId,
            String userId,
            String logoutTypeCode,
            LocalDateTime occurredAt) {
        mapper.updateLogout(accessLogId, userId, logoutTypeCode, occurredAt);
    }

    @Override
    public void recordReauthentication(
            String userId,
            ReauthenticationAuditOutcome outcome,
            LoginAttemptContext context,
            LocalDateTime occurredAt) {
        insertSystemLog(
                "REAUTHENTICATION_" + outcome.name(),
                outcome == ReauthenticationAuditOutcome.SUCCEEDED ? "SUCCESS" : "FAILURE",
                userId,
                "current password reauthentication " + outcome.name().toLowerCase(),
                context,
                occurredAt);
    }

    @Override
    public void recordInitialRegistrationCompleted(
            String userId, LoginAttemptContext context, LocalDateTime occurredAt) {
        insertSystemLog(
                "INITIAL_REGISTRATION_COMPLETED",
                "SUCCESS",
                userId,
                "initial registration completed; password changed; securityVersion incremented",
                context,
                occurredAt);
    }

    private void insertAccessLog(
            String accessLogId,
            String userId,
            String protectedLoginId,
            String failureReasonCode,
            boolean successful,
            LoginAttemptContext context,
            LocalDateTime occurredAt) {
        requireSingleInsert(
                mapper.insertAccessLog(
                        accessLogId,
                        userId,
                        occurredAt,
                        context.clientIpAddress(),
                        successful ? "Y" : "N",
                        SYSTEM_ACTOR,
                        failureReasonCode,
                        context.requestTraceId(),
                        protectedLoginId,
                        context.userAgent()),
                "AUTH_ACCESS_LOG_INSERT_COUNT_INVALID");
    }

    private void insertSystemLog(
            String eventTypeCode,
            String resultCode,
            String targetUserId,
            String changeSummary,
            LoginAttemptContext context,
            LocalDateTime occurredAt) {
        requireSingleInsert(
                mapper.insertSystemLog(
                        ulidGenerator.next(),
                        occurredAt,
                        SYSTEM_ACTOR,
                        eventTypeCode,
                        targetUserId == null ? null : "USER",
                        targetUserId,
                        resultCode,
                        changeSummary,
                        context.requestTraceId(),
                        context.clientIpAddress()),
                "AUTH_SYSTEM_LOG_INSERT_COUNT_INVALID");
    }

    private void requireSingleInsert(int inserted, String errorCode) {
        if (inserted != 1) {
            throw new DataIntegrityViolationException(errorCode);
        }
    }
}
