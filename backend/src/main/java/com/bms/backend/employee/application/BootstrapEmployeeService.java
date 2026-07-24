package com.bms.backend.employee.application;

import com.bms.backend.common.application.TaskTargetRegistrationService;
import com.bms.backend.employee.application.port.out.BootstrapEmployeeStore;
import com.bms.backend.employee.application.port.out.BootstrapEmployeeStore.NewEmployee;
import com.bms.backend.employee.application.port.out.BootstrapEmployeeStore.NewResource;
import com.bms.backend.global.persistence.MonotonicUlidGenerator;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BootstrapEmployeeService {

    private static final String SYSTEM_ACTOR = "SYSTEM";

    private final BootstrapEmployeeStore employeeStore;
    private final TaskTargetRegistrationService taskTargetRegistrationService;
    private final MonotonicUlidGenerator ulidGenerator;

    @Transactional
    public String register(
            String employeeNumber,
            String fullName,
            String organizationId,
            String emailAddress,
            String mobileNumber,
            LocalDateTime registeredAt) {
        if (employeeStore.existsByEmployeeNumber(employeeNumber)) {
            throw new IllegalStateException("BOOTSTRAP_EMPLOYEE_NUMBER_DUPLICATE");
        }

        String taskTargetId =
                taskTargetRegistrationService.register("EMPLOYEE", SYSTEM_ACTOR, registeredAt);
        String resourceId = ulidGenerator.next();

        employeeStore.createResource(new NewResource(
                resourceId,
                fullName,
                mobileNumber,
                emailAddress,
                SYSTEM_ACTOR,
                registeredAt,
                taskTargetId));
        employeeStore.createEmployee(new NewEmployee(
                resourceId,
                employeeNumber,
                organizationId,
                SYSTEM_ACTOR,
                registeredAt));
        return resourceId;
    }
}
