package com.bms.backend.system.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.bms.backend.common.application.authentication.LoginAttemptContext;
import com.bms.backend.common.application.authentication.LoginFailureReason;
import com.bms.backend.global.security.LoginSessionValidationFilter;
import com.bms.backend.system.application.authentication.ReauthenticationAuditOutcome;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        properties = "spring.main.web-application-type=none",
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(
        named = "BMS_VERIFY_DEVELOPMENT_DB",
        matches = "true")
class DevelopmentPostgresAuthenticationAuditVerificationTests {

    @Autowired
    private JdbcAuthenticationAuditStore auditStore;

    @Autowired
    private JdbcInitialRegistrationStore initialRegistrationStore;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LoginSessionValidationFilter loginSessionValidationFilter;

    @Test
    @Transactional
    @Rollback
    void writesAuthenticationSecurityLogsAgainstPostgresAndRollsThemBack() {
        String traceId = "DEV-PG-AUTH-AUDIT-VERIFY";
        LoginAttemptContext context =
                new LoginAttemptContext(traceId, "127.0.0.1", "BMS verification");
        LocalDateTime occurredAt =
                LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        String initialRegistrationUserId = "DEV-VERIFY-INITIAL";

        jdbc.update(
                """
                INSERT INTO tb_sys_user (
                    user_id, emp_id, login_id, user_nm, email_addr, mobile_no,
                    acnt_status_cd, pwd_hash_val, pwd_chg_dtm, login_fail_cnt,
                    inactive_dtm, last_login_dtm, pwd_init_req_yn,
                    reg_id, reg_dtm, mod_id, mod_dtm, del_yn, inactive_rsn_cd,
                    temp_pwd_expire_dtm, sec_ver
                ) VALUES (
                    ?, NULL, ?, ?, NULL, NULL,
                    'ACTIVE', ?, ?, 0,
                    NULL, NULL, 'Y',
                    'SYSTEM', ?, NULL, NULL, 'N', NULL,
                    ?, 1
                )
                """,
                initialRegistrationUserId,
                "dev.verify.initial",
                "Development verification",
                "TEMP-HASH",
                occurredAt,
                occurredAt,
                occurredAt.plusHours(24));
        var initialAccount =
                initialRegistrationStore.lockAccount(initialRegistrationUserId).orElseThrow();
        int newSecurityVersion = initialRegistrationStore.complete(
                initialAccount,
                "NEW-HASH",
                "verify@example.com",
                "010-0000-0000",
                occurredAt.plusSeconds(1));
        auditStore.recordInitialRegistrationCompleted(
                initialRegistrationUserId, context, occurredAt.plusSeconds(1));

        auditStore.recordFailedLogin(
                null,
                "hmac-sha256:development-verification",
                LoginFailureReason.USER_NOT_FOUND,
                context,
                occurredAt);
        auditStore.recordRateLimited(context, occurredAt);
        auditStore.recordReauthentication(
                "DEV-VERIFY-USER",
                ReauthenticationAuditOutcome.SUCCEEDED,
                context,
                occurredAt);
        String successfulAccessLogId =
                auditStore.recordSuccessfulLogin("DEV-VERIFY-USER", context, occurredAt);
        auditStore.recordLogout(
                successfulAccessLogId,
                "DEV-VERIFY-USER",
                "MANUAL",
                occurredAt.plusMinutes(1));

        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*)
                        FROM tb_sys_access_log
                        WHERE req_trace_id = ?
                          AND succ_yn = 'N'
                          AND fail_rsn_cd = 'USER_NOT_FOUND'
                          AND user_id IS NULL
                          AND login_id_hash_val = ?
                        """,
                        Integer.class,
                        traceId,
                        "hmac-sha256:development-verification"))
                .isEqualTo(1);
        assertThat(loginSessionValidationFilter).isNotNull();
        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*)
                        FROM tb_sys_access_log
                        WHERE access_log_id = ?
                          AND user_id = 'DEV-VERIFY-USER'
                          AND succ_yn = 'Y'
                          AND logout_type_cd = 'MANUAL'
                          AND logout_dtm IS NOT NULL
                        """,
                        Integer.class,
                        successfulAccessLogId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*)
                        FROM tb_sys_log
                        WHERE req_trace_id = ?
                          AND log_type_cd = 'SECURITY'
                          AND event_type_cd = 'LOGIN_RATE_LIMITED'
                          AND proc_result_cd = 'FAILURE'
                        """,
                        Integer.class,
                        traceId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*)
                        FROM tb_sys_log
                        WHERE req_trace_id = ?
                          AND log_type_cd = 'SECURITY'
                          AND event_type_cd = 'REAUTHENTICATION_SUCCEEDED'
                          AND proc_result_cd = 'SUCCESS'
                          AND tgt_type_cd = 'USER'
                          AND tgt_id = 'DEV-VERIFY-USER'
                        """,
                        Integer.class,
                        traceId))
                .isEqualTo(1);
        assertThat(newSecurityVersion).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*)
                        FROM tb_sys_user
                        WHERE user_id = ?
                          AND pwd_hash_val = 'NEW-HASH'
                          AND pwd_init_req_yn = 'N'
                          AND temp_pwd_expire_dtm IS NULL
                          AND sec_ver = 2
                          AND email_addr = 'verify@example.com'
                          AND mobile_no = '010-0000-0000'
                        """,
                        Integer.class,
                        initialRegistrationUserId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*)
                        FROM tb_sys_log
                        WHERE req_trace_id = ?
                          AND event_type_cd = 'INITIAL_REGISTRATION_COMPLETED'
                          AND proc_result_cd = 'SUCCESS'
                          AND tgt_type_cd = 'USER'
                          AND tgt_id = ?
                        """,
                        Integer.class,
                        traceId,
                        initialRegistrationUserId))
                .isEqualTo(1);
    }
}
