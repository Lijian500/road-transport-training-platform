package me.lj.train.common.security.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {

    @Test
    void shouldRequireLengthLetterAndDigit() {
        assertThat(PasswordPolicy.isValid("Password1")).isTrue();
        assertThat(PasswordPolicy.isValid("12345678")).isFalse();
        assertThat(PasswordPolicy.isValid("abcdefgh")).isFalse();
        assertThat(PasswordPolicy.isValid("Pass1")).isFalse();
    }
}
