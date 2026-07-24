package com.bms.backend.system.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class JdbcInitialRegistrationStoreTests {

    private static final LocalDateTime ISSUED_AT = LocalDateTime.of(2026, 7, 23, 1, 0);
    private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, 7, 24, 0, 0);

    private JdbcTemplate jdbc;
    private JdbcInitialRegistrationStore store;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE tb_sys_user (
                    user_id varchar(26) PRIMARY KEY,
                    login_id varchar(100) NOT NULL,
                    user_nm varchar(100) NOT NULL,
                    email_addr varchar(100),
                    mobile_no varchar(20),
                    pwd_hash_val varchar(255) NOT NULL,
                    acnt_status_cd varchar(20) NOT NULL,
                    del_yn char(1) NOT NULL,
                    pwd_init_req_yn char(1) NOT NULL,
                    pwd_chg_dtm timestamp NOT NULL,
                    temp_pwd_expire_dtm timestamp,
                    sec_ver integer NOT NULL,
                    login_fail_cnt integer NOT NULL,
                    mod_id varchar(26),
                    mod_dtm timestamp
                )
                """);
        jdbc.update(
                """
                INSERT INTO tb_sys_user (
                    user_id, login_id, user_nm, email_addr, mobile_no,
                    pwd_hash_val, acnt_status_cd, del_yn, pwd_init_req_yn,
                    pwd_chg_dtm, temp_pwd_expire_dtm, sec_ver,
                    login_fail_cnt, mod_id, mod_dtm
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "USER-01",
                "admin",
                "관리자",
                "old@example.com",
                null,
                "TEMP-HASH",
                "ACTIVE",
                "N",
                "Y",
                ISSUED_AT,
                ISSUED_AT.plusHours(24),
                3,
                2,
                null,
                null);
        store = new JdbcInitialRegistrationStore(jdbc);
    }

    @Test
    void locksAndAtomicallyCompletesInitialRegistration() {
        var account = store.lockAccount("USER-01").orElseThrow();

        int securityVersion = store.complete(
                account, "NEW-HASH", null, "010-1234-5678", COMPLETED_AT);

        assertThat(securityVersion).isEqualTo(4);
        var row = jdbc.queryForMap("""
                SELECT pwd_hash_val, pwd_chg_dtm, pwd_init_req_yn,
                       temp_pwd_expire_dtm, sec_ver, login_fail_cnt,
                       email_addr, mobile_no, mod_id, mod_dtm
                FROM tb_sys_user
                WHERE user_id = 'USER-01'
                """);
        assertThat(row)
                .containsEntry("PWD_HASH_VAL", "NEW-HASH")
                .containsEntry("PWD_INIT_REQ_YN", "N")
                .containsEntry("SEC_VER", 4)
                .containsEntry("LOGIN_FAIL_CNT", 0)
                .containsEntry("EMAIL_ADDR", "old@example.com")
                .containsEntry("MOBILE_NO", "010-1234-5678")
                .containsEntry("MOD_ID", "USER-01");
        assertThat(row.get("TEMP_PWD_EXPIRE_DTM")).isNull();
        assertThat(((java.sql.Timestamp) row.get("PWD_CHG_DTM")).toLocalDateTime())
                .isEqualTo(COMPLETED_AT);
    }

    @Test
    void refusesAnExpiredOrAlreadyChangedCredentialBaseline() {
        var account = store.lockAccount("USER-01").orElseThrow();
        jdbc.update(
                "UPDATE tb_sys_user SET temp_pwd_expire_dtm = ? WHERE user_id = ?",
                COMPLETED_AT,
                "USER-01");

        assertThatThrownBy(() ->
                        store.complete(account, "NEW-HASH", null, null, COMPLETED_AT))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject(
                        "SELECT pwd_hash_val FROM tb_sys_user WHERE user_id = 'USER-01'",
                        String.class))
                .isEqualTo("TEMP-HASH");
    }
}
