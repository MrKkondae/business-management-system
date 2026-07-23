package com.bms.backend.system.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionTemplate;

class JdbcAuthenticationStateStoreTests {

    private static final LocalDateTime PASSWORD_CHANGED_AT =
            LocalDateTime.of(2026, 7, 22, 9, 0);
    private static final LocalDateTime OCCURRED_AT =
            LocalDateTime.of(2026, 7, 23, 9, 0);

    private JdbcTemplate jdbcTemplate;
    private JdbcAuthenticationStateStore store;
    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2)
                .build();
        jdbcTemplate = new JdbcTemplate(dataSource);
        store = new JdbcAuthenticationStateStore(jdbcTemplate);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        createSchema();
    }

    @Test
    void deactivatesTheAccountAndIncrementsSecurityVersionOnTheSixthFailure() {
        insertUser(0);

        for (int attempt = 1; attempt <= 6; attempt++) {
            var update = transaction.execute(status ->
                    store.recordFailedLogin(
                                    "USER-01", 1, PASSWORD_CHANGED_AT, OCCURRED_AT)
                            .orElseThrow());

            assertThat(update.loginFailureCount()).isEqualTo(attempt);
            assertThat(update.accountDeactivated()).isEqualTo(attempt == 6);
        }

        var state = jdbcTemplate.queryForMap(
                """
                SELECT login_fail_cnt, acnt_status_cd, inactive_rsn_cd,
                       inactive_dtm, sec_ver
                FROM tb_sys_user
                WHERE user_id = 'USER-01'
                """);
        assertThat(state)
                .containsEntry("LOGIN_FAIL_CNT", 6)
                .containsEntry("ACNT_STATUS_CD", "INACTIVE")
                .containsEntry("INACTIVE_RSN_CD", "LOGIN_FAILURE")
                .containsEntry("SEC_VER", 2);
        assertThat(state.get("INACTIVE_DTM")).isNotNull();
    }

    @Test
    void serializesConcurrentFailuresWithoutLosingIncrements() throws Exception {
        insertUser(0);
        int attempts = 6;
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(attempts);
        var futures = new ArrayList<java.util.concurrent.Future<?>>();
        try {
            for (int index = 0; index < attempts; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    transaction.executeWithoutResult(status -> store.recordFailedLogin(
                                    "USER-01", 1, PASSWORD_CHANGED_AT, OCCURRED_AT)
                            .orElseThrow());
                    return null;
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        var state = jdbcTemplate.queryForMap(
                """
                SELECT login_fail_cnt, acnt_status_cd, sec_ver
                FROM tb_sys_user
                WHERE user_id = 'USER-01'
                """);
        assertThat(state)
                .containsEntry("LOGIN_FAIL_CNT", 6)
                .containsEntry("ACNT_STATUS_CD", "INACTIVE")
                .containsEntry("SEC_VER", 2);
    }

    @Test
    void resetsFailuresAndUpdatesLastLoginOnSuccess() {
        insertUser(4);

        var state = transaction.execute(status ->
                store.recordSuccessfulLogin(
                                "USER-01", 1, PASSWORD_CHANGED_AT, OCCURRED_AT)
                        .orElseThrow());

        assertThat(state.securityVersion()).isEqualTo(1);
        var stored = jdbcTemplate.queryForMap(
                """
                SELECT login_fail_cnt, last_login_dtm, acnt_status_cd, sec_ver
                FROM tb_sys_user
                WHERE user_id = 'USER-01'
                """);
        assertThat(stored)
                .containsEntry("LOGIN_FAIL_CNT", 0)
                .containsEntry("ACNT_STATUS_CD", "ACTIVE")
                .containsEntry("SEC_VER", 1);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT last_login_dtm
                        FROM tb_sys_user
                        WHERE user_id = 'USER-01'
                        """,
                        LocalDateTime.class))
                .isEqualTo(OCCURRED_AT);
    }

    @Test
    void refusesToUpdateCredentialsThatChangedAfterLookup() {
        insertUser(3);

        var failure = transaction.execute(status ->
                store.recordFailedLogin(
                        "USER-01", 2, PASSWORD_CHANGED_AT, OCCURRED_AT));
        var success = transaction.execute(status ->
                store.recordSuccessfulLogin(
                        "USER-01", 1, PASSWORD_CHANGED_AT.minusSeconds(1), OCCURRED_AT));

        assertThat(failure).isEmpty();
        assertThat(success).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT login_fail_cnt FROM tb_sys_user WHERE user_id = 'USER-01'",
                        Integer.class))
                .isEqualTo(3);
    }

    private void createSchema() {
        jdbcTemplate.execute(
                """
                CREATE TABLE tb_sys_user (
                    user_id varchar(26) PRIMARY KEY,
                    acnt_status_cd varchar(20) NOT NULL,
                    del_yn char(1) NOT NULL,
                    sec_ver integer NOT NULL,
                    pwd_init_req_yn char(1) NOT NULL,
                    pwd_chg_dtm timestamp NOT NULL,
                    login_fail_cnt integer NOT NULL,
                    inactive_rsn_cd varchar(20),
                    inactive_dtm timestamp,
                    last_login_dtm timestamp,
                    mod_id varchar(26),
                    mod_dtm timestamp
                )
                """);
    }

    private void insertUser(int failureCount) {
        jdbcTemplate.update(
                """
                INSERT INTO tb_sys_user (
                    user_id, acnt_status_cd, del_yn, sec_ver,
                    pwd_init_req_yn, pwd_chg_dtm, login_fail_cnt
                ) VALUES ('USER-01', 'ACTIVE', 'N', 1, 'N', ?, ?)
                """,
                PASSWORD_CHANGED_AT,
                failureCount);
    }
}
