package me.lj.train.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.security.SecurityConstants;
import me.lj.train.common.security.jwt.AccessTokenClaims;
import me.lj.train.common.security.jwt.JwtTokenService;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;

/**
 * Gateway统一JWT校验、登录版本校验及可信用户头写入。
 */
@Component
public class AuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/csrf",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/logout");

    private final JwtTokenService jwtTokenService;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public AuthenticationGlobalFilter(
            JwtTokenService jwtTokenService,
            ReactiveStringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.jwtTokenService = jwtTokenService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        ServerWebExchange sanitizedExchange = exchange.mutate()
                .request(stripTrustedHeaders(exchange.getRequest(), traceId))
                .build();
        String path = exchange.getRequest().getURI().getPath();
        if (isPublic(path)) {
            return chain.filter(sanitizedExchange);
        }

        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(SecurityConstants.ACCESS_TOKEN_COOKIE);
        if (cookie == null || cookie.getValue().isEmpty()) {
            return writeError(exchange, AppErrorCode.UNAUTHORIZED);
        }
        AccessTokenClaims claims;
        try {
            claims = jwtTokenService.decode(cookie.getValue());
        } catch (RuntimeException exception) {
            return writeError(exchange, AppErrorCode.TOKEN_EXPIRED);
        }
        String key = SecurityConstants.REDIS_LOGIN_VERSION_PREFIX + claims.getUserId();
        return redisTemplate.opsForValue().get(key)
                .defaultIfEmpty("")
                .flatMap(version -> {
                    if (!String.valueOf(claims.getLoginVersion()).equals(version)) {
                        return writeError(exchange, AppErrorCode.TOKEN_EXPIRED);
                    }
                    ServerHttpRequest request = sanitizedExchange.getRequest().mutate()
                            .header(SecurityConstants.HEADER_USER_ID, String.valueOf(claims.getUserId()))
                            .header(
                                    SecurityConstants.HEADER_ENTERPRISE_ID,
                                    claims.getEnterpriseId() == null
                                            ? ""
                                            : String.valueOf(claims.getEnterpriseId()))
                            .header(SecurityConstants.HEADER_SESSION_ID, claims.getSessionId())
                            .header(
                                    SecurityConstants.HEADER_LOGIN_VERSION,
                                    String.valueOf(claims.getLoginVersion()))
                            .build();
                    return chain.filter(sanitizedExchange.mutate().request(request).build());
                });
    }

    private ServerHttpRequest stripTrustedHeaders(ServerHttpRequest request, String traceId) {
        return request.mutate().headers(headers -> {
            headers.remove(SecurityConstants.HEADER_USER_ID);
            headers.remove(SecurityConstants.HEADER_ENTERPRISE_ID);
            headers.remove(SecurityConstants.HEADER_SESSION_ID);
            headers.remove(SecurityConstants.HEADER_LOGIN_VERSION);
            headers.remove(SecurityConstants.HEADER_PLATFORM_ADMIN);
            headers.remove(SecurityConstants.HEADER_MUST_CHANGE_PASSWORD);
            headers.remove(SecurityConstants.HEADER_PERMISSIONS);
            headers.remove(SecurityConstants.HEADER_TRACE_ID);
            headers.set(SecurityConstants.HEADER_TRACE_ID, traceId);
        }).build();
    }

    private boolean isPublic(String path) {
        return PUBLIC_PATHS.contains(path)
                || path.startsWith("/actuator/")
                || (!path.startsWith("/api/") && !path.startsWith("/ws/"));
    }

    private Mono<Void> writeError(ServerWebExchange exchange, AppErrorCode errorCode) {
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(Result.failed(errorCode));
        } catch (JsonProcessingException exception) {
            bytes = ("{\"code\":\"" + errorCode.getCode() + "\",\"message\":\""
                    + errorCode.getMessage() + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        exchange.getResponse().setStatusCode(HttpStatus.valueOf(errorCode.getHttpStatus()));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
