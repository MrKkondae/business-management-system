package com.bms.backend.common.application.port.out;

import java.time.LocalDateTime;

public interface TaskTargetStore {

    void create(
            String taskTargetId,
            String taskTargetTypeCode,
            String registeredBy,
            LocalDateTime registeredAt);
}
