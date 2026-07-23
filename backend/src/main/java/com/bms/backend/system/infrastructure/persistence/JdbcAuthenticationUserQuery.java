package com.bms.backend.system.infrastructure.persistence;

import com.bms.backend.common.domain.authentication.NormalizedLoginId;
import com.bms.backend.system.application.authentication.AccountStatus;
import com.bms.backend.system.application.authentication.AuthenticationAuthorizationSnapshot;
import com.bms.backend.system.application.authentication.AuthenticationMenu;
import com.bms.backend.system.application.authentication.AuthenticationRole;
import com.bms.backend.system.application.authentication.AuthenticationSessionState;
import com.bms.backend.system.application.authentication.AuthenticationUser;
import com.bms.backend.system.application.authentication.AuthenticationUserQuery;
import com.bms.backend.system.application.authentication.ReauthenticationCredential;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JdbcAuthenticationUserQuery implements AuthenticationUserQuery {

    private static final String LOGIN_CANDIDATE_SQL =
            """
            SELECT user_id,
                   emp_id,
                   login_id,
                   user_nm,
                   pwd_hash_val,
                   acnt_status_cd,
                   login_fail_cnt,
                   pwd_chg_dtm,
                   pwd_init_req_yn,
                   temp_pwd_expire_dtm,
                   sec_ver
            FROM tb_sys_user
            WHERE login_id = ?
              AND del_yn = 'N'
            """;

    private static final String AUTHORIZATION_ROLE_SQL =
            """
            SELECT role.role_id, role.role_nm
            FROM tb_sys_user_role_rel user_role
            JOIN tb_sys_role role
              ON role.role_id = user_role.role_id
             AND role.del_yn = 'N'
            WHERE user_role.user_id = ?
            ORDER BY role.role_id
            """;

    private static final String AUTHORIZATION_MENU_SQL =
            """
            SELECT DISTINCT
                   menu.menu_id,
                   menu.up_menu_id,
                   menu.menu_nm,
                   menu.menu_url,
                   menu.sort_seq
            FROM tb_sys_user_role_rel user_role
            JOIN tb_sys_role role
              ON role.role_id = user_role.role_id
             AND role.del_yn = 'N'
            JOIN tb_sys_role_menu_perm_rel permission
              ON permission.role_id = user_role.role_id
            JOIN tb_sys_menu menu
              ON menu.menu_id = permission.menu_id
             AND menu.del_yn = 'N'
            WHERE user_role.user_id = ?
            ORDER BY menu.menu_id
            """;

    private static final String SESSION_STATE_SQL =
            """
            SELECT user_id,
                   acnt_status_cd,
                   del_yn,
                   sec_ver,
                   pwd_init_req_yn,
                   pwd_chg_dtm
            FROM tb_sys_user
            WHERE user_id = ?
            """;

    private static final String REAUTHENTICATION_CREDENTIAL_SQL =
            """
            SELECT user_id,
                   pwd_hash_val,
                   acnt_status_cd,
                   del_yn,
                   sec_ver,
                   pwd_init_req_yn,
                   pwd_chg_dtm
            FROM tb_sys_user
            WHERE user_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<AuthenticationUser> findLoginCandidate(NormalizedLoginId loginId) {
        List<AuthenticationUser> users = jdbcTemplate.query(
                LOGIN_CANDIDATE_SQL, this::mapAuthenticationUser, loginId.value());
        return exactlyZeroOrOne(users, "AUTH_LOGIN_ID_NOT_UNIQUE");
    }

    @Override
    public AuthenticationAuthorizationSnapshot findAuthorizationSnapshot(String userId) {
        List<AuthenticationRole> roles = jdbcTemplate.query(
                AUTHORIZATION_ROLE_SQL,
                (resultSet, rowNumber) ->
                        new AuthenticationRole(
                                resultSet.getString("role_id"),
                                resultSet.getString("role_nm")),
                userId);
        List<AuthenticationMenu> menus = jdbcTemplate.query(
                AUTHORIZATION_MENU_SQL,
                (resultSet, rowNumber) ->
                        new AuthenticationMenu(
                                resultSet.getString("menu_id"),
                                resultSet.getString("up_menu_id"),
                                resultSet.getString("menu_nm"),
                                resultSet.getString("menu_url"),
                                resultSet.getInt("sort_seq")),
                userId);
        return new AuthenticationAuthorizationSnapshot(roles, menus);
    }

    @Override
    public Optional<AuthenticationSessionState> findSessionState(String userId) {
        List<AuthenticationSessionState> states = jdbcTemplate.query(
                SESSION_STATE_SQL,
                (resultSet, rowNumber) ->
                        new AuthenticationSessionState(
                                resultSet.getString("user_id"),
                                AccountStatus.fromDatabase(
                                        resultSet.getString("acnt_status_cd")),
                                flag(resultSet, "del_yn"),
                                resultSet.getInt("sec_ver"),
                                flag(resultSet, "pwd_init_req_yn"),
                                resultSet.getObject("pwd_chg_dtm", java.time.LocalDateTime.class)),
                userId);
        return exactlyZeroOrOne(states, "AUTH_USER_ID_NOT_UNIQUE");
    }

    @Override
    public Optional<ReauthenticationCredential> findReauthenticationCredential(String userId) {
        var credentials = jdbcTemplate.query(
                REAUTHENTICATION_CREDENTIAL_SQL,
                (resultSet, rowNumber) ->
                        new ReauthenticationCredential(
                                resultSet.getString("user_id"),
                                resultSet.getString("pwd_hash_val"),
                                AccountStatus.fromDatabase(
                                        resultSet.getString("acnt_status_cd")),
                                flag(resultSet, "del_yn"),
                                resultSet.getInt("sec_ver"),
                                flag(resultSet, "pwd_init_req_yn"),
                                resultSet.getObject(
                                        "pwd_chg_dtm", java.time.LocalDateTime.class)),
                userId);
        return exactlyZeroOrOne(credentials, "AUTH_USER_ID_NOT_UNIQUE");
    }

    private AuthenticationUser mapAuthenticationUser(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new AuthenticationUser(
                resultSet.getString("user_id"),
                resultSet.getString("emp_id"),
                NormalizedLoginId.from(resultSet.getString("login_id")),
                resultSet.getString("user_nm"),
                resultSet.getString("pwd_hash_val"),
                AccountStatus.fromDatabase(resultSet.getString("acnt_status_cd")),
                resultSet.getInt("login_fail_cnt"),
                resultSet.getObject("pwd_chg_dtm", java.time.LocalDateTime.class),
                flag(resultSet, "pwd_init_req_yn"),
                resultSet.getObject(
                        "temp_pwd_expire_dtm", java.time.LocalDateTime.class),
                resultSet.getInt("sec_ver"));
    }

    private boolean flag(ResultSet resultSet, String columnName) throws SQLException {
        return switch (resultSet.getString(columnName)) {
            case "Y" -> true;
            case "N" -> false;
            default -> throw new DataIntegrityViolationException("AUTH_BOOLEAN_FLAG_INVALID");
        };
    }

    private <T> Optional<T> exactlyZeroOrOne(List<T> values, String errorCode) {
        if (values.size() > 1) {
            throw new DataIntegrityViolationException(errorCode);
        }
        return values.stream().findFirst();
    }
}
