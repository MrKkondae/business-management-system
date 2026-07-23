package com.bms.backend.global.security;

import com.bms.backend.common.domain.authentication.NormalizedLoginId;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LoginIdentifierProtector {

    private static final Logger log = LoggerFactory.getLogger(LoginIdentifierProtector.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MINIMUM_KEY_BYTES = 32;

    private final SecretKeySpec key;

    public LoginIdentifierProtector(
            @Value("${bms.security.login-id-hmac-key:}") String configuredKey) {
        byte[] keyBytes;
        if (configuredKey == null || configuredKey.isBlank()) {
            keyBytes = new byte[MINIMUM_KEY_BYTES];
            new SecureRandom().nextBytes(keyBytes);
            log.warn(
                    "Login identifier HMAC key is not configured; "
                            + "using an ephemeral development key");
        } else {
            keyBytes = configuredKey.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < MINIMUM_KEY_BYTES) {
                throw new IllegalStateException("SECURITY_LOGIN_ID_HMAC_KEY_TOO_SHORT");
            }
        }
        this.key = new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
    }

    public String protect(NormalizedLoginId loginId) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            byte[] protectedValue =
                    mac.doFinal(loginId.value().getBytes(StandardCharsets.UTF_8));
            return "hmac-sha256:"
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(protectedValue);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SECURITY_LOGIN_ID_HMAC_UNAVAILABLE", exception);
        }
    }
}
