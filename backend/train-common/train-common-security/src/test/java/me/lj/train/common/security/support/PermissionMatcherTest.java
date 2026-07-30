package me.lj.train.common.security.support;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionMatcherTest {

    @Test
    void shouldMatchExactAndWildcardPermission() {
        assertThat(PermissionMatcher.matches("admin:user:view", "admin:user:view")).isTrue();
        assertThat(PermissionMatcher.matches("admin:user:*", "admin:user:update")).isTrue();
        assertThat(PermissionMatcher.matches("*", "admin:role:delete")).isTrue();
        assertThat(PermissionMatcher.matches("admin:role:view", "admin:user:view")).isFalse();
    }

    @Test
    void shouldMatchAnyGrantedPermission() {
        assertThat(PermissionMatcher.matchesAny(
                Arrays.asList("admin:user:view", "admin:role:*"),
                "admin:role:create")).isTrue();
    }
}
