package com.bms.backend.system.infrastructure.persistence;

import com.bms.backend.system.application.authentication.AccountStatus;
import com.bms.backend.system.application.authentication.InitialRegistrationAccount;
import com.bms.backend.system.application.authentication.InitialRegistrationStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcInitialRegistrationStore implements InitialRegistrationStore {

    private static final String LOCK_ACCOUNT_SQL =
            """
            SELECT user_id,
                   login_id,
                   user_nm,
                   pwd_hash_val,
                   acnt_status_cd,
                   del_yn,
                   pwd_init_req_yn,
                   pwd_chg_dtm,
                   temp_pwd_expire_dtm,
                   sec_ver
            FROM tb_sys_user
            WHERE user_id = ?
            FOR UPDATE
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<InitialRegistrationAccount> lockAccount(String userId) {
        List<InitialRegistrationAccount> accounts =
                jdbcTemplate.query(LOCK_ACCOUNT_SQL, this::mapAccount, userId);
        if (accounts.size() > 1) {
            throw new DataIntegrityViolationException("AUTH_USER_ID_NOT_UNIQUE");
        }
        return accounts.stream().findFirst();
    }

    @Override
    public int complete(
            InitialRegistrationAccount account,
            String newPasswordHash,
            String emailAddress,
            String mobileNumber,
            LocalDateTime completedAt) {
        int newSecurityVersion = account.securityVersion() + 1;
        int updated = jdbcTemplate.update(
                """
                UPDATE tb_sys_user
                SET pwd_hash_val = ?,
                    pwd_chg_dtm = ?,
                    pwd_init_req_yn = 'N',
                    temp_pwd_expire_dtm = NULL,
                    sec_ver = ?,
                    login_fail_cnt = 0,
                    email_addr = COALESCE(?, email_addr),
                    mobile_no = COALESCE(?, mobile_no),
                    mod_id = ?,
                    mod_dtm = ?
                WHERE user_id = ?
                  AND acnt_status_cd = 'ACTIVE'
                  AND del_yn = 'N'
                  AND pwd_init_req_yn = 'Y'
                  AND sec_ver = ?
                  AND pwd_chg_dtm = ?
                  AND temp_pwd_expire_dtm > ?
                """,
                newPasswordHash,
                completedAt,
                newSecurityVersion,
                emailAddress,
                mobileNumber,
                account.userId(),
                completedAt,
                account.userId(),
                account.securityVersion(),
                account.passwordChangedAt(),
                completedAt);
        if (updated != 1) {
            throw new DataIntegrityViolationException("AUTH_INITIAL_REGISTRATION_UPDATE_INVALID");
        }
        return newSecurityVersion;
    }

    private InitialRegistrationAccount mapAccount(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new InitialRegistrationAccount(
                resultSet.getString("user_id"),
                resultSet.getString("login_id"),
                resultSet.getString("user_nm"),
                resultSet.getString("pwd_hash_val"),
                AccountStatus.fromDatabase(resultSet.getString("acnt_status_cd")),
                flag(resultSet, "del_yn"),
                flag(resultSet, "pwd_init_req_yn"),
                resultSet.getObject("pwd_chg_dtm", LocalDateTime.class),
                resultSet.getObject("temp_pwd_expire_dtm", LocalDateTime.class),
                resultSet.getInt("sec_ver"));
    }

    private boolean flag(ResultSet resultSet, String columnName) throws SQLException {
        return switch (resultSet.getString(columnName)) {
            case "Y" -> true;
            case "N" -> false;
            default -> throw new DataIntegrityViolationException("AUTH_BOOLEAN_FLAG_INVALID");
        };
    }
}
