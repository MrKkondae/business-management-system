package com.bms.backend.system.infrastructure.persistence;

import com.bms.backend.common.application.authentication.LoginAttemptContext;
import com.bms.backend.common.application.authentication.LoginFailureReason;
import com.bms.backend.global.persistence.MonotonicUlidGenerator;
import com.bms.backend.system.application.authentication.AuthenticationAuditStore;
import com.bms.backend.system.application.authentication.ReauthenticationAuditOutcome;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcAuthenticationAuditStore implements AuthenticationAuditStore {

    private static final String SYSTEM_ACTOR = "SYSTEM";

    private final JdbcTemplate jdbcTemplate;
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
        jdbcTemplate.update(
                """
                UPDATE tb_sys_access_log
                SET logout_dtm = ?,
                    logout_type_cd = ?
                WHERE access_log_id = ?
                  AND user_id = ?
                  AND succ_yn = 'Y'
                  AND logout_dtm IS NULL
                """,
                occurredAt,
                logoutTypeCode,
                accessLogId,
                userId);
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
        jdbcTemplate.update(
                """
                INSERT INTO tb_sys_access_log (
                    access_log_id, user_id, access_dtm, logout_dtm,
                    access_ip_addr, succ_yn, reg_id, reg_dtm,
                    fail_rsn_cd, logout_type_cd, req_trace_id,
                    login_id_hash_val, user_agent_cont
                ) VALUES (
                    ?, ?, ?, NULL,
                    ?, ?, ?, ?,
                    ?, NULL, ?,
                    ?, ?
                )
                """,
                accessLogId,
                userId,
                occurredAt,
                context.clientIpAddress(),
                successful ? "Y" : "N",
                SYSTEM_ACTOR,
                occurredAt,
                failureReasonCode,
                context.requestTraceId(),
                protectedLoginId,
                context.userAgent());
    }

    private void insertSystemLog(
            String eventTypeCode,
            String resultCode,
            String targetUserId,
            String changeSummary,
            LoginAttemptContext context,
            LocalDateTime occurredAt) {
        jdbcTemplate.update(
                """
                INSERT INTO tb_sys_log (
                    log_id, log_type_cd, proc_user_id, occur_dtm,
                    log_cont, reg_id, reg_dtm, event_type_cd,
                    tgt_type_cd, tgt_id, proc_result_cd,
                    chg_smry_cont, req_trace_id, access_ip_addr
                ) VALUES (
                    ?, 'SECURITY', NULL, ?,
                    NULL, ?, ?, ?,
                    ?, ?, ?,
                    ?, ?, ?
                )
                """,
                ulidGenerator.next(),
                occurredAt,
                SYSTEM_ACTOR,
                occurredAt,
                eventTypeCode,
                targetUserId == null ? null : "USER",
                targetUserId,
                resultCode,
                changeSummary,
                context.requestTraceId(),
                context.clientIpAddress());
    }
}
