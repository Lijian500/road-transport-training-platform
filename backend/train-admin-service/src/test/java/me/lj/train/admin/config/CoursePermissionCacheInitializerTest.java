package me.lj.train.admin.config;

import me.lj.train.admin.support.AuthorizationCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoursePermissionCacheInitializerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private AuthorizationCacheService authorizationCacheService;
    @Mock
    private ApplicationArguments applicationArguments;

    @Test
    void shouldInvalidateBuiltInEnterpriseAdministratorCaches() {
        when(jdbcTemplate.queryForList(
                contains("r.role_code = 'ENTERPRISE_ADMIN'"), eq(Long.class)))
                .thenReturn(Arrays.asList(10L, 11L));
        CoursePermissionCacheInitializer initializer = new CoursePermissionCacheInitializer(
                jdbcTemplate, authorizationCacheService);

        initializer.run(applicationArguments);

        verify(authorizationCacheService).invalidateUsers(Arrays.asList(10L, 11L));
    }

    @Test
    void shouldKeepStartupAvailableWhenRedisCacheInvalidationFails() {
        when(jdbcTemplate.queryForList(
                contains("r.role_code = 'ENTERPRISE_ADMIN'"), eq(Long.class)))
                .thenReturn(Arrays.asList(10L, 11L));
        doThrow(new IllegalStateException("Redis unavailable"))
                .when(authorizationCacheService).invalidateUsers(Arrays.asList(10L, 11L));
        CoursePermissionCacheInitializer initializer = new CoursePermissionCacheInitializer(
                jdbcTemplate, authorizationCacheService);

        initializer.run(applicationArguments);

        verify(authorizationCacheService).invalidateUsers(Arrays.asList(10L, 11L));
    }
}
