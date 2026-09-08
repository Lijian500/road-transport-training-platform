package me.lj.train.realtime.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.lj.train.common.security.SecurityConstants;
import me.lj.train.common.security.jwt.AccessTokenClaims;
import me.lj.train.common.security.jwt.JwtTokenService;
import me.lj.train.common.security.model.LoginUser;
import me.lj.train.realtime.config.RealtimeConfiguration;
import me.lj.train.realtime.config.RealtimeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 通过真实Spring Security过滤链检查握手主体，覆盖单独测试过滤器时遗漏的顺序问题。 */
class RealtimeSecurityChainTest {
    @Test
    void shouldPreservePrincipalAfterSpringSecurityWrapsExchange() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            WebTestClient.bindToApplicationContext(context).build()
                    .get().uri("/ws/learning")
                    .header("Origin", "http://localhost:5173")
                    .cookie(SecurityConstants.ACCESS_TOKEN_COOKIE, "test-access")
                    .exchange().expectStatus().isOk()
                    .expectBody(String.class).isEqualTo("RealtimePrincipal");
        }
    }

    @Configuration
    @EnableWebFlux
    @EnableWebFluxSecurity
    static class TestConfiguration {
        /** 使用生产的安全链配置，保留其exchange包装行为。 */
        @Bean SecurityWebFilterChain securityChain(ServerHttpSecurity http) {
            return new RealtimeConfiguration().realtimeSecurityWebFilterChain(http);
        }

        /** 仅替换外部JWT与权限依赖，过滤器及框架链路使用真实实现。 */
        @Bean RealtimeAuthenticationWebFilter authenticationFilter() {
            JwtTokenService jwt = mock(JwtTokenService.class);
            RealtimeAuthorizationService authorization = mock(RealtimeAuthorizationService.class);
            AccessTokenClaims claims = new AccessTokenClaims(10L, 20L, "student", "session-1", 1L,
                    Instant.now().plusSeconds(300));
            LoginUser user = new LoginUser();
            user.setUserId(10L); user.setEnterpriseId(20L);
            user.setPermissions(List.of("student:learning:study"));
            when(jwt.decode("test-access")).thenReturn(claims);
            when(authorization.authenticate(claims)).thenReturn(Mono.just(user));
            return new RealtimeAuthenticationWebFilter(jwt, authorization, new ObjectMapper(), new RealtimeProperties());
        }

        /** 与WebSocket握手一样从最终exchange读取身份，而非直接调用认证过滤器。 */
        @Bean RouterFunction<ServerResponse> principalRoute() {
            return RouterFunctions.route().GET("/ws/learning", request -> request.principal()
                    .flatMap(principal -> ServerResponse.ok().bodyValue(principal.getClass().getSimpleName()))
                    .switchIfEmpty(ServerResponse.status(401).build())).build();
        }
    }
}
