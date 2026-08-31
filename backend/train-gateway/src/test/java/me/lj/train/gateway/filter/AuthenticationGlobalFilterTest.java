package me.lj.train.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.lj.train.common.security.SecurityConstants;
import me.lj.train.common.security.jwt.AccessTokenClaims;
import me.lj.train.common.security.jwt.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticationGlobalFilterTest {

    private JwtTokenService jwtTokenService;
    private ReactiveValueOperations<String, String> values;
    private AuthenticationGlobalFilter filter;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        jwtTokenService = mock(JwtTokenService.class);
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        values = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        filter = new AuthenticationGlobalFilter(
                jwtTokenService, redisTemplate, new ObjectMapper());
    }

    @Test
    void shouldStripForgedTrustedHeadersOnPublicRequest() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login")
                        .header(SecurityConstants.HEADER_USER_ID, "999")
                        .header(SecurityConstants.HEADER_PERMISSIONS, "*")
                        .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<ServerWebExchange>();
        GatewayFilterChain chain = current -> {
            forwarded.set(current);
            return reactor.core.publisher.Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders()
                .getFirst(SecurityConstants.HEADER_USER_ID)).isNull();
        assertThat(forwarded.get().getRequest().getHeaders()
                .getFirst(SecurityConstants.HEADER_PERMISSIONS)).isNull();
        assertThat(forwarded.get().getRequest().getHeaders()
                .getFirst(SecurityConstants.HEADER_TRACE_ID)).isNotBlank();
    }

    @Test
    void shouldRejectProtectedRequestWithoutAccessCookie() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/users").build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldRejectLearningWebSocketWithoutAccessCookie() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws/learning").build());

        filter.filter(exchange, mock(GatewayFilterChain.class)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldForwardValidLearningWebSocketWithTrustedHeaders() {
        AccessTokenClaims claims = new AccessTokenClaims(
                10L, 20L, "student", "session-one", 3L,
                Instant.now().plusSeconds(300));
        when(jwtTokenService.decode("access-token")).thenReturn(claims);
        when(values.get(SecurityConstants.REDIS_LOGIN_VERSION_PREFIX + 10L))
                .thenReturn(reactor.core.publisher.Mono.just("3"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws/learning")
                        .cookie(new HttpCookie(
                                SecurityConstants.ACCESS_TOKEN_COOKIE, "access-token"))
                        .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<ServerWebExchange>();
        GatewayFilterChain chain = current -> {
            forwarded.set(current);
            return reactor.core.publisher.Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders()
                .getFirst(SecurityConstants.HEADER_USER_ID)).isEqualTo("10");
        assertThat(forwarded.get().getRequest().getHeaders()
                .getFirst(SecurityConstants.HEADER_ENTERPRISE_ID)).isEqualTo("20");
    }
}
