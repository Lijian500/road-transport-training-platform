package me.lj.train.realtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.lj.train.api.learning.FaceCheckEvents;
import me.lj.train.api.learning.FaceCheckModels.FaceCheckRealtimeEvent;
import me.lj.train.api.learning.FaceCheckModels.FaceCheckView;
import me.lj.train.common.security.jwt.AccessTokenClaims;
import me.lj.train.common.security.model.LoginUser;
import me.lj.train.realtime.connection.LearningConnection;
import me.lj.train.realtime.connection.LearningConnectionRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class FaceCheckRealtimeConsumerTest {

    @Test
    void shouldPushFaceCheckToMatchingConnectionOnly() {
        LearningConnectionRegistry registry = new LearningConnectionRegistry();
        LearningConnection matching = connection("matching", 20L, 10L, 900L);
        LearningConnection another = connection("another", 20L, 11L, 900L);
        registry.register(matching);
        registry.register(another);
        FaceCheckRealtimeConsumer consumer = new FaceCheckRealtimeConsumer(
                registry, new ObjectMapper().findAndRegisterModules());
        LocalDateTime now = LocalDateTime.now();

        consumer.consume(new FaceCheckRealtimeEvent(
                "event-1", FaceCheckEvents.REQUIRED_EVENT, now,
                20L, 10L, 900L,
                new FaceCheckView(1000L, 900L, "PENDING", now,
                        now.plusSeconds(60), 0, 3, 3,
                        null, null, null, null)));

        String message = matching.outbound().next().block(Duration.ofSeconds(1));
        assertThat(message).contains("FACE_CHECK_REQUIRED")
                .contains("\"requestId\":\"event-1\"");
    }

    private LearningConnection connection(
            String id,
            Long enterpriseId,
            Long userId,
            Long sessionId) {
        LoginUser user = new LoginUser();
        user.setEnterpriseId(enterpriseId);
        user.setUserId(userId);
        LearningConnection connection = new LearningConnection(
                id,
                new AccessTokenClaims(userId, enterpriseId, "student", "session-1",
                        3L, Instant.now().plusSeconds(300)),
                user,
                "trace-id");
        connection.bind(sessionId, id);
        return connection;
    }
}
