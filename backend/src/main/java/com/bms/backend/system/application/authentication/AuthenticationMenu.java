package com.bms.backend.system.application.authentication;

import java.util.Objects;

public record AuthenticationMenu(
        String menuId,
        String parentMenuId,
        String menuName,
        String menuUrl,
        int sortOrder) {

    public AuthenticationMenu {
        Objects.requireNonNull(menuId, "menuId");
        Objects.requireNonNull(menuName, "menuName");
    }
}
