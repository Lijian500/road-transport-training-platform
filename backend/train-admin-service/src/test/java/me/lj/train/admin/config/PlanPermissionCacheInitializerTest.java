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

/** 培训计划权限缓存刷新启动器测试。 */
@ExtendWith(MockitoExtension.class)
class PlanPermissionCacheInitializerTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private AuthorizationCacheService authorizationCacheService;
    @Mock private ApplicationArguments applicationArguments;

    @Test
    void shouldInvalidateBuiltInAdministratorAndStudentCaches() {
        when(jdbcTemplate.queryForList(
                contains("'ENTERPRISE_ADMIN', 'STUDENT'"), eq(Long.class)))
                .thenReturn(Arrays.asList(10L, 11L));
        PlanPermissionCacheInitializer initializer = new PlanPermissionCacheInitializer(
                jdbcTemplate, authorizationCacheService);

        initializer.run(applicationArguments);

        verify(authorizationCacheService).invalidateUsers(Arrays.asList(10L, 11L));
    }

    @Test
    void shouldKeepStartupAvailableWhenCacheInvalidationFails() {
        when(jdbcTemplate.queryForList(
                contains("'ENTERPRISE_ADMIN', 'STUDENT'"), eq(Long.class)))
                .thenReturn(Arrays.asList(10L, 11L));
        doThrow(new IllegalStateException("Redis unavailable"))
                .when(authorizationCacheService).invalidateUsers(Arrays.asList(10L, 11L));
        PlanPermissionCacheInitializer initializer = new PlanPermissionCacheInitializer(
                jdbcTemplate, authorizationCacheService);

        initializer.run(applicationArguments);

        verify(authorizationCacheService).invalidateUsers(Arrays.asList(10L, 11L));
    }
}
