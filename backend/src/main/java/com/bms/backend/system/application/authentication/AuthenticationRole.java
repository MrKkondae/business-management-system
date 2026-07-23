package com.bms.backend.system.application.authentication;

import java.util.Objects;

public record AuthenticationRole(String roleId, String roleName) {

    public AuthenticationRole {
        Objects.requireNonNull(roleId, "roleId");
        Objects.requireNonNull(roleName, "roleName");
    }
}
