package com.bms.backend.common.infrastructure.persistence.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.bms.backend.common.application.TaskTargetRegistrationService;
import com.bms.backend.employee.application.port.out.BootstrapEmployeeStore;
import com.bms.backend.employee.application.port.out.BootstrapEmployeeStore.NewEmployee;
import com.bms.backend.employee.application.port.out.BootstrapEmployeeStore.NewResource;
import com.bms.backend.system.application.port.out.BootstrapAdminStore;
import com.bms.backend.system.application.port.out.BootstrapAdminStore.NewBootstrapAudit;
import com.bms.backend.system.application.port.out.BootstrapAdminStore.NewOrganization;
import com.bms.backend.system.application.port.out.BootstrapAdminStore.NewUser;
import com.bms.backend.system.application.port.out.BootstrapAdminStore.NewUserRole;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        properties = "spring.main.web-application-type=none",
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class BootstrapPersistencePostgresIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final String ADMIN_ROLE_ID = "01KY3HYG000000000000000001";
    private static final String ORGANIZATION_ID = "01KZ0000000000000000000001";
    private static final String RESOURCE_ID = "01KZ0000000000000000000002";
    private static final String USER_ID = "01KZ0000000000000000000003";
    private static final String LOG_ID = "01KZ0000000000000000000004";

    @Autowired
    private TaskTargetRegistrationService taskTargetService;

    @Autowired
    private BootstrapEmployeeStore employeeStore;

    @Autowired
    private BootstrapAdminStore adminStore;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @Transactional
    @Rollback
    void appliesFlywayAndPersistsTheTaskTargetAgainstPostgres() {
        LocalDateTime registeredAt =
                LocalDateTime.now(ZoneOffset.UTC).withNano(0);

        String taskTargetId =
                taskTargetService.register("EMPLOYEE", "SYSTEM", registeredAt);

        var row = jdbc.queryForMap(
                """
                SELECT task_tgt_type_cd, reg_id, reg_dtm, del_yn
                FROM tb_com_task_tgt
                WHERE task_tgt_id = ?
                """,
                taskTargetId);
        assertThat(row)
                .containsEntry("task_tgt_type_cd", "EMPLOYEE")
                .containsEntry("reg_id", "SYSTEM")
                .containsEntry("del_yn", "N");
        assertThat(((java.sql.Timestamp) row.get("reg_dtm")).toLocalDateTime())
                .isEqualTo(registeredAt);
    }

    @Test
    @Transactional
    @Rollback
    void executesEmployeeAndAdminBootstrapMappersAgainstPostgres() {
        LocalDateTime registeredAt =
                LocalDateTime.now(ZoneOffset.UTC).withNano(0);

        assertThat(adminStore.lockSystemAdministratorRole(
                        ADMIN_ROLE_ID, "시스템관리자"))
                .isTrue();
        assertThat(adminStore.countUsers()).isZero();
        assertThat(adminStore.countMissingMenuPermissions(ADMIN_ROLE_ID)).isZero();
        assertThat(adminStore.countMissingRequiredCodeGroups()).isZero();
        assertThat(adminStore.countMissingRequiredCodes()).isZero();

        adminStore.createOrganization(
                new NewOrganization(ORGANIZATION_ID, "BMS", "SYSTEM", registeredAt));
        String taskTargetId =
                taskTargetService.register("EMPLOYEE", "SYSTEM", registeredAt);
        employeeStore.createResource(new NewResource(
                RESOURCE_ID,
                "관리자",
                "010-0000-0000",
                "admin@example.com",
                "SYSTEM",
                registeredAt,
                taskTargetId));
        employeeStore.createEmployee(new NewEmployee(
                RESOURCE_ID,
                "EMP-VERIFY",
                ORGANIZATION_ID,
                "SYSTEM",
                registeredAt));
        adminStore.createUser(new NewUser(
                USER_ID,
                RESOURCE_ID,
                "admin.verify",
                "관리자",
                "admin@example.com",
                "010-0000-0000",
                "PASSWORD-HASH",
                registeredAt,
                "SYSTEM",
                registeredAt,
                registeredAt.plusHours(24)));
        adminStore.assignRole(
                new NewUserRole(USER_ID, ADMIN_ROLE_ID, "SYSTEM", registeredAt));
        adminStore.recordBootstrapAudit(
                new NewBootstrapAudit(LOG_ID, USER_ID, "SYSTEM", registeredAt));

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM tb_res_employee WHERE res_id = ? AND emp_no = ?",
                        Integer.class,
                        RESOURCE_ID,
                        "EMP-VERIFY"))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM tb_sys_user WHERE user_id = ? AND emp_id = ?",
                        Integer.class,
                        USER_ID,
                        RESOURCE_ID))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM tb_sys_log WHERE log_id = ? AND tgt_id = ?",
                        Integer.class,
                        LOG_ID,
                        USER_ID))
                .isEqualTo(1);
    }

}
