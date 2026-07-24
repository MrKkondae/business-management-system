package com.bms.backend.system.application.port.out;

import java.time.LocalDateTime;

public interface BootstrapAdminStore {

    boolean lockSystemAdministratorRole(String roleId, String roleName);

    int countUsers();

    int countMissingMenuPermissions(String roleId);

    int countMissingRequiredCodeGroups();

    int countMissingRequiredCodes();

    void createOrganization(NewOrganization organization);

    void createUser(NewUser user);

    void assignRole(NewUserRole userRole);

    void recordBootstrapAudit(NewBootstrapAudit audit);

    record NewOrganization(
            String organizationId,
            String organizationName,
            String registeredBy,
            LocalDateTime registeredAt) {}

    record NewUser(
            String userId,
            String employeeId,
            String loginId,
            String userName,
            String emailAddress,
            String mobileNumber,
            String passwordHash,
            LocalDateTime passwordChangedAt,
            String registeredBy,
            LocalDateTime registeredAt,
            LocalDateTime temporaryPasswordExpiresAt) {}

    record NewUserRole(
            String userId,
            String roleId,
            String registeredBy,
            LocalDateTime registeredAt) {}

    record NewBootstrapAudit(
            String logId,
            String targetUserId,
            String registeredBy,
            LocalDateTime occurredAt) {}
}
