package me.lj.train.realtime.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import me.lj.train.common.security.jwt.JwtTokenService;
import me.lj.train.common.security.jwt.PemKeyUtils;
import me.lj.train.realtime.websocket.LearningWebSocketHandler;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.Collections;

/** WebSocket路由、JWT与序列化配置。 */
@Configuration
@EnableConfigurationProperties({RealtimeProperties.class, RealtimeSecurityProperties.class})
public class RealtimeConfiguration {

    @Bean
    public JwtTokenService realtimeJwtTokenService(RealtimeSecurityProperties properties) {
        return new JwtTokenService(
                properties.getIssuer(),
                PemKeyUtils.readPublicKey(properties.getPublicKeyPath()));
    }

    @Bean
    public SecurityWebFilterChain realtimeSecurityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(exchange -> exchange.anyExchange().permitAll())
                .build();
    }

    @Bean
    public HandlerMapping learningWebSocketMapping(LearningWebSocketHandler handler) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setOrder(-1);
        mapping.setUrlMap(Collections.singletonMap("/ws/learning", handler));
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer realtimeJsonCustomizer() {
        return builder -> {
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializationInclusion(JsonInclude.Include.NON_NULL);
        };
    }
}
