package com.bms.backend.system.infrastructure.persistence.mybatis.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BootstrapAdminMapper {

    String findSystemAdministratorRoleForUpdate(
            @Param("roleId") String roleId,
            @Param("roleName") String roleName);

    int countUsers();

    int countMissingMenuPermissions(@Param("roleId") String roleId);

    int countMissingRequiredCodeGroups();

    int countMissingRequiredCodes();

    int insertOrganization(
            @Param("organizationId") String organizationId,
            @Param("organizationName") String organizationName,
            @Param("registeredBy") String registeredBy,
            @Param("registeredAt") LocalDateTime registeredAt);

    int insertUser(
            @Param("userId") String userId,
            @Param("employeeId") String employeeId,
            @Param("loginId") String loginId,
            @Param("userName") String userName,
            @Param("emailAddress") String emailAddress,
            @Param("mobileNumber") String mobileNumber,
            @Param("passwordHash") String passwordHash,
            @Param("passwordChangedAt") LocalDateTime passwordChangedAt,
            @Param("registeredBy") String registeredBy,
            @Param("registeredAt") LocalDateTime registeredAt,
            @Param("temporaryPasswordExpiresAt") LocalDateTime temporaryPasswordExpiresAt);

    int insertUserRole(
            @Param("userId") String userId,
            @Param("roleId") String roleId,
            @Param("registeredBy") String registeredBy,
            @Param("registeredAt") LocalDateTime registeredAt);

    int insertBootstrapAudit(
            @Param("logId") String logId,
            @Param("targetUserId") String targetUserId,
            @Param("registeredBy") String registeredBy,
            @Param("occurredAt") LocalDateTime occurredAt);
}
