package com.bms.backend.system.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "tb_sys_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SystemLog {

    @Id
    @Column(name = "log_id", length = 26, nullable = false)
    private String logId;

    @Column(name = "log_type_cd", length = 20, nullable = false)
    private String logTypeCode;

    @Column(name = "proc_user_id", length = 26)
    private String processingUserId;

    @Column(name = "occur_dtm", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "log_cont", columnDefinition = "text")
    private String logContent;

    @Column(name = "reg_id", length = 26, nullable = false)
    private String registeredBy;

    @Column(name = "reg_dtm", nullable = false)
    private LocalDateTime registeredAt;

    @Column(name = "event_type_cd", length = 50, nullable = false)
    private String eventTypeCode;

    @Column(name = "tgt_type_cd", length = 20)
    private String targetTypeCode;

    @Column(name = "tgt_id", length = 26)
    private String targetId;

    @Column(name = "proc_result_cd", length = 20, nullable = false)
    private String processingResultCode;

    @Column(name = "chg_smry_cont", columnDefinition = "text")
    private String changeSummaryContent;

    @Column(name = "req_trace_id", length = 64)
    private String requestTraceId;

    @Column(name = "access_ip_addr", length = 45)
    private String accessIpAddress;
}
