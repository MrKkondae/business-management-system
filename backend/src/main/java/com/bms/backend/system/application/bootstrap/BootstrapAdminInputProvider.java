package com.bms.backend.system.application.bootstrap;

import com.bms.backend.common.domain.authentication.NormalizedLoginId;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdminInputProvider {

    static final String ORGANIZATION_NAME = "BMS_BOOTSTRAP_ORGANIZATION_NAME";
    static final String EMPLOYEE_NUMBER = "BMS_BOOTSTRAP_EMPLOYEE_NUMBER";
    static final String ADMIN_NAME = "BMS_BOOTSTRAP_ADMIN_NAME";
    static final String LOGIN_ID = "BMS_BOOTSTRAP_LOGIN_ID";
    static final String TEMPORARY_PASSWORD = "BMS_BOOTSTRAP_TEMPORARY_PASSWORD";
    static final String EMAIL = "BMS_BOOTSTRAP_EMAIL";
    static final String MOBILE = "BMS_BOOTSTRAP_MOBILE";

    private static final Pattern LOGIN_ID_PATTERN =
            Pattern.compile("^[a-z0-9][a-z0-9._-]{3,25}$");

    private final Map<String, String> environment;

    public BootstrapAdminInputProvider() {
        this(System.getenv());
    }

    BootstrapAdminInputProvider(Map<String, String> environment) {
        this.environment = environment;
    }

    public BootstrapAdminInput load() {
        String organizationName = required(ORGANIZATION_NAME, 100);
        String employeeNumber = required(EMPLOYEE_NUMBER, 30);
        String administratorName = required(ADMIN_NAME, 100);
        String loginId = NormalizedLoginId.from(required(LOGIN_ID, 26)).value();
        String temporaryPassword = requiredUntrimmed(TEMPORARY_PASSWORD);

        if (!LOGIN_ID_PATTERN.matcher(loginId).matches()) {
            throw new IllegalArgumentException("BOOTSTRAP_LOGIN_ID_INVALID");
        }
        validateTemporaryPassword(temporaryPassword, loginId, administratorName);

        return new BootstrapAdminInput(
                organizationName,
                employeeNumber,
                administratorName,
                loginId,
                temporaryPassword,
                optional(EMAIL, 100),
                optional(MOBILE, 20));
    }

    private String required(String name, int maxLength) {
        String value = environment.get(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("BOOTSTRAP_REQUIRED_ENV_MISSING:" + name);
        }
        value = value.trim();
        if (value.length() > maxLength) {
            throw new IllegalArgumentException("BOOTSTRAP_ENV_TOO_LONG:" + name);
        }
        return value;
    }

    private String requiredUntrimmed(String name) {
        String value = environment.get(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("BOOTSTRAP_REQUIRED_ENV_MISSING:" + name);
        }
        return value;
    }

    private String optional(String name, int maxLength) {
        String value = environment.get(name);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        value = value.trim();
        if (value.length() > maxLength) {
            throw new IllegalArgumentException("BOOTSTRAP_ENV_TOO_LONG:" + name);
        }
        return value;
    }

    private void validateTemporaryPassword(
            String password, String loginId, String administratorName) {
        if (password.length() < 12 || password.length() > 64) {
            throw new IllegalArgumentException("BOOTSTRAP_TEMPORARY_PASSWORD_LENGTH_INVALID");
        }

        int categories = 0;
        categories += password.chars().anyMatch(Character::isUpperCase) ? 1 : 0;
        categories += password.chars().anyMatch(Character::isLowerCase) ? 1 : 0;
        categories += password.chars().anyMatch(Character::isDigit) ? 1 : 0;
        categories += password.chars().anyMatch(
                        character -> !Character.isLetterOrDigit(character))
                ? 1
                : 0;
        if (categories < 3) {
            throw new IllegalArgumentException("BOOTSTRAP_TEMPORARY_PASSWORD_COMPLEXITY_INVALID");
        }

        String lowerPassword = password.toLowerCase(Locale.ROOT);
        if (lowerPassword.contains(loginId)
                || lowerPassword.contains(administratorName.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("BOOTSTRAP_TEMPORARY_PASSWORD_PERSONAL_VALUE");
        }
    }
}
