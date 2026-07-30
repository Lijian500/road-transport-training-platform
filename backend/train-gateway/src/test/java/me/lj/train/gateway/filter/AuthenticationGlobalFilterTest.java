package me.lj.train.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.lj.train.common.security.SecurityConstants;
import me.lj.train.common.security.jwt.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AuthenticationGlobalFilterTest {

    private final AuthenticationGlobalFilter filter = new AuthenticationGlobalFilter(
            mock(JwtTokenService.class),
            mock(ReactiveStringRedisTemplate.class),
            new ObjectMapper());

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
}
