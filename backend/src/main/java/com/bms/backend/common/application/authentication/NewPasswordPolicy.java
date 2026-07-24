package com.bms.backend.common.application.authentication;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class NewPasswordPolicy {

    private static final List<String> WEAK_FRAGMENTS =
            List.of("password", "qwerty", "letmein", "welcome", "admin");

    public boolean isSatisfiedBy(String password, String loginId, String displayName) {
        if (password == null || password.length() < 12 || password.length() > 64) {
            return false;
        }
        if (password.chars().allMatch(Character::isWhitespace)) {
            return false;
        }

        int categories = 0;
        categories += password.chars().anyMatch(Character::isUpperCase) ? 1 : 0;
        categories += password.chars().anyMatch(Character::isLowerCase) ? 1 : 0;
        categories += password.chars().anyMatch(Character::isDigit) ? 1 : 0;
        categories += password.chars().anyMatch(character -> !Character.isLetterOrDigit(character))
                ? 1
                : 0;
        if (categories < 3) {
            return false;
        }

        String lowerPassword = password.toLowerCase(Locale.ROOT);
        if (containsPersonalValue(lowerPassword, loginId)
                || containsPersonalValue(lowerPassword, displayName)
                || WEAK_FRAGMENTS.stream().anyMatch(lowerPassword::contains)) {
            return false;
        }
        return !hasFourCharacterSequence(password)
                && !hasFourRepeatedCharacters(password)
                && !isRepeatedPattern(password);
    }

    private boolean containsPersonalValue(String lowerPassword, String value) {
        if (value == null) {
            return false;
        }
        String candidate = value.toLowerCase(Locale.ROOT);
        return candidate.length() >= 3 && lowerPassword.contains(candidate);
    }

    private boolean hasFourCharacterSequence(String password) {
        for (int start = 0; start <= password.length() - 4; start++) {
            int direction = password.charAt(start + 1) - password.charAt(start);
            if (Math.abs(direction) != 1) {
                continue;
            }
            boolean sequence = true;
            for (int offset = 2; offset < 4; offset++) {
                if (password.charAt(start + offset)
                                - password.charAt(start + offset - 1)
                        != direction) {
                    sequence = false;
                    break;
                }
            }
            if (sequence) {
                return true;
            }
        }
        return false;
    }

    private boolean hasFourRepeatedCharacters(String password) {
        for (int index = 3; index < password.length(); index++) {
            char character = password.charAt(index);
            if (character == password.charAt(index - 1)
                    && character == password.charAt(index - 2)
                    && character == password.charAt(index - 3)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRepeatedPattern(String password) {
        for (int patternLength = 1; patternLength <= password.length() / 2; patternLength++) {
            if (password.length() % patternLength != 0) {
                continue;
            }
            String pattern = password.substring(0, patternLength);
            if (pattern.repeat(password.length() / patternLength).equals(password)) {
                return true;
            }
        }
        return false;
    }
}
