package com.bms.backend.global.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionSecuritySettingsValidator implements ApplicationRunner {

    private final boolean secureSessionCookie;
    private final String loginIdHmacKey;

    public ProductionSecuritySettingsValidator(
            @Value("${server.servlet.session.cookie.secure:false}")
                    boolean secureSessionCookie,
            @Value("${bms.security.login-id-hmac-key:}") String loginIdHmacKey) {
        this.secureSessionCookie = secureSessionCookie;
        this.loginIdHmacKey = loginIdHmacKey;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!secureSessionCookie) {
            throw new IllegalStateException("SECURITY_PRODUCTION_SESSION_COOKIE_NOT_SECURE");
        }
        if (loginIdHmacKey == null
                || loginIdHmacKey.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("SECURITY_PRODUCTION_LOGIN_ID_HMAC_KEY_REQUIRED");
        }
    }
}
