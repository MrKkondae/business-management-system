package com.bms.backend.common.application.authentication;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthenticationPasswordVerifierTests {

    @Test
    void verifiesUnknownUsersAgainstTheRuntimeDummyHash() {
        PasswordEncoder encoder = org.mockito.Mockito.mock(PasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn("$argon2id$dummy-hash");
        AuthenticationPasswordVerifier verifier =
                new AuthenticationPasswordVerifier(encoder);

        verifier.verifyAgainstDummyHash("untrusted-password");

        verify(encoder).matches("untrusted-password", "$argon2id$dummy-hash");
    }
}
