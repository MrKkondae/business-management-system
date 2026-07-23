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
@Table(name = "tb_sys_user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SystemUser {

    @Id
    @Column(name = "user_id", length = 26, nullable = false)
    private String userId;

    @Column(name = "emp_id", length = 26)
    private String employeeId;

    @Column(name = "login_id", length = 26, nullable = false)
    private String loginId;

    @Column(name = "user_nm", length = 100, nullable = false)
    private String userName;

    @Column(name = "email_addr", length = 100)
    private String emailAddress;

    @Column(name = "mobile_no", length = 20)
    private String mobileNumber;

    @Column(name = "acnt_status_cd", length = 20, nullable = false)
    private String accountStatusCode;

    @Column(name = "pwd_hash_val", length = 255, nullable = false)
    private String passwordHashValue;

    @Column(name = "pwd_chg_dtm", nullable = false)
    private LocalDateTime passwordChangedAt;

    @Column(name = "login_fail_cnt", nullable = false)
    private Integer loginFailureCount;

    @Column(name = "inactive_dtm")
    private LocalDateTime inactiveAt;

    @Column(name = "last_login_dtm")
    private LocalDateTime lastLoginAt;

    @Column(name = "pwd_init_req_yn", nullable = false, columnDefinition = "char(1)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String passwordInitializationRequired;

    @Column(name = "temp_pwd_expire_dtm")
    private LocalDateTime temporaryPasswordExpiresAt;

    @Column(name = "sec_ver", nullable = false)
    private Integer securityVersion;

    @Column(name = "reg_id", length = 26, nullable = false)
    private String registeredBy;

    @Column(name = "reg_dtm", nullable = false)
    private LocalDateTime registeredAt;

    @Column(name = "mod_id", length = 26)
    private String modifiedBy;

    @Column(name = "mod_dtm")
    private LocalDateTime modifiedAt;

    @Column(name = "del_yn", nullable = false, columnDefinition = "char(1)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String deleted;

    @Column(name = "inactive_rsn_cd", length = 20)
    private String inactiveReasonCode;
}
