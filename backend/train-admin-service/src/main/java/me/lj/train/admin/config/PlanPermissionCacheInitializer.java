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
 * 迁移补发计划及视频学习权限后清理企业管理员和学员的授权缓存。
 */
@Component
public class PlanPermissionCacheInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlanPermissionCacheInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final AuthorizationCacheService authorizationCacheService;

    public PlanPermissionCacheInitializer(
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
                        + "WHERE r.role_code IN ('ENTERPRISE_ADMIN', 'STUDENT') "
                        + "AND r.built_in = 1 AND r.deleted_at IS NULL",
                Long.class);
        try {
            authorizationCacheService.invalidateUsers(userIds);
        } catch (RuntimeException exception) {
            LOGGER.warn("培训权限已写入数据库，但相关账号授权缓存清理失败，将在缓存过期后生效", exception);
        }
    }
}
