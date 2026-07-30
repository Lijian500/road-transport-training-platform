package me.lj.train.admin.config;

import me.lj.train.admin.service.AdminAuthServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class BootstrapInitializerTest {

    @Mock
    private AdminAuthServiceImpl adminAuthService;
    @Mock
    private ApplicationArguments arguments;

    @Test
    void shouldNotInitializeWhenBootstrapIsDisabled() {
        BootstrapInitializer initializer = new BootstrapInitializer(
                adminAuthService,
                false,
                "super-admin",
                "Password1",
                "平台管理员");

        initializer.run(arguments);

        verifyNoInteractions(adminAuthService);
    }

    @Test
    void shouldInitializeWithConfiguredValuesWhenBootstrapIsEnabled() {
        BootstrapInitializer initializer = new BootstrapInitializer(
                adminAuthService,
                true,
                "super-admin",
                "Password1",
                "平台管理员");

        initializer.run(arguments);

        verify(adminAuthService)
                .bootstrapSuperAdmin("super-admin", "Password1", "平台管理员");
    }
}
