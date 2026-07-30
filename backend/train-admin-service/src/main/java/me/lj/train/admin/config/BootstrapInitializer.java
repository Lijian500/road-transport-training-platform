package me.lj.train.admin.config;

import me.lj.train.admin.service.AdminAuthServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 根据环境变量幂等初始化平台超级管理员。
 */
@Component
@ConditionalOnProperty(prefix = "dubbo", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BootstrapInitializer implements ApplicationRunner {

    private final AdminAuthServiceImpl adminAuthService;
    private final boolean enabled;
    private final String username;
    private final String password;
    private final String displayName;

    public BootstrapInitializer(
            AdminAuthServiceImpl adminAuthService,
            @Value("${app.bootstrap.enabled:false}") boolean enabled,
            @Value("${app.bootstrap.username:}") String username,
            @Value("${app.bootstrap.password:}") String password,
            @Value("${app.bootstrap.display-name:平台超级管理员}") String displayName) {
        this.adminAuthService = adminAuthService;
        this.enabled = enabled;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (enabled) {
            adminAuthService.bootstrapSuperAdmin(username, password, displayName);
        }
    }
}
