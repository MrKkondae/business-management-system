package com.bms.backend.employee.infrastructure.persistence.mybatis.adapter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bms.backend.employee.application.port.out.BootstrapEmployeeStore.NewResource;
import com.bms.backend.employee.infrastructure.persistence.mybatis.mapper.BootstrapEmployeeMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class MyBatisBootstrapEmployeeStoreTests {

    @Test
    void rejectsAnUnexpectedResourceInsertCount() {
        LocalDateTime registeredAt = LocalDateTime.of(2026, 7, 24, 6, 0);
        BootstrapEmployeeMapper mapper = mock(BootstrapEmployeeMapper.class);
        when(mapper.insertResource(
                        "01KZ0000000000000000000002",
                        "관리자",
                        null,
                        null,
                        "SYSTEM",
                        registeredAt,
                        "01KZ0000000000000000000001"))
                .thenReturn(0);
        var store = new MyBatisBootstrapEmployeeStore(mapper);
        var resource = new NewResource(
                "01KZ0000000000000000000002",
                "관리자",
                null,
                null,
                "SYSTEM",
                registeredAt,
                "01KZ0000000000000000000001");

        assertThatThrownBy(() -> store.createResource(resource))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessage("BOOTSTRAP_RESOURCE_INSERT_COUNT_INVALID");
    }
}
