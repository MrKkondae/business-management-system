package com.bms.backend.system.infrastructure.persistence.mybatis.adapter;

import com.bms.backend.system.application.port.out.BootstrapAdminStore;
import com.bms.backend.system.infrastructure.persistence.mybatis.mapper.BootstrapAdminMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisBootstrapAdminStore implements BootstrapAdminStore {

    private final BootstrapAdminMapper mapper;

    @Override
    public boolean lockSystemAdministratorRole(String roleId, String roleName) {
        return mapper.findSystemAdministratorRoleForUpdate(roleId, roleName) != null;
    }

    @Override
    public int countUsers() {
        return mapper.countUsers();
    }

    @Override
    public int countMissingMenuPermissions(String roleId) {
        return mapper.countMissingMenuPermissions(roleId);
    }

    @Override
    public int countMissingRequiredCodeGroups() {
        return mapper.countMissingRequiredCodeGroups();
    }

    @Override
    public int countMissingRequiredCodes() {
        return mapper.countMissingRequiredCodes();
    }

    @Override
    public void createOrganization(NewOrganization organization) {
        requireSingleInsert(
                mapper.insertOrganization(
                        organization.organizationId(),
                        organization.organizationName(),
                        organization.registeredBy(),
                        organization.registeredAt()),
                "BOOTSTRAP_ORGANIZATION_INSERT_COUNT_INVALID");
    }

    @Override
    public void createUser(NewUser user) {
        requireSingleInsert(
                mapper.insertUser(
                        user.userId(),
                        user.employeeId(),
                        user.loginId(),
                        user.userName(),
                        user.emailAddress(),
                        user.mobileNumber(),
                        user.passwordHash(),
                        user.passwordChangedAt(),
                        user.registeredBy(),
                        user.registeredAt(),
                        user.temporaryPasswordExpiresAt()),
                "BOOTSTRAP_USER_INSERT_COUNT_INVALID");
    }

    @Override
    public void assignRole(NewUserRole userRole) {
        requireSingleInsert(
                mapper.insertUserRole(
                        userRole.userId(),
                        userRole.roleId(),
                        userRole.registeredBy(),
                        userRole.registeredAt()),
                "BOOTSTRAP_USER_ROLE_INSERT_COUNT_INVALID");
    }

    @Override
    public void recordBootstrapAudit(NewBootstrapAudit audit) {
        requireSingleInsert(
                mapper.insertBootstrapAudit(
                        audit.logId(),
                        audit.targetUserId(),
                        audit.registeredBy(),
                        audit.occurredAt()),
                "BOOTSTRAP_AUDIT_INSERT_COUNT_INVALID");
    }

    private void requireSingleInsert(int inserted, String errorCode) {
        if (inserted != 1) {
            throw new DataIntegrityViolationException(errorCode);
        }
    }
}
