package me.lj.train.realtime.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.lj.train.api.admin.AdminAuthService;
import me.lj.train.api.admin.AdminModels.LoginAccount;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.security.SecurityConstants;
import me.lj.train.common.security.jwt.AccessTokenClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealtimeAuthorizationServiceTest {

    private ReactiveValueOperations<String, String> values;
    private ObjectMapper objectMapper;
    private RealtimeAuthorizationService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        values = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new RealtimeAuthorizationService(redisTemplate, objectMapper);
    }

    @Test
    void shouldBuildFullLoginUserFromAuthorizationCache() throws Exception {
        AccessTokenClaims claims = claims();
        when(values.get(SecurityConstants.REDIS_LOGIN_VERSION_PREFIX + 10L))
                .thenReturn(Mono.just("3"));
        when(values.get(SecurityConstants.REDIS_AUTHORIZATION_PREFIX + 10L))
                .thenReturn(Mono.just(objectMapper.writeValueAsString(account(true, false))));

        me.lj.train.common.security.model.LoginUser user =
                service.authenticate(claims).block();

        assertThat(user).isNotNull();
        assertThat(user.getUserId()).isEqualTo(10L);
        assertThat(user.getEnterpriseId()).isEqualTo(20L);
        assertThat(user.hasPermission("student:learning:study")).isTrue();
    }

    @Test
    void shouldRejectChangedLoginVersion() {
        when(values.get(SecurityConstants.REDIS_LOGIN_VERSION_PREFIX + 10L))
                .thenReturn(Mono.just("4"));

        assertThatThrownBy(() -> service.authenticate(claims()).block())
                .isInstanceOfSatisfying(RealtimeAuthException.class,
                        auth -> assertThat(auth.getErrorCode())
                                .isEqualTo(AppErrorCode.TOKEN_EXPIRED));
    }

    @Test
    void shouldRejectMissingStudyPermission() throws Exception {
        when(values.get(SecurityConstants.REDIS_LOGIN_VERSION_PREFIX + 10L))
                .thenReturn(Mono.just("3"));
        when(values.get(SecurityConstants.REDIS_AUTHORIZATION_PREFIX + 10L))
                .thenReturn(Mono.just(objectMapper.writeValueAsString(account(false, false))));

        assertThatThrownBy(() -> service.authenticate(claims()).block())
                .isInstanceOfSatisfying(RealtimeAuthException.class,
                        auth -> assertThat(auth.getErrorCode())
                                .isEqualTo(AppErrorCode.FORBIDDEN));
    }

    @Test
    void shouldRejectForcedPasswordChange() throws Exception {
        when(values.get(SecurityConstants.REDIS_LOGIN_VERSION_PREFIX + 10L))
                .thenReturn(Mono.just("3"));
        when(values.get(SecurityConstants.REDIS_AUTHORIZATION_PREFIX + 10L))
                .thenReturn(Mono.just(objectMapper.writeValueAsString(account(true, true))));

        assertThatThrownBy(() -> service.authenticate(claims()).block())
                .isInstanceOfSatisfying(RealtimeAuthException.class,
                        auth -> assertThat(auth.getErrorCode())
                                .isEqualTo(AppErrorCode.PASSWORD_CHANGE_REQUIRED));
    }

    @Test
    void shouldFallbackToAdminServiceWhenAuthorizationCacheMisses() {
        AdminAuthService adminAuthService = mock(AdminAuthService.class);
        ReflectionTestUtils.setField(service, "adminAuthService", adminAuthService);
        when(values.get(SecurityConstants.REDIS_LOGIN_VERSION_PREFIX + 10L))
                .thenReturn(Mono.just("3"));
        when(values.get(SecurityConstants.REDIS_AUTHORIZATION_PREFIX + 10L))
                .thenReturn(Mono.empty());
        when(values.set(anyString(), anyString(), any(java.time.Duration.class)))
                .thenReturn(Mono.just(true));
        when(adminAuthService.getAuthorization(10L))
                .thenReturn(Result.ok(account(true, false)));

        me.lj.train.common.security.model.LoginUser user =
                service.authenticate(claims()).block();

        assertThat(user).isNotNull();
        verify(adminAuthService).getAuthorization(10L);
    }

    private AccessTokenClaims claims() {
        return new AccessTokenClaims(
                10L, 20L, "student", "session-1", 3L,
                Instant.now().plusSeconds(300));
    }

    private LoginAccount account(boolean studyPermission, boolean mustChangePassword) {
        return new LoginAccount(
                10L, 20L, "student", "学员", "示例企业", 3L,
                false, mustChangePassword, Collections.singletonList("STUDENT"),
                studyPermission
                        ? Collections.singletonList("student:learning:study")
                        : Collections.emptyList());
    }
}
