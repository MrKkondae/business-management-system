package com.bms.backend.employee.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bms.backend.common.application.TaskTargetRegistrationService;
import com.bms.backend.employee.application.port.out.BootstrapEmployeeStore;
import com.bms.backend.employee.application.port.out.BootstrapEmployeeStore.NewEmployee;
import com.bms.backend.employee.application.port.out.BootstrapEmployeeStore.NewResource;
import com.bms.backend.global.persistence.MonotonicUlidGenerator;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class BootstrapEmployeeServiceTests {

    private static final String RESOURCE_ID = "01KZ0000000000000000000002";
    private static final String TASK_TARGET_ID = "01KZ0000000000000000000001";
    private static final LocalDateTime REGISTERED_AT =
            LocalDateTime.of(2026, 7, 24, 6, 0);

    @Test
    void createsTheTaskTargetResourceAndEmployeeThroughApplicationPorts() {
        BootstrapEmployeeStore store = mock(BootstrapEmployeeStore.class);
        TaskTargetRegistrationService taskTargetService =
                mock(TaskTargetRegistrationService.class);
        MonotonicUlidGenerator ulidGenerator = mock(MonotonicUlidGenerator.class);
        when(taskTargetService.register("EMPLOYEE", "SYSTEM", REGISTERED_AT))
                .thenReturn(TASK_TARGET_ID);
        when(ulidGenerator.next()).thenReturn(RESOURCE_ID);
        var service = new BootstrapEmployeeService(store, taskTargetService, ulidGenerator);

        String result = service.register(
                "EMP-0001",
                "관리자",
                "01KZ0000000000000000000000",
                "admin@example.com",
                "010-0000-0000",
                REGISTERED_AT);

        assertThat(result).isEqualTo(RESOURCE_ID);
        verify(store).createResource(new NewResource(
                RESOURCE_ID,
                "관리자",
                "010-0000-0000",
                "admin@example.com",
                "SYSTEM",
                REGISTERED_AT,
                TASK_TARGET_ID));
        verify(store).createEmployee(new NewEmployee(
                RESOURCE_ID,
                "EMP-0001",
                "01KZ0000000000000000000000",
                "SYSTEM",
                REGISTERED_AT));
    }

    @Test
    void rejectsADuplicateEmployeeNumberBeforeCreatingRelatedData() {
        BootstrapEmployeeStore store = mock(BootstrapEmployeeStore.class);
        TaskTargetRegistrationService taskTargetService =
                mock(TaskTargetRegistrationService.class);
        MonotonicUlidGenerator ulidGenerator = mock(MonotonicUlidGenerator.class);
        when(store.existsByEmployeeNumber("EMP-0001")).thenReturn(true);
        var service = new BootstrapEmployeeService(store, taskTargetService, ulidGenerator);

        assertThatThrownBy(() -> service.register(
                        "EMP-0001",
                        "관리자",
                        "01KZ0000000000000000000000",
                        null,
                        null,
                        REGISTERED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("BOOTSTRAP_EMPLOYEE_NUMBER_DUPLICATE");
        verify(store, never()).createResource(
                org.mockito.ArgumentMatchers.any(NewResource.class));
        verify(store, never()).createEmployee(
                org.mockito.ArgumentMatchers.any(NewEmployee.class));
        verifyNoInteractions(taskTargetService, ulidGenerator);
    }
}
