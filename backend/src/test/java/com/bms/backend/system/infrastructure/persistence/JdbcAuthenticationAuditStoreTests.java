package com.bms.backend.system.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.bms.backend.common.application.authentication.LoginAttemptContext;
import com.bms.backend.common.application.authentication.LoginFailureReason;
import com.bms.backend.global.persistence.MonotonicUlidGenerator;
import com.bms.backend.system.application.authentication.ReauthenticationAuditOutcome;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class JdbcAuthenticationAuditStoreTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 23, 9, 0);
    private static final LoginAttemptContext CONTEXT =
            new LoginAttemptContext("TRACE-01", "203.0.113.10", "JUnit/1.0");

    private JdbcTemplate jdbc;
    private JdbcAuthenticationAuditStore store;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE tb_sys_access_log (
                    access_log_id varchar(26) PRIMARY KEY,
                    user_id varchar(26),
                    access_dtm timestamp NOT NULL,
                    logout_dtm timestamp,
                    access_ip_addr varchar(45),
                    succ_yn char(1) NOT NULL,
                    reg_id varchar(26) NOT NULL,
                    reg_dtm timestamp NOT NULL,
                    fail_rsn_cd varchar(20),
                    logout_type_cd varchar(20),
                    req_trace_id varchar(64) NOT NULL,
                    login_id_hash_val varchar(255),
                    user_agent_cont text,
                    CHECK ((succ_yn = 'Y' AND fail_rsn_cd IS NULL)
                        OR (succ_yn = 'N' AND fail_rsn_cd IS NOT NULL))
                )
                """);
        jdbc.execute("""
                CREATE TABLE tb_sys_log (
                    log_id varchar(26) PRIMARY KEY,
                    log_type_cd varchar(20) NOT NULL,
                    proc_user_id varchar(26),
                    occur_dtm timestamp NOT NULL,
                    log_cont text,
                    reg_id varchar(26) NOT NULL,
                    reg_dtm timestamp NOT NULL,
                    event_type_cd varchar(50) NOT NULL,
                    tgt_type_cd varchar(20),
                    tgt_id varchar(26),
                    proc_result_cd varchar(20) NOT NULL,
                    chg_smry_cont text,
                    req_trace_id varchar(64),
                    access_ip_addr varchar(45),
                    CHECK ((tgt_type_cd IS NULL AND tgt_id IS NULL)
                        OR (tgt_type_cd IS NOT NULL AND tgt_id IS NOT NULL))
                )
                """);
        store = new JdbcAuthenticationAuditStore(jdbc, new MonotonicUlidGenerator());
    }

    @Test
    void recordsSuccessfulAndKnownUserFailedAccessWithoutLoginIdentifierHash() {
        String successId = store.recordSuccessfulLogin("USER-01", CONTEXT, NOW);
        store.recordFailedLogin(
                "USER-01", null, LoginFailureReason.BAD_CREDENTIALS, CONTEXT, NOW);

        var rows = jdbc.queryForList("""
                SELECT access_log_id, user_id, succ_yn, fail_rsn_cd,
                       req_trace_id, login_id_hash_val, access_ip_addr, user_agent_cont
                FROM tb_sys_access_log
                ORDER BY succ_yn DESC
                """);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0))
                .containsEntry("ACCESS_LOG_ID", successId)
                .containsEntry("USER_ID", "USER-01")
                .containsEntry("SUCC_YN", "Y")
                .containsEntry("REQ_TRACE_ID", "TRACE-01")
                .containsEntry("ACCESS_IP_ADDR", "203.0.113.10")
                .containsEntry("USER_AGENT_CONT", "JUnit/1.0");
        assertThat(rows.get(0).get("FAIL_RSN_CD")).isNull();
        assertThat(rows.get(0).get("LOGIN_ID_HASH_VAL")).isNull();
        assertThat(rows.get(1))
                .containsEntry("USER_ID", "USER-01")
                .containsEntry("SUCC_YN", "N")
                .containsEntry("FAIL_RSN_CD", "BAD_CREDENTIALS");
        assertThat(rows.get(1).get("LOGIN_ID_HASH_VAL")).isNull();
    }

    @Test
    void recordsUnknownUserFailureUsingOnlyTheProtectedIdentifier() {
        store.recordFailedLogin(
                null,
                "hmac-sha256:protected-value",
                LoginFailureReason.USER_NOT_FOUND,
                CONTEXT,
                NOW);

        var row = jdbc.queryForMap("""
                SELECT user_id, succ_yn, fail_rsn_cd, login_id_hash_val
                FROM tb_sys_access_log
                """);

        assertThat(row.get("USER_ID")).isNull();
        assertThat(row)
                .containsEntry("SUCC_YN", "N")
                .containsEntry("FAIL_RSN_CD", "USER_NOT_FOUND")
                .containsEntry("LOGIN_ID_HASH_VAL", "hmac-sha256:protected-value");
    }

    @Test
    void recordsAccountDeactivationAndRateLimitAsSecurityEvents() {
        store.recordAccountDeactivated("USER-01", CONTEXT, NOW);
        store.recordRateLimited(CONTEXT, NOW);

        var rows = jdbc.queryForList("""
                SELECT log_type_cd, event_type_cd, tgt_type_cd, tgt_id,
                       proc_result_cd, req_trace_id, access_ip_addr, chg_smry_cont
                FROM tb_sys_log
                ORDER BY event_type_cd
                """);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0))
                .containsEntry("LOG_TYPE_CD", "SECURITY")
                .containsEntry("EVENT_TYPE_CD", "LOGIN_RATE_LIMITED")
                .containsEntry("PROC_RESULT_CD", "FAILURE")
                .containsEntry("REQ_TRACE_ID", "TRACE-01")
                .containsEntry("ACCESS_IP_ADDR", "203.0.113.10");
        assertThat(rows.get(0).get("TGT_ID")).isNull();
        assertThat(rows.get(1))
                .containsEntry("EVENT_TYPE_CD", "USER_DEACTIVATED_LOGIN_FAILURE")
                .containsEntry("TGT_TYPE_CD", "USER")
                .containsEntry("TGT_ID", "USER-01")
                .containsEntry("PROC_RESULT_CD", "SUCCESS");
        assertThat(rows.get(1).get("CHG_SMRY_CONT").toString())
                .doesNotContain("password", "hash", "cookie", "session");
    }

    @Test
    void closesASuccessfulAccessLogOnlyOnce() {
        String accessLogId = store.recordSuccessfulLogin("USER-01", CONTEXT, NOW);

        store.recordLogout(accessLogId, "USER-01", "MANUAL", NOW.plusMinutes(5));
        store.recordLogout(accessLogId, "USER-01", "TIMEOUT", NOW.plusMinutes(10));

        var row = jdbc.queryForMap(
                """
                SELECT logout_dtm, logout_type_cd
                FROM tb_sys_access_log
                WHERE access_log_id = ?
                """,
                accessLogId);
        assertThat(((java.sql.Timestamp) row.get("LOGOUT_DTM")).toLocalDateTime())
                .isEqualTo(NOW.plusMinutes(5));
        assertThat(row).containsEntry("LOGOUT_TYPE_CD", "MANUAL");
    }

    @Test
    void recordsReauthenticationOutcomesWithoutCredentialData() {
        store.recordReauthentication(
                "USER-01", ReauthenticationAuditOutcome.SUCCEEDED, CONTEXT, NOW);
        store.recordReauthentication(
                "USER-01", ReauthenticationAuditOutcome.FAILED, CONTEXT, NOW);
        store.recordReauthentication(
                "USER-01", ReauthenticationAuditOutcome.RATE_LIMITED, CONTEXT, NOW);

        var rows = jdbc.queryForList("""
                SELECT event_type_cd, proc_result_cd, tgt_id, chg_smry_cont
                FROM tb_sys_log
                WHERE event_type_cd LIKE 'REAUTHENTICATION_%'
                ORDER BY event_type_cd
                """);
        assertThat(rows).hasSize(3);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get("TGT_ID")).isEqualTo("USER-01");
            assertThat(row.get("CHG_SMRY_CONT").toString())
                    .doesNotContain("secret", "hash", "passwordHash");
        });
    }

    @Test
    void recordsInitialRegistrationWithoutCredentialOrPersonalData() {
        store.recordInitialRegistrationCompleted("USER-01", CONTEXT, NOW);

        var row = jdbc.queryForMap("""
                SELECT event_type_cd, proc_result_cd, tgt_type_cd, tgt_id,
                       chg_smry_cont, req_trace_id
                FROM tb_sys_log
                """);
        assertThat(row)
                .containsEntry("EVENT_TYPE_CD", "INITIAL_REGISTRATION_COMPLETED")
                .containsEntry("PROC_RESULT_CD", "SUCCESS")
                .containsEntry("TGT_TYPE_CD", "USER")
                .containsEntry("TGT_ID", "USER-01")
                .containsEntry("REQ_TRACE_ID", "TRACE-01");
        assertThat(row.get("CHG_SMRY_CONT").toString())
                .doesNotContain("secret", "hash", "email", "mobile");
    }
}
