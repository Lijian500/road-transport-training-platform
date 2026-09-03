package me.lj.train.realtime.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.lj.train.api.learning.LearningModels.LearningEventResultView;
import me.lj.train.api.learning.LearningModels.LearningSessionView;
import me.lj.train.api.learning.LearningModels.SubmitEventCommand;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.security.jwt.AccessTokenClaims;
import me.lj.train.common.security.model.LoginUser;
import me.lj.train.realtime.config.RealtimeProperties;
import me.lj.train.realtime.connection.LearningConnectionRegistry;
import me.lj.train.realtime.metrics.RealtimeMetrics;
import me.lj.train.realtime.rpc.LearningRpcClient;
import me.lj.train.realtime.rpc.RealtimeRpcException;
import me.lj.train.realtime.security.RealtimeAuthException;
import me.lj.train.realtime.security.RealtimeAuthorizationService;
import me.lj.train.realtime.security.RealtimePrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningWebSocketHandlerTest {

    private ObjectMapper objectMapper;
    private LearningRpcClient rpcClient;
    private LearningWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        rpcClient = mock(LearningRpcClient.class);
        handler = new LearningWebSocketHandler(
                objectMapper,
                rpcClient,
                mock(RealtimeAuthorizationService.class),
                new LearningConnectionRegistry(),
                mock(RealtimeMetrics.class),
                new RealtimeProperties());
    }

    @Test
    void shouldBindThenSubmitLearningEventSerially() throws Exception {
        Sinks.One<LearningSessionView> bindResult = Sinks.one();
        when(rpcClient.bind(any(LoginUser.class), any(Long.class), anyString()))
                .thenReturn(bindResult.asMono());
        when(rpcClient.submit(any(LoginUser.class), any(SubmitEventCommand.class)))
                .thenReturn(Mono.just(eventResult()));
        List<String> output = new ArrayList<String>();
        WebSocketSession session = session(
                Flux.just(message(bindMessage()), message(signInMessage())), output);

        CompletableFuture<Void> handling = handler.handle(session).toFuture();
        verify(rpcClient).bind(any(LoginUser.class), any(Long.class), anyString());
        verify(rpcClient, never()).submit(any(), any());
        assertThat(bindResult.tryEmitValue(sessionView("CREATED", 0L)).isSuccess()).isTrue();
        handling.get(2, TimeUnit.SECONDS);

        assertThat(output).anyMatch(value -> value.contains("\"type\":\"STATE_SYNC\""));
        assertThat(output).anyMatch(value -> value.contains("\"type\":\"ACK\""));
        assertThat(output).anyMatch(value -> value.contains(
                "\"type\":\"PROGRESS_CONFIRMED\""));
        InOrder order = inOrder(rpcClient);
        order.verify(rpcClient).bind(any(LoginUser.class), any(Long.class), anyString());
        order.verify(rpcClient).submit(any(LoginUser.class), any(SubmitEventCommand.class));
    }

    @Test
    void shouldRejectLearningEventBeforeBinding() {
        List<String> output = new ArrayList<String>();
        WebSocketSession session = session(Flux.just(message(signInMessage())), output);

        handler.handle(session).block(Duration.ofSeconds(2));

        assertThat(output).singleElement().satisfies(value -> {
            assertThat(value).contains("\"type\":\"ERROR\"");
            assertThat(value).contains("请先绑定学习会话");
        });
        verify(rpcClient, never()).submit(any(), any());
    }

    @Test
    void shouldMapSequenceRpcErrorToResyncRequired() {
        when(rpcClient.bind(any(LoginUser.class), any(Long.class), anyString()))
                .thenReturn(Mono.just(sessionView("CREATED", 0L)));
        when(rpcClient.submit(any(LoginUser.class), any(SubmitEventCommand.class)))
                .thenReturn(Mono.error(new RealtimeRpcException(
                        AppErrorCode.LEARNING_EVENT_SEQUENCE_INVALID.getCode(),
                        AppErrorCode.LEARNING_EVENT_SEQUENCE_INVALID.getMessage())));
        List<String> output = new ArrayList<String>();
        WebSocketSession session = session(
                Flux.just(message(bindMessage()), message(signInMessage())), output);

        handler.handle(session).block(Duration.ofSeconds(2));

        assertThat(output).anyMatch(value -> value.contains("\"type\":\"ERROR\"")
                && value.contains("\"code\":\"L3005\"")
                && value.contains("\"retryable\":false")
                && value.contains("\"resyncRequired\":true"));
        assertThat(output).noneMatch(value -> value.contains("\"type\":\"ACK\""));
    }

    @Test
    void shouldReturnProtocolErrorForInvalidJson() {
        List<String> output = new ArrayList<String>();
        WebSocketSession session = session(Flux.just(message("not-json")), output);

        handler.handle(session).block(Duration.ofSeconds(2));

        assertThat(output).singleElement().satisfies(value -> {
            assertThat(value).contains("\"type\":\"ERROR\"");
            assertThat(value).contains("\"code\":\"S9001\"");
        });
        verify(rpcClient, never()).bind(any(), any(), any());
        verify(rpcClient, never()).submit(any(), any());
    }

    @Test
    void shouldCloseOversizedMessageWith1009() {
        WebSocketSession session = session(
                Flux.just(message("x".repeat(16_385))), new ArrayList<String>());

        handler.handle(session).block(Duration.ofSeconds(2));

        verify(session).close(CloseStatus.TOO_BIG_TO_PROCESS);
    }

    @Test
    void shouldCloseConnectionAfterHeartbeatTimeout() {
        RealtimeProperties timeoutProperties = new RealtimeProperties();
        timeoutProperties.setHeartbeatIntervalSeconds(1);
        timeoutProperties.setConnectionTimeoutSeconds(1);
        LearningWebSocketHandler timeoutHandler = new LearningWebSocketHandler(
                objectMapper,
                rpcClient,
                mock(RealtimeAuthorizationService.class),
                new LearningConnectionRegistry(),
                mock(RealtimeMetrics.class),
                timeoutProperties);
        WebSocketSession session = session(Flux.never(), new ArrayList<String>());

        timeoutHandler.handle(session).block(Duration.ofSeconds(3));

        verify(session).close(argThat(status -> status.getCode() == 4408));
    }

    @Test
    void shouldCloseConnectionWith4401WhenAccessTokenExpires() {
        WebSocketSession session = session(
                Flux.never(), new ArrayList<String>(), Instant.now().plusMillis(100));

        handler.handle(session).block(Duration.ofSeconds(2));

        verify(session).close(argThat(status -> status.getCode() == 4401));
    }

    @Test
    void shouldCloseConnectionWith4403WhenAuthorizationIsRevoked() {
        RealtimeAuthorizationService authorizationService = mock(
                RealtimeAuthorizationService.class);
        when(authorizationService.authenticate(any())).thenReturn(Mono.error(
                new RealtimeAuthException(AppErrorCode.FORBIDDEN)));
        RealtimeProperties recheckProperties = new RealtimeProperties();
        recheckProperties.setAuthRecheckSeconds(1);
        LearningWebSocketHandler recheckHandler = new LearningWebSocketHandler(
                objectMapper,
                rpcClient,
                authorizationService,
                new LearningConnectionRegistry(),
                mock(RealtimeMetrics.class),
                recheckProperties);
        WebSocketSession session = session(Flux.never(), new ArrayList<String>());

        recheckHandler.handle(session).block(Duration.ofSeconds(3));

        verify(session).close(argThat(status -> status.getCode() == 4403));
    }

    @Test
    void shouldCloseWithRetryableCodeWhenAuthorizationInfrastructureFails() {
        RealtimeAuthorizationService authorizationService = mock(
                RealtimeAuthorizationService.class);
        when(authorizationService.authenticate(any())).thenReturn(Mono.error(
                new IllegalStateException("Redis暂时不可用")));
        RealtimeProperties recheckProperties = new RealtimeProperties();
        recheckProperties.setAuthRecheckSeconds(1);
        LearningWebSocketHandler recheckHandler = new LearningWebSocketHandler(
                objectMapper,
                rpcClient,
                authorizationService,
                new LearningConnectionRegistry(),
                mock(RealtimeMetrics.class),
                recheckProperties);
        WebSocketSession session = session(Flux.never(), new ArrayList<String>());

        recheckHandler.handle(session).block(Duration.ofSeconds(3));

        verify(session).close(argThat(status -> status.getCode() == 1011));
    }

    @Test
    void shouldCloseDirectConnectionWithoutTrustedPrincipal() {
        WebSocketSession session = mock(WebSocketSession.class);
        HandshakeInfo handshake = new HandshakeInfo(
                URI.create("ws://localhost/ws/learning"), HttpHeaders.EMPTY,
                Mono.empty(), null);
        when(session.getHandshakeInfo()).thenReturn(handshake);
        when(session.close(any(CloseStatus.class))).thenReturn(Mono.empty());

        handler.handle(session).block(Duration.ofSeconds(1));

        verify(session).close(argThat(status -> status.getCode() == 4401));
        verify(session, never()).receive();
    }

    private WebSocketSession session(
            Flux<WebSocketMessage> inbound,
            List<String> output) {
        return session(inbound, output, Instant.now().plusSeconds(300));
    }

    @SuppressWarnings("unchecked")
    private WebSocketSession session(
            Flux<WebSocketMessage> inbound,
            List<String> output,
            Instant expiresAt) {
        WebSocketSession session = mock(WebSocketSession.class);
        RealtimePrincipal principal = new RealtimePrincipal(
                new AccessTokenClaims(
                        10L, 20L, "student", "login-session", 3L,
                        expiresAt),
                student(), "trace-id");
        HandshakeInfo handshake = new HandshakeInfo(
                URI.create("ws://localhost/ws/learning"), HttpHeaders.EMPTY,
                Mono.just(principal), null);
        when(session.getId()).thenReturn("connection-one");
        when(session.getHandshakeInfo()).thenReturn(handshake);
        when(session.receive()).thenReturn(inbound);
        when(session.textMessage(anyString())).thenAnswer(invocation ->
                message(invocation.getArgument(0, String.class)));
        when(session.send(any(Publisher.class))).thenAnswer(invocation ->
                Flux.from((Publisher<WebSocketMessage>) invocation.getArgument(0))
                        .doOnNext(message -> output.add(message.getPayloadAsText()))
                        .then());
        when(session.close(any(CloseStatus.class))).thenReturn(Mono.empty());
        return session;
    }

    private WebSocketMessage message(String text) {
        return new WebSocketMessage(
                WebSocketMessage.Type.TEXT,
                DefaultDataBufferFactory.sharedInstance.wrap(
                        text.getBytes(StandardCharsets.UTF_8)));
    }

    private String bindMessage() {
        return """
                {"type":"BIND_SESSION","requestId":"bind-1","studySessionId":"900",
                 "sentAt":"2026-08-30T08:00:00Z",
                 "payload":{"clientInstanceId":"browser-one","reconnecting":false}}
                """;
    }

    private String signInMessage() {
        return """
                {"type":"SIGN_IN","requestId":"event-1","studySessionId":"900","seq":1,
                 "sentAt":"2026-08-30T08:00:01Z",
                 "payload":{"videoPositionMillis":0}}
                """;
    }

    private LearningSessionView sessionView(String status, long sequence) {
        return new LearningSessionView(
                900L, 500L, 100L, 101L, "安全驾驶", status,
                null, sequence, 0L, 0L, 60_000L, null, LocalDateTime.now(), null);
    }

    private LearningEventResultView eventResult() {
        return new LearningEventResultView(
                900L, "event-1", 1L, "SIGNED_IN", null,
                0L, 0L, 0L, 60_000L, false, false, LocalDateTime.now());
    }

    private LoginUser student() {
        LoginUser user = new LoginUser();
        user.setUserId(10L);
        user.setEnterpriseId(20L);
        user.setUsername("student");
        user.setPermissions(Collections.singletonList("student:learning:study"));
        return user;
    }
}
