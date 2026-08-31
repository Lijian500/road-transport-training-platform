package me.lj.train.realtime.connection;

import me.lj.train.common.security.jwt.AccessTokenClaims;
import me.lj.train.common.security.model.LoginUser;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.concurrent.atomic.AtomicLong;

/** 单条实时学习连接的身份、绑定和发送状态。 */
public class LearningConnection {

    private final String connectionId;
    private final AccessTokenClaims claims;
    private final String traceId;
    private final Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();
    private final Sinks.One<Boolean> replacement = Sinks.one();
    private final AtomicLong lastHeartbeatMillis = new AtomicLong(System.currentTimeMillis());

    private volatile LoginUser loginUser;
    private volatile Long studySessionId;
    private volatile String clientInstanceId;

    public LearningConnection(
            String connectionId,
            AccessTokenClaims claims,
            LoginUser loginUser,
            String traceId) {
        this.connectionId = connectionId;
        this.claims = claims;
        this.loginUser = loginUser;
        this.traceId = traceId;
    }

    public synchronized boolean emit(String message) {
        return outbound.tryEmitNext(message).isSuccess();
    }

    public void complete() {
        outbound.tryEmitComplete();
    }

    public Flux<String> outbound() {
        return outbound.asFlux();
    }

    public void requestReplacement() {
        replacement.tryEmitValue(true);
    }

    public Mono<Boolean> replacement() {
        return replacement.asMono();
    }

    public void bind(Long sessionId, String clientInstanceId) {
        this.studySessionId = sessionId;
        this.clientInstanceId = clientInstanceId;
    }

    public boolean isBound() {
        return studySessionId != null && clientInstanceId != null;
    }

    public void markHeartbeat() {
        lastHeartbeatMillis.set(System.currentTimeMillis());
    }

    public long millisSinceHeartbeat() {
        return System.currentTimeMillis() - lastHeartbeatMillis.get();
    }

    public String getConnectionId() {
        return connectionId;
    }

    public AccessTokenClaims getClaims() {
        return claims;
    }

    public LoginUser getLoginUser() {
        return loginUser;
    }

    public void setLoginUser(LoginUser loginUser) {
        this.loginUser = loginUser;
    }

    public Long getStudySessionId() {
        return studySessionId;
    }

    public String getClientInstanceId() {
        return clientInstanceId;
    }

    public String getTraceId() {
        return traceId;
    }
}
