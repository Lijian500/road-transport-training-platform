package me.lj.train.realtime.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.security.SecurityConstants;
import me.lj.train.common.security.jwt.AccessTokenClaims;
import me.lj.train.common.security.jwt.JwtTokenService;
import me.lj.train.realtime.config.RealtimeProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** WebSocket握手的同源、Token、登录版本和学习权限校验。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RealtimeAuthenticationWebFilter implements WebFilter {

    private static final String LEARNING_PATH = "/ws/learning";

    private final JwtTokenService jwtTokenService;
    private final RealtimeAuthorizationService authorizationService;
    private final ObjectMapper objectMapper;
    private final Set<String> allowedOrigins;

    public RealtimeAuthenticationWebFilter(
            JwtTokenService jwtTokenService,
            RealtimeAuthorizationService authorizationService,
            ObjectMapper objectMapper,
            RealtimeProperties properties) {
        this.jwtTokenService = jwtTokenService;
        this.authorizationService = authorizationService;
        this.objectMapper = objectMapper;
        this.allowedOrigins = properties.getAllowedOrigins().stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!LEARNING_PATH.equals(exchange.getRequest().getPath().value())) {
            return chain.filter(exchange);
        }
        String origin = exchange.getRequest().getHeaders().getOrigin();
        if (origin == null || !allowedOrigins.contains(origin)) {
            return writeError(exchange, AppErrorCode.FORBIDDEN);
        }
        HttpCookie cookie = exchange.getRequest().getCookies()
                .getFirst(SecurityConstants.ACCESS_TOKEN_COOKIE);
        if (cookie == null || cookie.getValue().isEmpty()) {
            return writeError(exchange, AppErrorCode.UNAUTHORIZED);
        }
        AccessTokenClaims claims;
        try {
            claims = jwtTokenService.decode(cookie.getValue());
        } catch (RuntimeException exception) {
            return writeError(exchange, AppErrorCode.TOKEN_EXPIRED);
        }
        String traceId = resolveTraceId(exchange);
        return authorizationService.authenticate(claims)
                .flatMap(user -> chain.filter(exchange.mutate()
                        .principal(Mono.just(new RealtimePrincipal(claims, user, traceId)))
                        .build()))
                .onErrorResume(RealtimeAuthException.class,
                        exception -> writeError(exchange, exception.getErrorCode()))
                .onErrorResume(exception -> writeError(exchange, AppErrorCode.SYSTEM_ERROR));
    }

    private String resolveTraceId(ServerWebExchange exchange) {
        String traceId = exchange.getRequest().getHeaders()
                .getFirst(SecurityConstants.HEADER_TRACE_ID);
        if (traceId != null && traceId.matches("[a-fA-F0-9]{32}")) {
            return traceId;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private Mono<Void> writeError(ServerWebExchange exchange, AppErrorCode errorCode) {
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(Result.failed(errorCode));
        } catch (JsonProcessingException exception) {
            bytes = ("{\"code\":\"" + errorCode.getCode() + "\",\"message\":\""
                    + errorCode.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponse().setStatusCode(HttpStatus.valueOf(errorCode.getHttpStatus()));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
