package com.bms.backend.system.application.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.bms.backend.global.security.PasswordConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class BootstrapPasswordEncoderTests {

    @Test
    void hashesTemporaryPasswordWithArgon2idAndRandomSalt() {
        PasswordEncoder encoder =
                new PasswordConfiguration().passwordEncoder();

        String first = encoder.encode("Temp-Secret-9082");
        String second = encoder.encode("Temp-Secret-9082");

        assertThat(first).startsWith("$argon2id$");
        assertThat(first).contains("m=19456,t=2,p=1");
        assertThat(first).isNotEqualTo(second);
        assertThat(encoder.matches("Temp-Secret-9082", first)).isTrue();
    }
}
