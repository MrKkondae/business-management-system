package com.bms.backend.common.infrastructure.persistence.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.bms.backend.common.application.TaskTargetRegistrationService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        properties = {
            "spring.main.web-application-type=none",
            "spring.datasource.url=jdbc:h2:mem:task-target-mybatis;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.datasource.hikari.connection-init-sql=",
            "spring.flyway.enabled=false"
        },
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class TaskTargetMyBatisIntegrationTests {

    private static final LocalDateTime REGISTERED_AT =
            LocalDateTime.of(2026, 7, 24, 6, 0);

    @Autowired
    private TaskTargetRegistrationService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @Sql(statements = """
            CREATE TABLE tb_com_task_tgt (
                task_tgt_id varchar(26) PRIMARY KEY,
                task_tgt_type_cd varchar(20) NOT NULL,
                reg_id varchar(26) NOT NULL,
                reg_dtm timestamp NOT NULL,
                mod_id varchar(26),
                mod_dtm timestamp,
                del_yn char(1) NOT NULL CHECK (del_yn IN ('Y', 'N'))
            );
            """)
    void loadsTheMapperXmlAndPersistsThroughTheApplicationPort() {
        String taskTargetId = service.register("EMPLOYEE", "SYSTEM", REGISTERED_AT);

        var row = jdbc.queryForMap(
                """
                SELECT task_tgt_type_cd, reg_id, reg_dtm, del_yn
                FROM tb_com_task_tgt
                WHERE task_tgt_id = ?
                """,
                taskTargetId);
        assertThat(taskTargetId).hasSize(26);
        assertThat(row)
                .containsEntry("TASK_TGT_TYPE_CD", "EMPLOYEE")
                .containsEntry("REG_ID", "SYSTEM")
                .containsEntry("DEL_YN", "N");
        assertThat(((java.sql.Timestamp) row.get("REG_DTM")).toLocalDateTime())
                .isEqualTo(REGISTERED_AT);
    }
}
