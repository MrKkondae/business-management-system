package com.bms.backend.system.infrastructure.persistence.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bms.backend.common.application.authentication.LoginAttemptContext;
import com.bms.backend.common.application.authentication.LoginFailureReason;
import com.bms.backend.common.domain.authentication.NormalizedLoginId;
import com.bms.backend.system.application.authentication.AccountStatus;
import com.bms.backend.system.application.authentication.AuthenticationAuditStore;
import com.bms.backend.system.application.authentication.AuthenticationStateStore;
import com.bms.backend.system.application.authentication.AuthenticationUserQuery;
import com.bms.backend.system.application.authentication.InitialRegistrationStore;
import com.bms.backend.system.application.authentication.ReauthenticationAuditOutcome;
import com.bms.backend.system.infrastructure.persistence.mybatis.mapper.AuthenticationStateMapper;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        properties = "spring.main.web-application-type=none",
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AuthenticationMapperPostgresIntegrationTests {

    private static final String ADMIN_ROLE_ID = "01KY3HYG000000000000000001";
    private static final LocalDateTime PASSWORD_CHANGED_AT =
            LocalDateTime.of(2026, 7, 24, 1, 0);
    private static final LoginAttemptContext LOGIN_CONTEXT =
            new LoginAttemptContext("AUTH-MAPPER-PG", "203.0.113.10", "JUnit/1.0");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private AuthenticationUserQuery userQuery;

    @Autowired
    private AuthenticationStateStore stateStore;

    @Autowired
    private InitialRegistrationStore initialRegistrationStore;

    @Autowired
    private AuthenticationAuditStore auditStore;

    @Autowired
    private AuthenticationStateMapper stateMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @Transactional
    @Rollback
    void mapsLoginSessionReauthenticationAndAuthorizationQueries() {
        String userId = "AUTH-PG-QUERY-USER-000001";
        insertUser(
                userId,
                "auth.pg.query",
                "Y",
                2,
                3,
                PASSWORD_CHANGED_AT.plusHours(24));
        jdbc.update(
                """
                INSERT INTO tb_sys_user_role_rel (user_id, role_id, reg_id, reg_dtm)
                VALUES (?, ?, 'SYSTEM', ?)
                """,
                userId,
                ADMIN_ROLE_ID,
                PASSWORD_CHANGED_AT);

        var candidate =
                userQuery.findLoginCandidate(NormalizedLoginId.from(" AUTH.PG.QUERY "))
                        .orElseThrow();
        var snapshot = userQuery.findAuthorizationSnapshot(userId);
        var sessionState = userQuery.findSessionState(userId).orElseThrow();
        var credential = userQuery.findReauthenticationCredential(userId).orElseThrow();

        assertThat(candidate.userId()).isEqualTo(userId);
        assertThat(candidate.loginFailureCount()).isEqualTo(2);
        assertThat(candidate.passwordChangeRequired()).isTrue();
        assertThat(candidate.securityVersion()).isEqualTo(3);
        assertThat(snapshot.roles()).extracting("roleId").containsExactly(ADMIN_ROLE_ID);
        assertThat(snapshot.menus())
                .extracting("menuId")
                .containsExactly(
                        "01KY3HYG100000000000000001",
                        "01KY3HYG100000000000000002",
                        "01KY3HYG100000000000000003",
                        "01KY3HYG100000000000000004",
                        "01KY3HYG100000000000000005",
                        "01KY3HYG100000000000000006");
        assertThat(sessionState.accountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(sessionState.passwordChangedAt()).isEqualTo(PASSWORD_CHANGED_AT);
        assertThat(credential.passwordHash()).isEqualTo("PASSWORD-HASH");
        assertThat(credential.securityVersion()).isEqualTo(3);
    }

    @Test
    @Transactional
    @Rollback
    void appliesFailedAndSuccessfulLoginStateTransitions() {
        String failingUserId = "AUTH-PG-FAIL-USER-0000001";
        String successfulUserId = "AUTH-PG-SUCC-USER-0000001";
        insertUser(failingUserId, "auth.pg.fail", "N", 5, 1, null);
        insertUser(successfulUserId, "auth.pg.success", "N", 4, 2, null);

        var failure = stateStore
                .recordFailedLogin(
                        failingUserId,
                        1,
                        PASSWORD_CHANGED_AT,
                        PASSWORD_CHANGED_AT.plusMinutes(1))
                .orElseThrow();
        var success = stateStore
                .recordSuccessfulLogin(
                        successfulUserId,
                        2,
                        PASSWORD_CHANGED_AT,
                        PASSWORD_CHANGED_AT.plusMinutes(2))
                .orElseThrow();

        assertThat(failure.loginFailureCount()).isEqualTo(6);
        assertThat(failure.accountDeactivated()).isTrue();
        assertThat(failure.securityVersion()).isEqualTo(2);
        assertThat(success.securityVersion()).isEqualTo(2);
        assertThat(jdbc.queryForMap(
                        """
                        SELECT login_fail_cnt, acnt_status_cd, inactive_rsn_cd, sec_ver
                        FROM tb_sys_user
                        WHERE user_id = ?
                        """,
                        failingUserId))
                .containsEntry("login_fail_cnt", 6)
                .containsEntry("acnt_status_cd", "INACTIVE")
                .containsEntry("inactive_rsn_cd", "LOGIN_FAILURE")
                .containsEntry("sec_ver", 2);
        assertThat(jdbc.queryForMap(
                        """
                        SELECT login_fail_cnt, acnt_status_cd, sec_ver
                        FROM tb_sys_user
                        WHERE user_id = ?
                        """,
                        successfulUserId))
                .containsEntry("login_fail_cnt", 0)
                .containsEntry("acnt_status_cd", "ACTIVE")
                .containsEntry("sec_ver", 2);
    }

    @Test
    @Transactional
    @Rollback
    void completesInitialRegistrationOnlyForTheLockedCredentialBaseline() {
        String userId = "AUTH-PG-INIT-USER-0000001";
        insertUser(
                userId,
                "auth.pg.initial",
                "Y",
                3,
                4,
                PASSWORD_CHANGED_AT.plusHours(24));

        var account = initialRegistrationStore.lockAccount(userId).orElseThrow();
        int securityVersion = initialRegistrationStore.complete(
                account,
                "NEW-PASSWORD-HASH",
                "mapper@example.com",
                "010-1234-5678",
                PASSWORD_CHANGED_AT.plusMinutes(1));

        assertThat(securityVersion).isEqualTo(5);
        assertThat(jdbc.queryForMap(
                        """
                        SELECT pwd_hash_val, pwd_init_req_yn, temp_pwd_expire_dtm,
                               sec_ver, login_fail_cnt, email_addr, mobile_no
                        FROM tb_sys_user
                        WHERE user_id = ?
                        """,
                        userId))
                .containsEntry("pwd_hash_val", "NEW-PASSWORD-HASH")
                .containsEntry("pwd_init_req_yn", "N")
                .containsEntry("sec_ver", 5)
                .containsEntry("login_fail_cnt", 0)
                .containsEntry("email_addr", "mapper@example.com")
                .containsEntry("mobile_no", "010-1234-5678");
        assertThat(jdbc.queryForObject(
                        "SELECT temp_pwd_expire_dtm FROM tb_sys_user WHERE user_id = ?",
                        LocalDateTime.class,
                        userId))
                .isNull();
        assertThatThrownBy(() -> initialRegistrationStore.complete(
                        account,
                        "STALE-PASSWORD-HASH",
                        null,
                        null,
                        PASSWORD_CHANGED_AT.plusMinutes(2)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessage("AUTH_INITIAL_REGISTRATION_UPDATE_INVALID");
    }

    @Test
    @Transactional
    @Rollback
    void writesAndClosesAccessLogsAndWritesSecurityAuditLogs() {
        String userId = "AUTH-PG-AUDIT-USER-000001";
        insertUser(userId, "auth.pg.audit", "N", 0, 1, null);

        String accessLogId =
                auditStore.recordSuccessfulLogin(userId, LOGIN_CONTEXT, PASSWORD_CHANGED_AT);
        auditStore.recordLogout(
                accessLogId, userId, "MANUAL", PASSWORD_CHANGED_AT.plusMinutes(1));
        auditStore.recordFailedLogin(
                null,
                "hmac-sha256:protected",
                LoginFailureReason.USER_NOT_FOUND,
                LOGIN_CONTEXT,
                PASSWORD_CHANGED_AT);
        auditStore.recordRateLimited(LOGIN_CONTEXT, PASSWORD_CHANGED_AT);
        auditStore.recordReauthentication(
                userId,
                ReauthenticationAuditOutcome.SUCCEEDED,
                LOGIN_CONTEXT,
                PASSWORD_CHANGED_AT);
        auditStore.recordInitialRegistrationCompleted(
                userId, LOGIN_CONTEXT, PASSWORD_CHANGED_AT);

        assertThat(jdbc.queryForMap(
                        """
                        SELECT user_id, succ_yn, logout_type_cd, req_trace_id
                        FROM tb_sys_access_log
                        WHERE access_log_id = ?
                        """,
                        accessLogId))
                .containsEntry("user_id", userId)
                .containsEntry("succ_yn", "Y")
                .containsEntry("logout_type_cd", "MANUAL")
                .containsEntry("req_trace_id", "AUTH-MAPPER-PG");
        assertThat(jdbc.queryForMap(
                        """
                        SELECT user_id, succ_yn, fail_rsn_cd, login_id_hash_val
                        FROM tb_sys_access_log
                        WHERE succ_yn = 'N'
                          AND req_trace_id = ?
                        """,
                        LOGIN_CONTEXT.requestTraceId()))
                .containsEntry("succ_yn", "N")
                .containsEntry("fail_rsn_cd", "USER_NOT_FOUND")
                .containsEntry("login_id_hash_val", "hmac-sha256:protected");
        assertThat(jdbc.queryForList(
                        """
                        SELECT event_type_cd
                        FROM tb_sys_log
                        WHERE req_trace_id = ?
                        ORDER BY event_type_cd
                        """,
                        String.class,
                        LOGIN_CONTEXT.requestTraceId()))
                .containsExactly(
                        "INITIAL_REGISTRATION_COMPLETED",
                        "LOGIN_RATE_LIMITED",
                        "REAUTHENTICATION_SUCCEEDED");
    }

    @Test
    void blocksACompetingTransactionWhileThePostgresRowLockIsHeld() throws Exception {
        String userId = "AUTH-PG-LOCK-USER-0000001";
        insertUser(userId, "auth.pg.lock", "N", 0, 1, null);
        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirstLock = new CountDownLatch(1);
        CountDownLatch secondTransactionStarted = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        stateMapper.findUsersForUpdate(userId);
                        firstLockAcquired.countDown();
                        await(releaseFirstLock);
                    }));
            assertThat(firstLockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

            var second = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        secondTransactionStarted.countDown();
                        stateMapper.findUsersForUpdate(userId);
                    }));
            assertThat(secondTransactionStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseFirstLock.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            releaseFirstLock.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            jdbc.update("DELETE FROM tb_sys_user WHERE user_id = ?", userId);
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("PostgreSQL row lock test timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("PostgreSQL row lock test interrupted", exception);
        }
    }

    private void insertUser(
            String userId,
            String loginId,
            String passwordChangeRequiredFlag,
            int loginFailureCount,
            int securityVersion,
            LocalDateTime temporaryPasswordExpiresAt) {
        jdbc.update(
                """
                INSERT INTO tb_sys_user (
                    user_id, emp_id, login_id, user_nm, email_addr, mobile_no,
                    acnt_status_cd, pwd_hash_val, pwd_chg_dtm, login_fail_cnt,
                    inactive_dtm, last_login_dtm, pwd_init_req_yn,
                    reg_id, reg_dtm, mod_id, mod_dtm, del_yn, inactive_rsn_cd,
                    temp_pwd_expire_dtm, sec_ver
                ) VALUES (
                    ?, NULL, ?, 'PostgreSQL Mapper', NULL, NULL,
                    'ACTIVE', 'PASSWORD-HASH', ?, ?,
                    NULL, NULL, ?,
                    'SYSTEM', ?, NULL, NULL, 'N', NULL,
                    ?, ?
                )
                """,
                userId,
                loginId,
                PASSWORD_CHANGED_AT,
                loginFailureCount,
                passwordChangeRequiredFlag,
                PASSWORD_CHANGED_AT,
                temporaryPasswordExpiresAt,
                securityVersion);
    }
}
