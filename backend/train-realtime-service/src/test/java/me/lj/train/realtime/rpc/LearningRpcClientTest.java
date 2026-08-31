package me.lj.train.realtime.rpc;

import io.micrometer.core.instrument.Timer;
import me.lj.train.api.learning.LearningModels.LearningEventResultView;
import me.lj.train.api.learning.LearningModels.SubmitEventCommand;
import me.lj.train.api.learning.LearningSessionService;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;
import me.lj.train.realtime.metrics.RealtimeMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LearningRpcClientTest {

    private LearningSessionService learningSessionService;
    private LearningRpcClient client;

    @BeforeEach
    void setUp() {
        RealtimeMetrics metrics = mock(RealtimeMetrics.class);
        when(metrics.startRpc()).thenReturn(mock(Timer.Sample.class));
        learningSessionService = mock(LearningSessionService.class);
        client = new LearningRpcClient(metrics);
        ReflectionTestUtils.setField(client, "learningSessionService", learningSessionService);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldSetAndClearUserContextInSameRpcTask() {
        LoginUser user = student();
        when(learningSessionService.submitEvent(any(SubmitEventCommand.class)))
                .thenAnswer(invocation -> {
                    assertThat(UserContext.get()).isSameAs(user);
                    return Result.ok(eventResult());
                });

        LearningEventResultView result = client.submit(user, command()).block();

        assertThat(result).isNotNull();
        assertThat(UserContext.get()).isNull();
    }

    @Test
    void shouldClearUserContextWhenRpcThrows() {
        when(learningSessionService.submitEvent(any(SubmitEventCommand.class)))
                .thenThrow(new IllegalStateException("RPC失败"));

        assertThatThrownBy(() -> client.submit(student(), command()).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("RPC失败");
        assertThat(UserContext.get()).isNull();
    }

    private SubmitEventCommand command() {
        return new SubmitEventCommand(
                900L, "browser-one", "request-one", 1L,
                "SIGN_IN", null, 0L);
    }

    private LearningEventResultView eventResult() {
        return new LearningEventResultView(
                900L, "request-one", 1L, "SIGNED_IN", null,
                0L, 0L, 0L, 60_000L, false, false, LocalDateTime.now());
    }

    private LoginUser student() {
        LoginUser user = new LoginUser();
        user.setUserId(10L);
        user.setEnterpriseId(20L);
        return user;
    }
}
