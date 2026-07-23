package com.bms.backend.common.application.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LoginAttemptContextTests {

    @Test
    void sanitizesControlCharactersAndCapsTheUserAgent() {
        LoginAttemptContext context =
                new LoginAttemptContext("TRACE-01", "203.0.113.10", "agent\r\n" + "x".repeat(600));

        assertThat(context.userAgent()).doesNotContain("\r", "\n").hasSize(512);
    }

    @Test
    void rejectsInvalidTraceAndIpValues() {
        assertThatThrownBy(() -> new LoginAttemptContext("", "203.0.113.10", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("LOGIN_TRACE_ID_REQUIRED");
        assertThatThrownBy(() -> new LoginAttemptContext("TRACE-01", "x".repeat(46), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("LOGIN_CLIENT_IP_REQUIRED");
    }
}
