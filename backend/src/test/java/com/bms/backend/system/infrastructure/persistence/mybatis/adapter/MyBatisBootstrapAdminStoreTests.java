package com.bms.backend.system.infrastructure.persistence.mybatis.adapter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bms.backend.system.application.port.out.BootstrapAdminStore.NewOrganization;
import com.bms.backend.system.infrastructure.persistence.mybatis.mapper.BootstrapAdminMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class MyBatisBootstrapAdminStoreTests {

    @Test
    void rejectsAnUnexpectedOrganizationInsertCount() {
        LocalDateTime registeredAt = LocalDateTime.of(2026, 7, 24, 6, 0);
        BootstrapAdminMapper mapper = mock(BootstrapAdminMapper.class);
        when(mapper.insertOrganization(
                        "01KZ0000000000000000000001",
                        "BMS",
                        "SYSTEM",
                        registeredAt))
                .thenReturn(0);
        var store = new MyBatisBootstrapAdminStore(mapper);
        var organization = new NewOrganization(
                "01KZ0000000000000000000001",
                "BMS",
                "SYSTEM",
                registeredAt);

        assertThatThrownBy(() -> store.createOrganization(organization))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessage("BOOTSTRAP_ORGANIZATION_INSERT_COUNT_INVALID");
    }
}
