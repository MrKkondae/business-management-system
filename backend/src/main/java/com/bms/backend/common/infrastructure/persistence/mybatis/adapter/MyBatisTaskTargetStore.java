package com.bms.backend.common.infrastructure.persistence.mybatis.adapter;

import com.bms.backend.common.application.port.out.TaskTargetStore;
import com.bms.backend.common.infrastructure.persistence.mybatis.mapper.TaskTargetMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisTaskTargetStore implements TaskTargetStore {

    private final TaskTargetMapper taskTargetMapper;

    @Override
    public void create(
            String taskTargetId,
            String taskTargetTypeCode,
            String registeredBy,
            LocalDateTime registeredAt) {
        int inserted = taskTargetMapper.insert(
                taskTargetId, taskTargetTypeCode, registeredBy, registeredAt);
        if (inserted != 1) {
            throw new DataIntegrityViolationException(
                    "COMMON_TASK_TARGET_INSERT_COUNT_INVALID");
        }
    }
}
