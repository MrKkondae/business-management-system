package com.bms.backend.system.application.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bms.backend.employee.application.BootstrapEmployeeService;
import com.bms.backend.global.persistence.MonotonicUlidGenerator;
import com.bms.backend.system.application.port.out.BootstrapAdminStore;
import com.bms.backend.system.application.port.out.BootstrapAdminStore.NewBootstrapAudit;
import com.bms.backend.system.application.port.out.BootstrapAdminStore.NewOrganization;
import com.bms.backend.system.application.port.out.BootstrapAdminStore.NewUser;
import com.bms.backend.system.application.port.out.BootstrapAdminStore.NewUserRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class BootstrapAdminServiceTests {

    private static final String ORGANIZATION_ID = "01KZ0000000000000000000001";
    private static final String EMPLOYEE_ID = "01KZ0000000000000000000002";
    private static final String USER_ID = "01KZ0000000000000000000003";
    private static final String LOG_ID = "01KZ0000000000000000000004";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 24, 6, 0);
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-24T06:00:00Z"), ZoneOffset.UTC);
    private static final BootstrapAdminInput INPUT = new BootstrapAdminInput(
            "BMS",
            "EMP-0001",
            "관리자",
            "admin",
            "Temporary!234",
            "admin@example.com",
            "010-0000-0000");

    @Test
    void validatesPrerequisitesAndCreatesTheAdministrator() {
        BootstrapAdminStore store = readyStore();
        BootstrapEmployeeService employeeService = mock(BootstrapEmployeeService.class);
        MonotonicUlidGenerator ulidGenerator = mock(MonotonicUlidGenerator.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(ulidGenerator.next()).thenReturn(ORGANIZATION_ID, USER_ID, LOG_ID);
        when(employeeService.register(
                        "EMP-0001",
                        "관리자",
                        ORGANIZATION_ID,
                        "admin@example.com",
                        "010-0000-0000",
                        NOW))
                .thenReturn(EMPLOYEE_ID);
        when(passwordEncoder.encode("Temporary!234")).thenReturn("PASSWORD-HASH");
        var service = new BootstrapAdminService(
                store, employeeService, ulidGenerator, passwordEncoder, CLOCK);

        String result = service.bootstrap(INPUT);

        assertThat(result).isEqualTo(USER_ID);
        verify(store).createOrganization(new NewOrganization(
                ORGANIZATION_ID, "BMS", "SYSTEM", NOW));
        verify(store).createUser(new NewUser(
                USER_ID,
                EMPLOYEE_ID,
                "admin",
                "관리자",
                "admin@example.com",
                "010-0000-0000",
                "PASSWORD-HASH",
                NOW,
                "SYSTEM",
                NOW,
                NOW.plusHours(24)));
        verify(store).assignRole(new NewUserRole(
                USER_ID,
                BootstrapAdminService.SYSTEM_ADMINISTRATOR_ROLE_ID,
                "SYSTEM",
                NOW));
        verify(store).recordBootstrapAudit(
                new NewBootstrapAudit(LOG_ID, USER_ID, "SYSTEM", NOW));
    }

    @Test
    void rejectsAnInvalidSystemAdministratorRoleBeforeWritingData() {
        BootstrapAdminStore store = mock(BootstrapAdminStore.class);
        BootstrapEmployeeService employeeService = mock(BootstrapEmployeeService.class);
        MonotonicUlidGenerator ulidGenerator = mock(MonotonicUlidGenerator.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        var service = new BootstrapAdminService(
                store, employeeService, ulidGenerator, passwordEncoder, CLOCK);

        assertThatThrownBy(() -> service.bootstrap(INPUT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("BOOTSTRAP_SYSTEM_ADMIN_ROLE_INVALID");
        verifyNoInteractions(employeeService, ulidGenerator, passwordEncoder);
    }

    @Test
    void rejectsExistingUsersBeforeWritingData() {
        BootstrapAdminStore store = readyStore();
        when(store.countUsers()).thenReturn(1);
        BootstrapEmployeeService employeeService = mock(BootstrapEmployeeService.class);
        MonotonicUlidGenerator ulidGenerator = mock(MonotonicUlidGenerator.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        var service = new BootstrapAdminService(
                store, employeeService, ulidGenerator, passwordEncoder, CLOCK);

        assertThatThrownBy(() -> service.bootstrap(INPUT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("BOOTSTRAP_USER_TABLE_NOT_EMPTY");
        verifyNoInteractions(employeeService, ulidGenerator, passwordEncoder);
    }

    @Test
    void rejectsMissingPermissionsOrCodesBeforeWritingData() {
        BootstrapAdminStore store = readyStore();
        when(store.countMissingRequiredCodes()).thenReturn(1);
        BootstrapEmployeeService employeeService = mock(BootstrapEmployeeService.class);
        MonotonicUlidGenerator ulidGenerator = mock(MonotonicUlidGenerator.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        var service = new BootstrapAdminService(
                store, employeeService, ulidGenerator, passwordEncoder, CLOCK);

        assertThatThrownBy(() -> service.bootstrap(INPUT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("BOOTSTRAP_REQUIRED_CODE_MISSING");
        verifyNoInteractions(employeeService, ulidGenerator, passwordEncoder);
    }

    private BootstrapAdminStore readyStore() {
        BootstrapAdminStore store = mock(BootstrapAdminStore.class);
        when(store.lockSystemAdministratorRole(
                        BootstrapAdminService.SYSTEM_ADMINISTRATOR_ROLE_ID,
                        "시스템관리자"))
                .thenReturn(true);
        return store;
    }
}
