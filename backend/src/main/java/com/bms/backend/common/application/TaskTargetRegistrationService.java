package com.bms.backend.common.application;

import com.bms.backend.common.application.port.out.TaskTargetStore;
import com.bms.backend.global.persistence.MonotonicUlidGenerator;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskTargetRegistrationService {

    private final TaskTargetStore taskTargetStore;
    private final MonotonicUlidGenerator ulidGenerator;

    @Transactional
    public String register(String targetTypeCode, String registeredBy, LocalDateTime registeredAt) {
        String targetId = ulidGenerator.next();
        taskTargetStore.create(targetId, targetTypeCode, registeredBy, registeredAt);
        return targetId;
    }
}
