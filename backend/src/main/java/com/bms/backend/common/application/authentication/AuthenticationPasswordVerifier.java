package com.bms.backend.common.application.authentication;

import java.util.Objects;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationPasswordVerifier {

    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    public AuthenticationPasswordVerifier(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    public void verifyAgainstDummyHash(String rawPassword) {
        passwordEncoder.matches(rawPassword, dummyPasswordHash);
    }
}
