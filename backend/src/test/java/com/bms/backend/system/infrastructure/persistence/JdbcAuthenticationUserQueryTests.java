package com.bms.backend.system.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bms.backend.common.domain.authentication.NormalizedLoginId;
import com.bms.backend.system.application.authentication.AccountStatus;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class JdbcAuthenticationUserQueryTests {

    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private JdbcAuthenticationUserQuery query;

    @BeforeEach
    void setUp() {
        dataSource = new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2)
                .build();
        jdbcTemplate = new JdbcTemplate(dataSource);
        query = new JdbcAuthenticationUserQuery(jdbcTemplate);
        createSchema();
        insertFixtures();
    }

    @Test
    void findsAStoredLoginCandidateByTheNormalizedLoginId() {
        var candidate =
                query.findLoginCandidate(NormalizedLoginId.from("  ADMIN.USER  "))
                        .orElseThrow();

        assertThat(candidate.userId()).isEqualTo("USER-01");
        assertThat(candidate.employeeId()).isEqualTo("EMP-01");
        assertThat(candidate.loginId().value()).isEqualTo("admin.user");
        assertThat(candidate.displayName()).isEqualTo("관리자");
        assertThat(candidate.passwordHash()).isEqualTo("$argon2id$secret-hash");
        assertThat(candidate.accountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(candidate.loginFailureCount()).isEqualTo(2);
        assertThat(candidate.passwordChangeRequired()).isTrue();
        assertThat(candidate.temporaryPasswordExpiresAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 24, 9, 0));
        assertThat(candidate.securityVersion()).isEqualTo(3);
        assertThat(candidate.toString()).doesNotContain("$argon2id$secret-hash");
    }

    @Test
    void hidesDeletedUsersFromLoginLookupButKeepsTheirSessionStateVisible() {
        assertThat(query.findLoginCandidate(NormalizedLoginId.from("deleted.user")))
                .isEmpty();

        var state = query.findSessionState("USER-02").orElseThrow();

        assertThat(state.deleted()).isTrue();
        assertThat(state.isUsableWith(4)).isFalse();
    }

    @Test
    void returnsOnlyActiveRolesAndMenusWithoutPermissionDuplicates() {
        var snapshot = query.findAuthorizationSnapshot("USER-01");

        assertThat(snapshot.roles())
                .extracting("roleId", "roleName")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("ROLE-01", "시스템관리자"),
                        org.assertj.core.groups.Tuple.tuple("ROLE-03", "일반사용자"));
        assertThat(snapshot.menus())
                .extracting("menuId", "parentMenuId", "menuName", "menuUrl", "sortOrder")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "MENU-01", null, "시스템 관리", null, 20),
                        org.assertj.core.groups.Tuple.tuple(
                                "MENU-02", "MENU-01", "사용자 관리", "/system/users", 10));
        assertThatThrownBy(() -> snapshot.roles().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void returnsSessionSecurityStateForProtectedRequestValidation() {
        var state = query.findSessionState("USER-01").orElseThrow();

        assertThat(state.accountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(state.securityVersion()).isEqualTo(3);
        assertThat(state.passwordChangeRequired()).isTrue();
        assertThat(state.passwordChangedAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 23, 9, 0));
        assertThat(state.isUsableWith(3)).isTrue();
        assertThat(state.isUsableWith(2)).isFalse();
        assertThat(query.findSessionState("UNKNOWN")).isEmpty();
    }

    @Test
    void returnsASecretProtectedCredentialOnlyForReauthentication() {
        var credential = query.findReauthenticationCredential("USER-01").orElseThrow();

        assertThat(credential.passwordHash()).isEqualTo("$argon2id$secret-hash");
        assertThat(credential.securityVersion()).isEqualTo(3);
        assertThat(credential.passwordChangeRequired()).isTrue();
        assertThat(credential.toString()).doesNotContain("$argon2id$secret-hash");
        assertThat(query.findReauthenticationCredential("UNKNOWN")).isEmpty();
    }

    private void createSchema() {
        jdbcTemplate.execute(
                """
                CREATE TABLE tb_sys_user (
                    user_id varchar(26) PRIMARY KEY,
                    emp_id varchar(26),
                    login_id varchar(26) NOT NULL UNIQUE,
                    user_nm varchar(100) NOT NULL,
                    pwd_hash_val varchar(255) NOT NULL,
                    acnt_status_cd varchar(20) NOT NULL,
                    login_fail_cnt integer NOT NULL,
                    pwd_chg_dtm timestamp NOT NULL,
                    pwd_init_req_yn char(1) NOT NULL,
                    temp_pwd_expire_dtm timestamp,
                    sec_ver integer NOT NULL,
                    del_yn char(1) NOT NULL
                )
                """);
        jdbcTemplate.execute(
                """
                CREATE TABLE tb_sys_role (
                    role_id varchar(26) PRIMARY KEY,
                    role_nm varchar(100) NOT NULL,
                    del_yn char(1) NOT NULL
                )
                """);
        jdbcTemplate.execute(
                """
                CREATE TABLE tb_sys_user_role_rel (
                    user_id varchar(26) NOT NULL,
                    role_id varchar(26) NOT NULL,
                    PRIMARY KEY (user_id, role_id)
                )
                """);
        jdbcTemplate.execute(
                """
                CREATE TABLE tb_sys_menu (
                    menu_id varchar(26) PRIMARY KEY,
                    up_menu_id varchar(26),
                    menu_nm varchar(100) NOT NULL,
                    menu_url varchar(300),
                    sort_seq integer NOT NULL,
                    del_yn char(1) NOT NULL
                )
                """);
        jdbcTemplate.execute(
                """
                CREATE TABLE tb_sys_role_menu_perm_rel (
                    role_id varchar(26) NOT NULL,
                    menu_id varchar(26) NOT NULL,
                    PRIMARY KEY (role_id, menu_id)
                )
                """);
    }

    private void insertFixtures() {
        jdbcTemplate.update(
                """
                INSERT INTO tb_sys_user (
                    user_id, emp_id, login_id, user_nm, pwd_hash_val,
                    acnt_status_cd, login_fail_cnt, pwd_chg_dtm,
                    pwd_init_req_yn, temp_pwd_expire_dtm, sec_ver, del_yn
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "USER-01",
                "EMP-01",
                "admin.user",
                "관리자",
                "$argon2id$secret-hash",
                "ACTIVE",
                2,
                LocalDateTime.of(2026, 7, 23, 9, 0),
                "Y",
                LocalDateTime.of(2026, 7, 24, 9, 0),
                3,
                "N");
        jdbcTemplate.update(
                """
                INSERT INTO tb_sys_user (
                    user_id, emp_id, login_id, user_nm, pwd_hash_val,
                    acnt_status_cd, login_fail_cnt, pwd_chg_dtm,
                    pwd_init_req_yn, temp_pwd_expire_dtm, sec_ver, del_yn
                ) VALUES (?, NULL, ?, ?, ?, ?, 0, ?, 'N', NULL, 4, 'Y')
                """,
                "USER-02",
                "deleted.user",
                "삭제 사용자",
                "$argon2id$deleted-hash",
                "INACTIVE",
                LocalDateTime.of(2026, 7, 20, 9, 0));

        jdbcTemplate.update(
                "INSERT INTO tb_sys_role VALUES ('ROLE-01', '시스템관리자', 'N')");
        jdbcTemplate.update(
                "INSERT INTO tb_sys_role VALUES ('ROLE-02', '삭제역할', 'Y')");
        jdbcTemplate.update(
                "INSERT INTO tb_sys_role VALUES ('ROLE-03', '일반사용자', 'N')");
        jdbcTemplate.update(
                "INSERT INTO tb_sys_user_role_rel VALUES ('USER-01', 'ROLE-01')");
        jdbcTemplate.update(
                "INSERT INTO tb_sys_user_role_rel VALUES ('USER-01', 'ROLE-02')");
        jdbcTemplate.update(
                "INSERT INTO tb_sys_user_role_rel VALUES ('USER-01', 'ROLE-03')");

        jdbcTemplate.update(
                "INSERT INTO tb_sys_menu VALUES ('MENU-01', NULL, '시스템 관리', NULL, 20, 'N')");
        jdbcTemplate.update(
                "INSERT INTO tb_sys_menu VALUES ('MENU-02', 'MENU-01', '사용자 관리', '/system/users', 10, 'N')");
        jdbcTemplate.update(
                "INSERT INTO tb_sys_menu VALUES ('MENU-03', NULL, '삭제 메뉴', '/deleted', 30, 'Y')");
        jdbcTemplate.update(
                "INSERT INTO tb_sys_role_menu_perm_rel VALUES ('ROLE-01', 'MENU-01')");
        jdbcTemplate.update(
                "INSERT INTO tb_sys_role_menu_perm_rel VALUES ('ROLE-01', 'MENU-02')");
        jdbcTemplate.update(
                "INSERT INTO tb_sys_role_menu_perm_rel VALUES ('ROLE-01', 'MENU-03')");
        jdbcTemplate.update(
                "INSERT INTO tb_sys_role_menu_perm_rel VALUES ('ROLE-03', 'MENU-02')");
    }
}
