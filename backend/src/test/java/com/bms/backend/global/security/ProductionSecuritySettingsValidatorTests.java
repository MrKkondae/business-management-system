package com.bms.backend.global.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class ProductionSecuritySettingsValidatorTests {

    private final DefaultApplicationArguments arguments =
            new DefaultApplicationArguments(new String[0]);

    @Test
    void rejectsInsecureProductionSessionCookie() {
        ProductionSecuritySettingsValidator validator =
                new ProductionSecuritySettingsValidator(false, "x".repeat(32));

        assertThatThrownBy(() -> validator.run(arguments))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SECURITY_PRODUCTION_SESSION_COOKIE_NOT_SECURE");
    }

    @Test
    void rejectsMissingLoginIdentifierHmacKey() {
        ProductionSecuritySettingsValidator validator =
                new ProductionSecuritySettingsValidator(true, "");

        assertThatThrownBy(() -> validator.run(arguments))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SECURITY_PRODUCTION_LOGIN_ID_HMAC_KEY_REQUIRED");
    }

    @Test
    void acceptsSecureProductionSettings() {
        ProductionSecuritySettingsValidator validator =
                new ProductionSecuritySettingsValidator(true, "x".repeat(32));

        assertThatCode(() -> validator.run(arguments)).doesNotThrowAnyException();
    }
}
