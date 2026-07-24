package com.bms.backend.common.infrastructure.persistence.mybatis.adapter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bms.backend.common.infrastructure.persistence.mybatis.mapper.TaskTargetMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class MyBatisTaskTargetStoreTests {

    @Test
    void rejectsAnUnexpectedInsertCount() {
        LocalDateTime registeredAt = LocalDateTime.of(2026, 7, 24, 6, 0);
        TaskTargetMapper mapper = mock(TaskTargetMapper.class);
        when(mapper.insert(
                        "01KZ0000000000000000000001",
                        "EMPLOYEE",
                        "SYSTEM",
                        registeredAt))
                .thenReturn(0);
        var store = new MyBatisTaskTargetStore(mapper);

        assertThatThrownBy(() -> store.create(
                        "01KZ0000000000000000000001",
                        "EMPLOYEE",
                        "SYSTEM",
                        registeredAt))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessage("COMMON_TASK_TARGET_INSERT_COUNT_INVALID");
    }
}
