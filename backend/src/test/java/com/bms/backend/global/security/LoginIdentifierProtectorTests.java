package com.bms.backend.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bms.backend.common.domain.authentication.NormalizedLoginId;
import org.junit.jupiter.api.Test;

class LoginIdentifierProtectorTests {

    @Test
    void producesAStableNonReversibleValueWithoutExposingTheLoginId() {
        LoginIdentifierProtector protector =
                new LoginIdentifierProtector("a-secure-test-key-with-at-least-32-bytes");

        String first = protector.protect(NormalizedLoginId.from("Admin"));
        String second = protector.protect(NormalizedLoginId.from(" admin "));

        assertThat(first).isEqualTo(second)
                .startsWith("hmac-sha256:")
                .doesNotContain("admin");
    }

    @Test
    void differentKeysProduceDifferentProtectedValues() {
        var loginId = NormalizedLoginId.from("admin");

        assertThat(new LoginIdentifierProtector("a".repeat(32)).protect(loginId))
                .isNotEqualTo(
                        new LoginIdentifierProtector("b".repeat(32)).protect(loginId));
    }

    @Test
    void rejectsConfiguredKeysShorterThanThirtyTwoBytes() {
        assertThatThrownBy(() -> new LoginIdentifierProtector("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SECURITY_LOGIN_ID_HMAC_KEY_TOO_SHORT");
    }
}
