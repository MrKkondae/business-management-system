package com.bms.backend.system.application.authentication;

import java.util.List;
import java.util.Objects;

public record AuthenticationAuthorizationSnapshot(
        List<AuthenticationRole> roles, List<AuthenticationMenu> menus) {

    public AuthenticationAuthorizationSnapshot {
        roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
        menus = List.copyOf(Objects.requireNonNull(menus, "menus"));
    }
}
