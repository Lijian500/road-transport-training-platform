package me.lj.train.webapi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.security.SecurityConstants;
import me.lj.train.common.security.jwt.JwtTokenService;
import me.lj.train.common.security.jwt.PemKeyUtils;
import me.lj.train.webapi.security.TrustedUserContextFilter;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.nio.charset.StandardCharsets;

/**
 * Stateless Cookie认证与CSRF配置。
 */
@Configuration
@EnableConfigurationProperties(WebApiSecurityProperties.class)
public class WebApiSecurityConfig {

    @Bean
    public JwtTokenService jwtTokenService(WebApiSecurityProperties properties) {
        return new JwtTokenService(
                properties.getIssuer(),
                PemKeyUtils.readPrivateKey(properties.getPrivateKeyPath()),
                PemKeyUtils.readPublicKey(properties.getPublicKeyPath()));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            TrustedUserContextFilter trustedUserContextFilter,
            WebApiSecurityProperties properties,
            ObjectMapper objectMapper) throws Exception {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName(SecurityConstants.CSRF_COOKIE);
        repository.setHeaderName(SecurityConstants.CSRF_HEADER);
        repository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .sameSite("Lax")
                .secure(properties.isSecureCookie()));

        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName("_csrf");
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(repository)
                        .csrfTokenRequestHandler(requestHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .exceptionHandling(exception -> exception.accessDeniedHandler((request, response, denied) -> {
                    response.setStatus(AppErrorCode.CSRF_INVALID.getHttpStatus());
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    objectMapper.writeValue(
                            response.getWriter(),
                            Result.failed(AppErrorCode.CSRF_INVALID));
                }))
                .addFilterAfter(trustedUserContextFilter, CsrfFilter.class);
        return http.build();
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longIdCustomizer() {
        return builder -> builder
                .serializerByType(Long.class, ToStringSerializer.instance);
    }
}
