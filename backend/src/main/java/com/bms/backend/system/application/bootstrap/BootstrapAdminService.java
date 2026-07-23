package com.bms.backend.system.application.bootstrap;

import com.bms.backend.employee.application.BootstrapEmployeeService;
import com.bms.backend.global.persistence.MonotonicUlidGenerator;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "bms.bootstrap-admin.enabled", havingValue = "true")
public class BootstrapAdminService {

    static final String SYSTEM_ADMINISTRATOR_ROLE_ID = "01KY3HYG000000000000000001";
    private static final String SYSTEM_ADMINISTRATOR_ROLE_NAME = "시스템관리자";
    private static final String SYSTEM_ACTOR = "SYSTEM";

    private final JdbcTemplate jdbcTemplate;
    private final BootstrapEmployeeService bootstrapEmployeeService;
    private final MonotonicUlidGenerator ulidGenerator;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional
    public String bootstrap(BootstrapAdminInput input) {
        lockAndValidateSystemAdministratorRole();
        validateEmptyUserTable();
        validateSystemAdministratorMenuPermissions();
        validateRequiredCodes();

        LocalDateTime now = LocalDateTime.now(clock);
        String organizationId = createOrganization(input.organizationName(), now);
        String employeeId = bootstrapEmployeeService.register(
                input.employeeNumber(),
                input.administratorName(),
                organizationId,
                input.emailAddress(),
                input.mobileNumber(),
                now);
        String userId = createUser(input, employeeId, now);
        assignSystemAdministratorRole(userId, now);
        recordBootstrapAudit(userId, now);
        return userId;
    }

    private void lockAndValidateSystemAdministratorRole() {
        var roleIds = jdbcTemplate.query(
                """
                SELECT role_id
                FROM tb_sys_role
                WHERE role_id = ? AND role_nm = ? AND del_yn = 'N'
                FOR UPDATE
                """,
                (resultSet, rowNumber) -> resultSet.getString("role_id"),
                SYSTEM_ADMINISTRATOR_ROLE_ID,
                SYSTEM_ADMINISTRATOR_ROLE_NAME);
        if (roleIds.size() != 1) {
            throw new IllegalStateException("BOOTSTRAP_SYSTEM_ADMIN_ROLE_INVALID");
        }
    }

    private void validateEmptyUserTable() {
        Integer userCount =
                jdbcTemplate.queryForObject("SELECT count(*) FROM tb_sys_user", Integer.class);
        if (userCount == null || userCount != 0) {
            throw new IllegalStateException("BOOTSTRAP_USER_TABLE_NOT_EMPTY");
        }
    }

