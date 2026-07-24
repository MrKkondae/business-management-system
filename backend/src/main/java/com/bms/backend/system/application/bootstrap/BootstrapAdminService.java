package com.bms.backend.system.application.bootstrap;

import com.bms.backend.employee.application.BootstrapEmployeeService;
import com.bms.backend.global.persistence.MonotonicUlidGenerator;
import com.bms.backend.system.application.port.out.BootstrapAdminStore;
import com.bms.backend.system.application.port.out.BootstrapAdminStore.NewBootstrapAudit;
import com.bms.backend.system.application.port.out.BootstrapAdminStore.NewOrganization;
import com.bms.backend.system.application.port.out.BootstrapAdminStore.NewUser;
import com.bms.backend.system.application.port.out.BootstrapAdminStore.NewUserRole;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

    private final BootstrapAdminStore adminStore;
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
        if (!adminStore.lockSystemAdministratorRole(
                SYSTEM_ADMINISTRATOR_ROLE_ID, SYSTEM_ADMINISTRATOR_ROLE_NAME)) {
            throw new IllegalStateException("BOOTSTRAP_SYSTEM_ADMIN_ROLE_INVALID");
        }
    }

    private void validateEmptyUserTable() {
        if (adminStore.countUsers() != 0) {
            throw new IllegalStateException("BOOTSTRAP_USER_TABLE_NOT_EMPTY");
        }
    }

    private void validateSystemAdministratorMenuPermissions() {
        if (adminStore.countMissingMenuPermissions(SYSTEM_ADMINISTRATOR_ROLE_ID) != 0) {
            throw new IllegalStateException("BOOTSTRAP_SYSTEM_ADMIN_MENU_PERMISSION_MISSING");
        }
    }

    private void validateRequiredCodes() {
        if (adminStore.countMissingRequiredCodeGroups() != 0
                || adminStore.countMissingRequiredCodes() != 0) {
            throw new IllegalStateException("BOOTSTRAP_REQUIRED_CODE_MISSING");
        }
    }

    private String createOrganization(String organizationName, LocalDateTime now) {
        String organizationId = ulidGenerator.next();
        adminStore.createOrganization(new NewOrganization(
                organizationId,
                organizationName,
                SYSTEM_ACTOR,
                now));
        return organizationId;
    }

    private String createUser(
            BootstrapAdminInput input, String employeeId, LocalDateTime now) {
        String userId = ulidGenerator.next();
        String passwordHash = passwordEncoder.encode(input.temporaryPassword());
        adminStore.createUser(new NewUser(
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
                now.plusHours(24)));
        return userId;
    }

    private void assignSystemAdministratorRole(String userId, LocalDateTime now) {
        adminStore.assignRole(new NewUserRole(
                userId,
                SYSTEM_ADMINISTRATOR_ROLE_ID,
                SYSTEM_ACTOR,
                now));
    }

    private void recordBootstrapAudit(String userId, LocalDateTime now) {
        adminStore.recordBootstrapAudit(new NewBootstrapAudit(
                ulidGenerator.next(),
                userId,
                SYSTEM_ACTOR,
                now));
    }
}
