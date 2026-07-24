package com.bms.backend.employee.application.port.out;

import java.time.LocalDateTime;

public interface BootstrapEmployeeStore {

    boolean existsByEmployeeNumber(String employeeNumber);

    void createResource(NewResource resource);

    void createEmployee(NewEmployee employee);

    record NewResource(
            String resourceId,
            String fullName,
            String mobileNumber,
            String emailAddress,
            String registeredBy,
            LocalDateTime registeredAt,
            String taskTargetId) {}

    record NewEmployee(
            String resourceId,
            String employeeNumber,
            String organizationId,
            String registeredBy,
            LocalDateTime registeredAt) {}
}
