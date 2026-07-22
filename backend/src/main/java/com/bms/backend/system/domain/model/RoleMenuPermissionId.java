package com.bms.backend.system.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@EqualsAndHashCode
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoleMenuPermissionId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "role_id", length = 26, nullable = false)
    private String roleId;

    @Column(name = "menu_id", length = 26, nullable = false)
    private String menuId;
}
