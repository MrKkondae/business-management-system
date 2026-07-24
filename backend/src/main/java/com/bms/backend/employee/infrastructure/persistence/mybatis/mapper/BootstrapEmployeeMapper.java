package com.bms.backend.employee.infrastructure.persistence.mybatis.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BootstrapEmployeeMapper {

    int countByEmployeeNumber(@Param("employeeNumber") String employeeNumber);

    int insertResource(
            @Param("resourceId") String resourceId,
            @Param("fullName") String fullName,
            @Param("mobileNumber") String mobileNumber,
            @Param("emailAddress") String emailAddress,
            @Param("registeredBy") String registeredBy,
            @Param("registeredAt") LocalDateTime registeredAt,
            @Param("taskTargetId") String taskTargetId);

    int insertEmployee(
            @Param("resourceId") String resourceId,
            @Param("employeeNumber") String employeeNumber,
            @Param("organizationId") String organizationId,
            @Param("registeredBy") String registeredBy,
            @Param("registeredAt") LocalDateTime registeredAt);
}
