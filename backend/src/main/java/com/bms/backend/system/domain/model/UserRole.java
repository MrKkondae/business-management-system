package com.bms.backend.system.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "tb_sys_user_role_rel")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRole {

    @EmbeddedId
    private UserRoleId id;

    @Column(name = "reg_id", length = 26, nullable = false)
    private String registeredBy;

    @Column(name = "reg_dtm", nullable = false)
    private LocalDateTime registeredAt;
}
