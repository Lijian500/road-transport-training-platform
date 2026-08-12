package me.lj.train.admin.config;

import me.lj.train.admin.support.AuthorizationCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 迁移补发课程权限后清理内置企业管理员的授权缓存。
 */
@Component
public class CoursePermissionCacheInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoursePermissionCacheInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final AuthorizationCacheService authorizationCacheService;

    public CoursePermissionCacheInitializer(
            JdbcTemplate jdbcTemplate,
            AuthorizationCacheService authorizationCacheService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authorizationCacheService = authorizationCacheService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Long> userIds = jdbcTemplate.queryForList(
                "SELECT DISTINCT ur.user_id FROM sys_user_role ur "
                        + "JOIN sys_role r ON r.id = ur.role_id "
                        + "WHERE r.role_code = 'ENTERPRISE_ADMIN' "
                        + "AND r.built_in = 1 AND r.deleted_at IS NULL",
                Long.class);
        try {
            authorizationCacheService.invalidateUsers(userIds);
        } catch (RuntimeException exception) {
            LOGGER.warn("课程权限已写入数据库，但企业管理员授权缓存清理失败，将在缓存过期后生效", exception);
        }
    }
}
