package com.bms.backend.common.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bms.backend.common.application.port.out.TaskTargetStore;
import com.bms.backend.global.persistence.MonotonicUlidGenerator;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TaskTargetRegistrationServiceTests {

    private static final String TASK_TARGET_ID = "01KZ0000000000000000000001";
    private static final LocalDateTime REGISTERED_AT =
            LocalDateTime.of(2026, 7, 24, 6, 0);

    @Test
    void generatesAnIdAndDelegatesPersistenceToTheOutputPort() {
        TaskTargetStore store = mock(TaskTargetStore.class);
        MonotonicUlidGenerator ulidGenerator = mock(MonotonicUlidGenerator.class);
        when(ulidGenerator.next()).thenReturn(TASK_TARGET_ID);
        var service = new TaskTargetRegistrationService(store, ulidGenerator);

        String result = service.register("EMPLOYEE", "SYSTEM", REGISTERED_AT);

        assertThat(result).isEqualTo(TASK_TARGET_ID);
        verify(store).create(TASK_TARGET_ID, "EMPLOYEE", "SYSTEM", REGISTERED_AT);
    }
}
