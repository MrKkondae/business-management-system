package com.bms.backend.system.application.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BootstrapAdminInputProviderTests {

    @Test
    void loadsAndNormalizesBootstrapInputWithoutExposingPassword() {
        Map<String, String> environment = validEnvironment();
        BootstrapAdminInput input = new BootstrapAdminInputProvider(environment).load();

        assertThat(input.organizationName()).isEqualTo("BMS 회사");
        assertThat(input.loginId()).isEqualTo("root.admin");
        assertThat(input.emailAddress()).isNull();
        assertThat(input.toString())
                .contains("temporaryPassword=***")
                .doesNotContain(environment.get(BootstrapAdminInputProvider.TEMPORARY_PASSWORD));
    }

    @Test
    void rejectsPasswordContainingLoginId() {
        Map<String, String> environment = validEnvironment();
        environment.put(
                BootstrapAdminInputProvider.TEMPORARY_PASSWORD, "Root.Admin-Secret9");

        assertThatThrownBy(() -> new BootstrapAdminInputProvider(environment).load())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("BOOTSTRAP_TEMPORARY_PASSWORD_PERSONAL_VALUE");
    }

    @Test
    void rejectsMissingSecretWithoutPrintingItsValue() {
        Map<String, String> environment = validEnvironment();
        environment.remove(BootstrapAdminInputProvider.TEMPORARY_PASSWORD);

        assertThatThrownBy(() -> new BootstrapAdminInputProvider(environment).load())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "BOOTSTRAP_REQUIRED_ENV_MISSING:"
                                + BootstrapAdminInputProvider.TEMPORARY_PASSWORD);
    }

    private Map<String, String> validEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put(BootstrapAdminInputProvider.ORGANIZATION_NAME, " BMS 회사 ");
        environment.put(BootstrapAdminInputProvider.EMPLOYEE_NUMBER, "EMP-0001");
        environment.put(BootstrapAdminInputProvider.ADMIN_NAME, "관리자");
        environment.put(BootstrapAdminInputProvider.LOGIN_ID, " Root.Admin ");
        environment.put(
                BootstrapAdminInputProvider.TEMPORARY_PASSWORD, "Temp-Secret-9082");
        environment.put(BootstrapAdminInputProvider.EMAIL, " ");
        return environment;
    }
}
