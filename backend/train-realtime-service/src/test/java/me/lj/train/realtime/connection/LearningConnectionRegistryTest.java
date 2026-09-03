package me.lj.train.realtime.connection;

import me.lj.train.common.security.jwt.AccessTokenClaims;
import me.lj.train.common.security.model.LoginUser;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LearningConnectionRegistryTest {

    @Test
    void shouldReplaceSameUserSessionAndBrowserConnection() {
        LearningConnectionRegistry registry = new LearningConnectionRegistry();
        LearningConnection first = connection("first");
        LearningConnection second = connection("second");
        first.bind(900L, "browser-one");
        second.bind(900L, "browser-one");
        registry.register(first);

        LearningConnection replaced = registry.register(second);

        assertThat(replaced).isSameAs(first);
        assertThat(registry.size()).isEqualTo(1);
        assertThat(first.replacement().block()).isTrue();
        registry.remove(first);
        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.find(20L, 10L, 900L)).containsExactly(second);
        assertThat(registry.find(21L, 10L, 900L)).isEmpty();
    }

    private LearningConnection connection(String id) {
        LoginUser user = new LoginUser();
        user.setUserId(10L);
        user.setEnterpriseId(20L);
        AccessTokenClaims claims = new AccessTokenClaims(
                10L, 20L, "student", "session-1", 3L,
                Instant.now().plusSeconds(300));
        return new LearningConnection(id, claims, user, "trace-id");
    }
}
