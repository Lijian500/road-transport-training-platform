package me.lj.train.gateway.config;

import me.lj.train.common.security.jwt.JwtTokenService;
import me.lj.train.common.security.jwt.PemKeyUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Gateway仅由全局过滤器处理Cookie JWT，关闭默认登录机制。
 */
@Configuration
@EnableConfigurationProperties(GatewaySecurityProperties.class)
public class GatewaySecurityConfig {

    @Bean
    public JwtTokenService gatewayJwtTokenService(GatewaySecurityProperties properties) {
        return new JwtTokenService(
                properties.getIssuer(),
                PemKeyUtils.readPublicKey(properties.getPublicKeyPath()));
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(exchange -> exchange.anyExchange().permitAll())
                .build();
    }
}
