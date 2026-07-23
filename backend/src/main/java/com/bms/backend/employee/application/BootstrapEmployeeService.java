package com.bms.backend.employee.application;

import com.bms.backend.common.application.TaskTargetRegistrationService;
import com.bms.backend.global.persistence.MonotonicUlidGenerator;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BootstrapEmployeeService {

    private static final String SYSTEM_ACTOR = "SYSTEM";

    private final JdbcTemplate jdbcTemplate;
    private final TaskTargetRegistrationService taskTargetRegistrationService;
    private final MonotonicUlidGenerator ulidGenerator;

    public String register(
            String employeeNumber,
            String fullName,
            String organizationId,
            String emailAddress,
            String mobileNumber,
            LocalDateTime registeredAt) {
        Integer duplicateCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM tb_res_employee WHERE emp_no = ?",
                Integer.class,
                employeeNumber);
        if (duplicateCount != null && duplicateCount > 0) {
            throw new IllegalStateException("BOOTSTRAP_EMPLOYEE_NUMBER_DUPLICATE");
        }

        String taskTargetId =
                taskTargetRegistrationService.register("EMPLOYEE", SYSTEM_ACTOR, registeredAt);
        String resourceId = ulidGenerator.next();

        jdbcTemplate.update(
                """
                INSERT INTO tb_res_mst (
                    res_id, res_se_cd, full_nm, mobile_no, email_addr,
                    reg_id, reg_dtm, del_yn, task_tgt_id
                ) VALUES (?, 'EMPLOYEE', ?, ?, ?, ?, ?, 'N', ?)
                """,
                resourceId,
                fullName,
                mobileNumber,
                emailAddress,
                SYSTEM_ACTOR,
                registeredAt,
                taskTargetId);

        jdbcTemplate.update(
                """
                INSERT INTO tb_res_employee (
                    res_id, emp_no, org_id, employ_status_cd, reg_id, reg_dtm, del_yn
                ) VALUES (?, ?, ?, 'EMPLOYED', ?, ?, 'N')
                """,
                resourceId,
                employeeNumber,
                organizationId,
                SYSTEM_ACTOR,
                registeredAt);
        return resourceId;
    }
}
