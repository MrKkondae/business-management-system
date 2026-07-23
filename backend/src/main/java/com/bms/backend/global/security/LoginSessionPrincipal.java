package com.bms.backend.global.security;

import com.bms.backend.common.application.authentication.LoginSession;
import java.security.Principal;
import java.util.Objects;

public record LoginSessionPrincipal(LoginSession session) implements Principal {

    public LoginSessionPrincipal {
        Objects.requireNonNull(session, "session");
    }

    @Override
    public String getName() {
        return session.userId();
    }
}
