package com.bms.backend.common.application.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NewPasswordPolicyTests {

    private final NewPasswordPolicy policy = new NewPasswordPolicy();

    @Test
    void acceptsAPasswordWithSufficientLengthAndThreeCharacterCategories() {
        assertThat(policy.isSatisfiedBy("River!Glass82", "staff01", "홍길동")).isTrue();
    }

    @Test
    void rejectsPersonalWeakSequentialAndRepeatedPasswords() {
        assertThat(policy.isSatisfiedBy("staff01!River9", "staff01", "홍길동")).isFalse();
        assertThat(policy.isSatisfiedBy("Password!8294", "staff01", "홍길동")).isFalse();
        assertThat(policy.isSatisfiedBy("River!abcd82", "staff01", "홍길동")).isFalse();
        assertThat(policy.isSatisfiedBy("River!!!!829", "staff01", "홍길동")).isFalse();
        assertThat(policy.isSatisfiedBy("Ab1!Ab1!Ab1!", "staff01", "홍길동")).isFalse();
    }

    @Test
    void doesNotTrimOrNormalizeThePasswordBeforeEvaluation() {
        assertThat(policy.isSatisfiedBy(" River!Glass82 ", "staff01", "홍길동")).isTrue();
        assertThat(policy.isSatisfiedBy("             ", "staff01", "홍길동")).isFalse();
    }
}
