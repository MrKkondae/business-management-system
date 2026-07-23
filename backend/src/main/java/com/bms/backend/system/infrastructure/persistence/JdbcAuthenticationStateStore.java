package com.bms.backend.system.infrastructure.persistence;

import com.bms.backend.system.application.authentication.AccountStatus;
import com.bms.backend.system.application.authentication.AuthenticationSessionState;
import com.bms.backend.system.application.authentication.AuthenticationStateStore;
import com.bms.backend.system.application.authentication.FailedLoginUpdate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class JdbcAuthenticationStateStore implements AuthenticationStateStore {

    private static final int DEACTIVATION_FAILURE_COUNT = 6;
    private static final String SYSTEM_ACTOR = "SYSTEM";

    private static final String LOCK_USER_SQL =
            """
            SELECT user_id,
                   acnt_status_cd,
                   del_yn,
                   sec_ver,
                   pwd_init_req_yn,
                   pwd_chg_dtm,
                   login_fail_cnt
            FROM tb_sys_user
            WHERE user_id = ?
            FOR UPDATE
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public Optional<FailedLoginUpdate> recordFailedLogin(
            String userId,
            int expectedSecurityVersion,
            LocalDateTime expectedPasswordChangedAt,
            LocalDateTime occurredAt) {
        Optional<LockedAuthenticationUser> lockedUser =
                lockCurrentUser(userId, expectedSecurityVersion, expectedPasswordChangedAt);
        if (lockedUser.isEmpty()) {
            return Optional.empty();
        }

        LockedAuthenticationUser user = lockedUser.orElseThrow();
        int failureCount =
                Math.min(user.loginFailureCount() + 1, DEACTIVATION_FAILURE_COUNT);
        boolean deactivated = failureCount >= DEACTIVATION_FAILURE_COUNT;
        int securityVersion = user.securityVersion() + (deactivated ? 1 : 0);

        int updated = jdbcTemplate.update(
                """
                UPDATE tb_sys_user
                SET login_fail_cnt = ?,
                    acnt_status_cd = ?,
                    inactive_rsn_cd = ?,
                    inactive_dtm = ?,
                    sec_ver = ?,
                    mod_id = ?,
                    mod_dtm = ?
                WHERE user_id = ?
                """,
                failureCount,
                deactivated ? AccountStatus.INACTIVE.name() : AccountStatus.ACTIVE.name(),
                deactivated ? "LOGIN_FAILURE" : null,
                deactivated ? occurredAt : null,
                securityVersion,
                SYSTEM_ACTOR,
                occurredAt,
                userId);
        requireSingleUpdate(updated);
        return Optional.of(new FailedLoginUpdate(failureCount, deactivated, securityVersion));
    }

    @Override
    @Transactional
    public Optional<AuthenticationSessionState> recordSuccessfulLogin(
            String userId,
            int expectedSecurityVersion,
            LocalDateTime expectedPasswordChangedAt,
            LocalDateTime occurredAt) {
        Optional<LockedAuthenticationUser> lockedUser =
                lockCurrentUser(userId, expectedSecurityVersion, expectedPasswordChangedAt);
        if (lockedUser.isEmpty()) {
            return Optional.empty();
        }

        LockedAuthenticationUser user = lockedUser.orElseThrow();
        int updated = jdbcTemplate.update(
                """
                UPDATE tb_sys_user
                SET login_fail_cnt = 0,
                    last_login_dtm = ?,
                    mod_id = ?,
                    mod_dtm = ?
                WHERE user_id = ?
                """,
                occurredAt,
                SYSTEM_ACTOR,
                occurredAt,
                userId);
        requireSingleUpdate(updated);
        return Optional.of(new AuthenticationSessionState(
                user.userId(),
                AccountStatus.ACTIVE,
                false,
                user.securityVersion(),
                user.passwordChangeRequired(),
                user.passwordChangedAt()));
    }

    private Optional<LockedAuthenticationUser> lockCurrentUser(
            String userId,
            int expectedSecurityVersion,
            LocalDateTime expectedPasswordChangedAt) {
        List<LockedAuthenticationUser> users =
                jdbcTemplate.query(LOCK_USER_SQL, this::mapLockedUser, userId);
        if (users.size() > 1) {
            throw new DataIntegrityViolationException("AUTH_USER_ID_NOT_UNIQUE");
        }
        return users.stream()
                .filter(user -> !user.deleted())
                .filter(user -> user.accountStatus() == AccountStatus.ACTIVE)
                .filter(user -> user.securityVersion() == expectedSecurityVersion)
                .filter(user -> user.passwordChangedAt().equals(expectedPasswordChangedAt))
                .findFirst();
    }

    private LockedAuthenticationUser mapLockedUser(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new LockedAuthenticationUser(
                resultSet.getString("user_id"),
                AccountStatus.fromDatabase(resultSet.getString("acnt_status_cd")),
                flag(resultSet, "del_yn"),
                resultSet.getInt("sec_ver"),
                flag(resultSet, "pwd_init_req_yn"),
                resultSet.getObject("pwd_chg_dtm", LocalDateTime.class),
                resultSet.getInt("login_fail_cnt"));
    }

    private boolean flag(ResultSet resultSet, String columnName) throws SQLException {
        return switch (resultSet.getString(columnName)) {
            case "Y" -> true;
            case "N" -> false;
            default -> throw new DataIntegrityViolationException("AUTH_BOOLEAN_FLAG_INVALID");
        };
    }

    private void requireSingleUpdate(int updated) {
        if (updated != 1) {
            throw new DataIntegrityViolationException("AUTH_STATE_UPDATE_COUNT_INVALID");
        }
    }

    private record LockedAuthenticationUser(
            String userId,
            AccountStatus accountStatus,
            boolean deleted,
            int securityVersion,
            boolean passwordChangeRequired,
            LocalDateTime passwordChangedAt,
            int loginFailureCount) {}
}
