package com.bms.backend.common.domain.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NormalizedLoginIdTests {

    @Test
    void trimsAndLowercasesLoginIdUsingTheSharedRule() {
        NormalizedLoginId loginId = NormalizedLoginId.from("  Root.Admin  ");

        assertThat(loginId.value()).isEqualTo("root.admin");
        assertThat(loginId.toString()).doesNotContain("root.admin");
    }

    @Test
    void rejectsMissingOrBlankLoginId() {
        assertThatThrownBy(() -> NormalizedLoginId.from(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("LOGIN_ID_REQUIRED");
        assertThatThrownBy(() -> NormalizedLoginId.from("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("LOGIN_ID_REQUIRED");
    }
}
