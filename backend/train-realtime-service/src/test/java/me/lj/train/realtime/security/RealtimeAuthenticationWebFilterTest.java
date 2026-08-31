package me.lj.train.realtime.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.lj.train.common.security.SecurityConstants;
import me.lj.train.common.security.jwt.AccessTokenClaims;
import me.lj.train.common.security.jwt.JwtTokenService;
import me.lj.train.common.security.model.LoginUser;
import me.lj.train.realtime.config.RealtimeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RealtimeAuthenticationWebFilterTest {

    private JwtTokenService jwtTokenService;
    private RealtimeAuthorizationService authorizationService;
    private RealtimeAuthenticationWebFilter filter;

    @BeforeEach
    void setUp() {
        jwtTokenService = mock(JwtTokenService.class);
        authorizationService = mock(RealtimeAuthorizationService.class);
        filter = new RealtimeAuthenticationWebFilter(
                jwtTokenService, authorizationService, new ObjectMapper(),
                new RealtimeProperties());
    }

    @Test
    void shouldRejectOriginOutsideExactAllowlist() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws/learning")
                        .header("Origin", "http://localhost:5173.example.com")
                        .build());

        filter.filter(exchange, mock(WebFilterChain.class)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldRejectHandshakeWithoutAccessCookie() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws/learning")
                        .header("Origin", "http://localhost:5173")
                        .build());

        filter.filter(exchange, mock(WebFilterChain.class)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldForwardAuthenticatedPrincipal() {
        AccessTokenClaims claims = claims();
        LoginUser user = student();
        when(jwtTokenService.decode("access-token")).thenReturn(claims);
        when(authorizationService.authenticate(claims)).thenReturn(Mono.just(user));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws/learning")
                        .header("Origin", "http://localhost:5173")
                        .cookie(new HttpCookie(
                                SecurityConstants.ACCESS_TOKEN_COOKIE, "access-token"))
                        .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<ServerWebExchange>();
        WebFilterChain chain = current -> {
            forwarded.set(current);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getPrincipal().block())
                .isInstanceOf(RealtimePrincipal.class);
    }

    @Test
    void shouldRejectInvalidJwtCookie() {
        when(jwtTokenService.decode("invalid-token"))
                .thenThrow(new IllegalArgumentException("JWT无效"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws/learning")
                        .header("Origin", "http://localhost:5173")
                        .cookie(new HttpCookie(
                                SecurityConstants.ACCESS_TOKEN_COOKIE, "invalid-token"))
                        .build());

        filter.filter(exchange, mock(WebFilterChain.class)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private AccessTokenClaims claims() {
        return new AccessTokenClaims(
                10L, 20L, "student", "session-1", 3L,
                Instant.now().plusSeconds(300));
    }

    private LoginUser student() {
        LoginUser user = new LoginUser();
        user.setUserId(10L);
        user.setEnterpriseId(20L);
        user.setUsername("student");
        user.setPermissions(java.util.Collections.singletonList(
                "student:learning:study"));
        return user;
    }
}
