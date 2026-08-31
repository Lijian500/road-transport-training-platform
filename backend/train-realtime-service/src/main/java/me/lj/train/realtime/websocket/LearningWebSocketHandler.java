package me.lj.train.realtime.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.lj.train.api.learning.LearningModels.LearningEventResultView;
import me.lj.train.api.learning.LearningModels.LearningSessionView;
import me.lj.train.api.learning.LearningModels.SubmitEventCommand;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.realtime.config.RealtimeProperties;
import me.lj.train.realtime.connection.LearningConnection;
import me.lj.train.realtime.connection.LearningConnectionRegistry;
import me.lj.train.realtime.metrics.RealtimeMetrics;
import me.lj.train.realtime.protocol.RealtimeMessages.AckPayload;
import me.lj.train.realtime.protocol.RealtimeMessages.BindPayload;
import me.lj.train.realtime.protocol.RealtimeMessages.ClientEnvelope;
import me.lj.train.realtime.protocol.RealtimeMessages.ErrorPayload;
import me.lj.train.realtime.protocol.RealtimeMessages.LearningPayload;
import me.lj.train.realtime.protocol.RealtimeMessages.PongPayload;
import me.lj.train.realtime.protocol.RealtimeMessages.ServerEnvelope;
import me.lj.train.realtime.protocol.RealtimeMessages.SessionReplacedPayload;
import me.lj.train.realtime.protocol.RealtimeProtocolException;
import me.lj.train.realtime.rpc.LearningRpcClient;
import me.lj.train.realtime.rpc.RealtimeRpcException;
import me.lj.train.realtime.security.RealtimeAuthException;
import me.lj.train.realtime.security.RealtimeAuthorizationService;
import me.lj.train.realtime.security.RealtimePrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** 实时学习协议解析、连接控制和学习RPC转发入口。 */
@Component
public class LearningWebSocketHandler implements WebSocketHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LearningWebSocketHandler.class);
    private static final Set<String> MESSAGE_TYPES = new HashSet<String>(Arrays.asList(
            "BIND_SESSION", "HEARTBEAT", "SYNC_STATE",
            "SIGN_IN", "PLAY", "PROGRESS", "PAUSE", "SIGN_OUT"));
    private static final Set<String> LEARNING_TYPES = new HashSet<String>(Arrays.asList(
            "SIGN_IN", "PLAY", "PROGRESS", "PAUSE", "SIGN_OUT"));

    private static final CloseStatus TOKEN_EXPIRED = new CloseStatus(4401, "登录状态已过期");
    private static final CloseStatus AUTH_REVOKED = new CloseStatus(4403, "学习权限已失效");
    private static final CloseStatus AUTH_SERVICE_UNAVAILABLE =
            CloseStatus.SERVER_ERROR.withReason("授权服务暂时不可用");
    private static final CloseStatus HEARTBEAT_TIMEOUT = new CloseStatus(4408, "心跳超时");
    private static final CloseStatus SESSION_REPLACED = new CloseStatus(4409, "连接已被接管");

    private final ObjectMapper objectMapper;
    private final LearningRpcClient rpcClient;
    private final RealtimeAuthorizationService authorizationService;
    private final LearningConnectionRegistry connectionRegistry;
    private final RealtimeMetrics metrics;
    private final RealtimeProperties properties;

    public LearningWebSocketHandler(
            ObjectMapper objectMapper,
            LearningRpcClient rpcClient,
            RealtimeAuthorizationService authorizationService,
            LearningConnectionRegistry connectionRegistry,
            RealtimeMetrics metrics,
            RealtimeProperties properties) {
        this.objectMapper = objectMapper;
        this.rpcClient = rpcClient;
        this.authorizationService = authorizationService;
        this.connectionRegistry = connectionRegistry;
        this.metrics = metrics;
        this.properties = properties;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        return session.getHandshakeInfo().getPrincipal()
                .ofType(RealtimePrincipal.class)
                .flatMap(principal -> handleAuthorized(session, principal).thenReturn(true))
                .switchIfEmpty(Mono.defer(() -> session.close(TOKEN_EXPIRED).thenReturn(false)))
                .then();
    }

    private Mono<Void> handleAuthorized(
            WebSocketSession session,
            RealtimePrincipal principal) {
        LearningConnection connection = new LearningConnection(
                session.getId(), principal.getClaims(), principal.getLoginUser(),
                principal.getTraceId());
        metrics.connectionOpened();

        Mono<Void> inbound = session.receive()
                .concatMap(message -> processMessage(session, connection, message))
                .then();
        Mono<Void> heartbeat = heartbeatMonitor(session, connection);
        Mono<Void> expiration = tokenExpirationMonitor(session, connection);
        Mono<Void> authorization = authorizationMonitor(session, connection);
        Mono<Void> replacement = replacementMonitor(session, connection);
        Mono<Void> controller = Mono.firstWithSignal(
                        inbound, heartbeat, expiration, authorization, replacement)
                .doFinally(signal -> connection.complete());
        Mono<Void> sender = session.send(connection.outbound().map(session::textMessage));

        return Mono.when(sender, controller).doFinally(signal -> {
            connection.complete();
            connectionRegistry.remove(connection);
            metrics.connectionClosed();
        });
    }

    private Mono<Void> processMessage(
            WebSocketSession session,
            LearningConnection connection,
            WebSocketMessage message) {
        if (message.getType() != WebSocketMessage.Type.TEXT) {
            return protocolError(connection, null, null, "仅支持文本消息");
        }
        if (message.getPayload().readableByteCount() > properties.getMaxMessageBytes()) {
            metrics.protocolError();
            return session.close(CloseStatus.TOO_BIG_TO_PROCESS);
        }
        ClientEnvelope envelope;
        try {
            envelope = objectMapper.readValue(message.getPayloadAsText(), ClientEnvelope.class);
            validateEnvelope(envelope);
        } catch (JsonProcessingException | RealtimeProtocolException exception) {
            return protocolError(connection, null, null, "消息格式不正确");
        }
        metrics.message(envelope.type());
        Mono<Void> action = Mono.defer(() -> {
            if ("BIND_SESSION".equals(envelope.type())) {
                return bind(connection, envelope);
            }
            if ("SYNC_STATE".equals(envelope.type())) {
                return sync(connection, envelope);
            }
            if ("HEARTBEAT".equals(envelope.type())) {
                return heartbeat(connection, envelope);
            }
            return submitLearningEvent(connection, envelope);
        });
        return action
                .onErrorResume(RealtimeRpcException.class,
                        exception -> rpcError(connection, envelope, exception))
                .onErrorResume(RealtimeProtocolException.class,
                        exception -> protocolError(
                                connection, envelope.requestId(), envelope.seq(),
                                exception.getMessage()))
                .onErrorResume(exception -> {
                    logError(connection, AppErrorCode.SYSTEM_ERROR.getCode());
                    return sendError(
                            connection, envelope.requestId(), envelope.seq(),
                            AppErrorCode.SYSTEM_ERROR.getCode(),
                            AppErrorCode.SYSTEM_ERROR.getMessage(), true, false);
                });
    }

    private Mono<Void> bind(LearningConnection connection, ClientEnvelope envelope) {
        Long sessionId = parseId(envelope.studySessionId(), "学习会话ID");
        BindPayload payload = convertPayload(envelope.payload(), BindPayload.class);
        if (payload.clientInstanceId() == null || payload.clientInstanceId().isBlank()) {
            throw new RealtimeProtocolException("客户端实例ID不能为空");
        }
        return rpcClient.bind(
                        connection.getLoginUser(), sessionId, payload.clientInstanceId())
                .doOnNext(view -> {
                    connection.bind(sessionId, payload.clientInstanceId());
                    LearningConnection previous = connectionRegistry.register(connection);
                    if (previous != null && previous != connection) {
                        metrics.replacement();
                    }
                    if (payload.reconnecting()) {
                        metrics.reconnect();
                    }
                    emit(connection, envelope(
                            "STATE_SYNC", envelope.requestId(), sessionId,
                            view.lastSequence(), view));
                })
                .then();
    }

    private Mono<Void> sync(LearningConnection connection, ClientEnvelope envelope) {
        requireBoundSession(connection, envelope.studySessionId());
        return rpcClient.bind(
                        connection.getLoginUser(), connection.getStudySessionId(),
                        connection.getClientInstanceId())
                .doOnNext(view -> emit(connection, envelope(
                        "STATE_SYNC", envelope.requestId(), connection.getStudySessionId(),
                        view.lastSequence(), view)))
                .then();
    }

    private Mono<Void> heartbeat(
            LearningConnection connection,
            ClientEnvelope envelope) {
        connection.markHeartbeat();
        emit(connection, envelope(
                "PONG", envelope.requestId(), connection.getStudySessionId(),
                null, new PongPayload(Instant.now())));
        return Mono.empty();
    }

    private Mono<Void> submitLearningEvent(
            LearningConnection connection,
            ClientEnvelope envelope) {
        requireBoundSession(connection, envelope.studySessionId());
        if (!LEARNING_TYPES.contains(envelope.type())
                || envelope.seq() == null || envelope.seq().longValue() <= 0) {
            throw new RealtimeProtocolException("学习事件序号不正确");
        }
        LearningPayload payload = convertPayload(envelope.payload(), LearningPayload.class);
        Long coursewareId = payload.coursewareSnapshotId() == null
                || payload.coursewareSnapshotId().isBlank()
                ? null : parseId(payload.coursewareSnapshotId(), "课件ID");
        SubmitEventCommand command = new SubmitEventCommand(
                connection.getStudySessionId(), connection.getClientInstanceId(),
                envelope.requestId(), envelope.seq(), envelope.type(), coursewareId,
                payload.videoPositionMillis());
        return rpcClient.submit(connection.getLoginUser(), command)
                .doOnNext(result -> emitLearningResult(connection, envelope, result))
                .then();
    }

    private void emitLearningResult(
            LearningConnection connection,
            ClientEnvelope request,
            LearningEventResultView result) {
        emit(connection, envelope(
                "ACK", request.requestId(), result.sessionId(), result.acceptedSequence(),
                new AckPayload(result.acceptedSequence(), result.status())));
        emit(connection, envelope(
                "PROGRESS_CONFIRMED", request.requestId(), result.sessionId(),
                result.acceptedSequence(), result));
    }

    private Mono<Void> rpcError(
            LearningConnection connection,
            ClientEnvelope envelope,
            RealtimeRpcException exception) {
        boolean resync = AppErrorCode.LEARNING_EVENT_SEQUENCE_INVALID.getCode()
                .equals(exception.getCode());
        boolean retryable = AppErrorCode.SYSTEM_ERROR.getCode().equals(exception.getCode())
                || AppErrorCode.LEARNING_PLAYBACK_UNAVAILABLE.getCode()
                .equals(exception.getCode());
        logError(connection, exception.getCode());
        return sendError(
                connection, envelope.requestId(), envelope.seq(), exception.getCode(),
                exception.getMessage(), retryable, resync);
    }

    private Mono<Void> protocolError(
            LearningConnection connection,
            String requestId,
            Long sequence,
            String message) {
        metrics.protocolError();
        logError(connection, AppErrorCode.PARAM_INVALID.getCode());
        return sendError(
                connection, requestId, sequence, AppErrorCode.PARAM_INVALID.getCode(),
                message, false, false);
    }

    private Mono<Void> sendError(
            LearningConnection connection,
            String requestId,
            Long sequence,
            String code,
            String message,
            boolean retryable,
            boolean resyncRequired) {
        emit(connection, envelope(
                "ERROR", requestId == null ? serverRequestId() : requestId,
                connection.getStudySessionId(), sequence,
                new ErrorPayload(code, message, retryable, resyncRequired)));
        return Mono.empty();
    }

    private Mono<Void> heartbeatMonitor(
            WebSocketSession session,
            LearningConnection connection) {
        Duration checkInterval = Duration.ofSeconds(
                Math.max(1, properties.getHeartbeatIntervalSeconds()));
        long timeoutMillis = Duration.ofSeconds(
                Math.max(1, properties.getConnectionTimeoutSeconds())).toMillis();
        return Flux.interval(checkInterval)
                .filter(tick -> connection.millisSinceHeartbeat() >= timeoutMillis)
                .next()
                .doOnNext(tick -> metrics.heartbeatTimeout())
                .flatMap(tick -> session.close(HEARTBEAT_TIMEOUT));
    }

    private Mono<Void> tokenExpirationMonitor(
            WebSocketSession session,
            LearningConnection connection) {
        Duration delay = Duration.between(Instant.now(), connection.getClaims().getExpiresAt());
        if (delay.isNegative()) {
            delay = Duration.ZERO;
        }
        return Mono.delay(delay).flatMap(tick -> session.close(TOKEN_EXPIRED));
    }

    private Mono<Void> authorizationMonitor(
            WebSocketSession session,
            LearningConnection connection) {
        Duration interval = Duration.ofSeconds(Math.max(1, properties.getAuthRecheckSeconds()));
        return Flux.interval(interval)
                .concatMap(tick -> authorizationService.authenticate(connection.getClaims())
                        .doOnNext(connection::setLoginUser))
                .then()
                .onErrorResume(exception -> session.close(resolveAuthCloseStatus(exception)));
    }

    private Mono<Void> replacementMonitor(
            WebSocketSession session,
            LearningConnection connection) {
        return connection.replacement().flatMap(replaced -> {
            emit(connection, envelope(
                    "SESSION_REPLACED", serverRequestId(), connection.getStudySessionId(),
                    null, new SessionReplacedPayload("学习连接已被其他页面接管")));
            return Mono.delay(Duration.ofMillis(50))
                    .then(session.close(SESSION_REPLACED));
        });
    }

    private CloseStatus resolveAuthCloseStatus(Throwable exception) {
        if (!(exception instanceof RealtimeAuthException authException)) {
            return AUTH_SERVICE_UNAVAILABLE;
        }
        AppErrorCode errorCode = authException.getErrorCode();
        if (AppErrorCode.TOKEN_EXPIRED.equals(errorCode)
                || AppErrorCode.UNAUTHORIZED.equals(errorCode)
                || AppErrorCode.REFRESH_TOKEN_INVALID.equals(errorCode)) {
            return TOKEN_EXPIRED;
        }
        if (AppErrorCode.ACCOUNT_DISABLED.equals(errorCode)
                || AppErrorCode.FORBIDDEN.equals(errorCode)
                || AppErrorCode.PASSWORD_CHANGE_REQUIRED.equals(errorCode)) {
            return AUTH_REVOKED;
        }
        return AUTH_SERVICE_UNAVAILABLE;
    }

    private void validateEnvelope(ClientEnvelope envelope) {
        if (envelope == null || !MESSAGE_TYPES.contains(envelope.type())
                || envelope.requestId() == null || envelope.requestId().isBlank()
                || envelope.requestId().length() > 64 || envelope.sentAt() == null) {
            throw new RealtimeProtocolException("消息信封不正确");
        }
    }

    private void requireBoundSession(
            LearningConnection connection,
            String requestedSessionId) {
        if (!connection.isBound()) {
            throw new RealtimeProtocolException("请先绑定学习会话");
        }
        Long sessionId = parseId(requestedSessionId, "学习会话ID");
        if (!connection.getStudySessionId().equals(sessionId)) {
            throw new RealtimeProtocolException("学习会话与当前连接不一致");
        }
    }

    private Long parseId(String value, String name) {
        try {
            if (value == null || value.isBlank()) {
                throw new NumberFormatException();
            }
            Long parsed = Long.valueOf(value);
            if (parsed.longValue() <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new RealtimeProtocolException(name + "不正确");
        }
    }

    private <T> T convertPayload(JsonNode payload, Class<T> targetType) {
        if (payload == null || payload.isNull()) {
            throw new RealtimeProtocolException("消息载荷不能为空");
        }
        try {
            return objectMapper.treeToValue(payload, targetType);
        } catch (JsonProcessingException exception) {
            throw new RealtimeProtocolException("消息载荷不正确");
        }
    }

    private ServerEnvelope envelope(
            String type,
            String requestId,
            Long sessionId,
            Long sequence,
            Object payload) {
        return new ServerEnvelope(
                type, requestId,
                sessionId == null ? null : String.valueOf(sessionId),
                sequence, Instant.now(), payload);
    }

    private void emit(LearningConnection connection, ServerEnvelope envelope) {
        try {
            if (!connection.emit(objectMapper.writeValueAsString(envelope))) {
                throw new IllegalStateException("实时响应队列已关闭");
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("实时响应序列化失败", exception);
        }
    }

    private void logError(LearningConnection connection, String code) {
        LOGGER.warn(
                "实时学习处理失败 traceId={} userId={} sessionId={} code={}",
                connection.getTraceId(), connection.getLoginUser().getUserId(),
                connection.getStudySessionId(), code);
    }

    private String serverRequestId() {
        return "server-" + UUID.randomUUID();
    }
}