    private void validateSystemAdministratorMenuPermissions() {
        Integer missingPermissionCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM tb_sys_menu menu
                WHERE menu.del_yn = 'N'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM tb_sys_role_menu_perm_rel permission
                      WHERE permission.role_id = ?
                        AND permission.menu_id = menu.menu_id
                  )
                """,
                Integer.class,
                SYSTEM_ADMINISTRATOR_ROLE_ID);
        if (missingPermissionCount == null || missingPermissionCount != 0) {
            throw new IllegalStateException("BOOTSTRAP_SYSTEM_ADMIN_MENU_PERMISSION_MISSING");
        }
    }

    private void validateRequiredCodes() {
        Integer missingGroupCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM (
                    VALUES
                        ('01KY3HYG200000000000000001'),
                        ('01KY3HYG200000000000000002'),
                        ('01KY3HYG200000000000000003'),
                        ('01KY3HYG200000000000000004'),
                        ('01KY3HYG200000000000000005'),
                        ('01KY3HYG200000000000000006'),
                        ('01KY3HYG200000000000000007')
                ) required(cd_grp_id)
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM tb_com_code_grp code_group
                    WHERE code_group.cd_grp_id = required.cd_grp_id
                      AND code_group.del_yn = 'N'
                )
                """,
                Integer.class);
        Integer missingCodeGroupDataCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM (
                    VALUES
                        ('01KY3HYG200000000000000001', 'ACTIVE'),
                        ('01KY3HYG200000000000000002', 'LOGIN_FAILURE'),
                        ('01KY3HYG200000000000000003', 'BAD_CREDENTIALS'),
                        ('01KY3HYG200000000000000004', 'MANUAL'),
                        ('01KY3HYG200000000000000005', 'COMPANY'),
                        ('01KY3HYG200000000000000006', 'EMPLOYEE'),
                        ('01KY3HYG200000000000000007', 'EMPLOYED')
                ) required(cd_grp_id, required_cd)
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM tb_com_code code
                    WHERE code.cd_grp_id = required.cd_grp_id
                      AND code.cd = required.required_cd
                      AND code.del_yn = 'N'
                )
                """,
                Integer.class);
        if (missingGroupCount == null
                || missingGroupCount != 0
                || missingCodeGroupDataCount == null
                || missingCodeGroupDataCount != 0) {
            throw new IllegalStateException("BOOTSTRAP_REQUIRED_CODE_MISSING");
        }
    }

    private String createOrganization(String organizationName, LocalDateTime now) {
        String organizationId = ulidGenerator.next();
        jdbcTemplate.update(
                """
                INSERT INTO tb_sys_org (
                    org_id, org_nm, org_type_cd, reg_id, reg_dtm, del_yn
                ) VALUES (?, ?, 'COMPANY', ?, ?, 'N')
                """,
                organizationId,
                organizationName,
                SYSTEM_ACTOR,
                now);
        return organizationId;
    }

    private String createUser(
            BootstrapAdminInput input, String employeeId, LocalDateTime now) {
        String userId = ulidGenerator.next();
        String passwordHash = passwordEncoder.encode(input.temporaryPassword());
        jdbcTemplate.update(
                """
                INSERT INTO tb_sys_user (
                    user_id, emp_id, login_id, user_nm, email_addr, mobile_no,
                    acnt_status_cd, pwd_hash_val, pwd_chg_dtm, login_fail_cnt,
                    inactive_dtm, last_login_dtm, pwd_init_req_yn,
                    reg_id, reg_dtm, mod_id, mod_dtm, del_yn, inactive_rsn_cd,
                    temp_pwd_expire_dtm, sec_ver
                ) VALUES (
                    ?, ?, ?, ?, ?, ?,
                    'ACTIVE', ?, ?, 0,
                    NULL, NULL, 'Y',
                    ?, ?, NULL, NULL, 'N', NULL,
                    ?, 1
                )
                """,
                userId,
                employeeId,
                input.loginId(),
                input.administratorName(),
                input.emailAddress(),
                input.mobileNumber(),
                passwordHash,
                now,
                SYSTEM_ACTOR,
                now,
                now.plusHours(24));
        return userId;
    }

    private void assignSystemAdministratorRole(String userId, LocalDateTime now) {
        jdbcTemplate.update(
                """
                INSERT INTO tb_sys_user_role_rel (user_id, role_id, reg_id, reg_dtm)
                VALUES (?, ?, ?, ?)
                """,
                userId,
                SYSTEM_ADMINISTRATOR_ROLE_ID,
                SYSTEM_ACTOR,
                now);
    }

    private void recordBootstrapAudit(String userId, LocalDateTime now) {
        jdbcTemplate.update(
                """
                INSERT INTO tb_sys_log (
                    log_id, log_type_cd, proc_user_id, occur_dtm, log_cont,
                    reg_id, reg_dtm, event_type_cd, tgt_type_cd, tgt_id,
                    proc_result_cd, chg_smry_cont, req_trace_id, access_ip_addr
                ) VALUES (
                    ?, 'SECURITY', NULL, ?, NULL,
                    ?, ?, 'BOOTSTRAP_ADMIN_CREATED', 'USER', ?,
                    'SUCCESS', '최초 시스템관리자 생성', NULL, NULL
                )
                """,
                ulidGenerator.next(),
                now,
                SYSTEM_ACTOR,
                now,
                userId);
    }
}
