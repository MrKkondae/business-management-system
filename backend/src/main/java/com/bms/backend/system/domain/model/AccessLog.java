package com.bms.backend.system.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "tb_sys_access_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccessLog {

    @Id
    @Column(name = "access_log_id", length = 26, nullable = false)
    private String accessLogId;

    @Column(name = "user_id", length = 26)
    private String userId;

    @Column(name = "access_dtm", nullable = false)
    private LocalDateTime accessedAt;

    @Column(name = "logout_dtm")
    private LocalDateTime loggedOutAt;

    @Column(name = "access_ip_addr", length = 45)
    private String accessIpAddress;

    @Column(name = "succ_yn", nullable = false, columnDefinition = "char(1)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String successful;

    @Column(name = "reg_id", length = 26, nullable = false)
    private String registeredBy;

    @Column(name = "reg_dtm", nullable = false)
    private LocalDateTime registeredAt;

    @Column(name = "fail_rsn_cd", length = 20)
    private String failureReasonCode;

    @Column(name = "logout_type_cd", length = 20)
    private String logoutTypeCode;

    @Column(name = "req_trace_id", length = 64, nullable = false)
    private String requestTraceId;

    @Column(name = "login_id_hash_val", length = 255)
    private String loginIdHashValue;

    @Column(name = "user_agent_cont", columnDefinition = "text")
    private String userAgentContent;
}
