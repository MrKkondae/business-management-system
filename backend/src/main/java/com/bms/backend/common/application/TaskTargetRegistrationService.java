package com.bms.backend.common.application;

import com.bms.backend.global.persistence.MonotonicUlidGenerator;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TaskTargetRegistrationService {

    private final JdbcTemplate jdbcTemplate;
    private final MonotonicUlidGenerator ulidGenerator;

    public String register(String targetTypeCode, String registeredBy, LocalDateTime registeredAt) {
        String targetId = ulidGenerator.next();
        jdbcTemplate.update(
                """
                INSERT INTO tb_com_task_tgt (
                    task_tgt_id, task_tgt_type_cd, reg_id, reg_dtm, del_yn
                ) VALUES (?, ?, ?, ?, 'N')
                """,
                targetId,
                targetTypeCode,
                registeredBy,
                registeredAt);
        return targetId;
    }
}
