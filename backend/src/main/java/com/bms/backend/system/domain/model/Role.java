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
@Table(name = "tb_sys_role")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Role {

    @Id
    @Column(name = "role_id", length = 26, nullable = false)
    private String roleId;

    @Column(name = "role_nm", length = 100, nullable = false)
    private String roleName;

    @Column(name = "role_desc", length = 500)
    private String roleDescription;

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
}
