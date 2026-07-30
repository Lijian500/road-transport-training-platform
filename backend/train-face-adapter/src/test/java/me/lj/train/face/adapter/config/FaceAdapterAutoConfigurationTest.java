package me.lj.train.face.adapter.config;

import me.lj.train.face.adapter.FaceVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class FaceAdapterAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FaceAdapterAutoConfiguration.class));

    @Test
    void shouldNotLoadModelsUnlessExplicitlyEnabled() {
        contextRunner.run(context -> assertThat(context)
                .doesNotHaveBean(FaceVerifier.class)
                .hasNotFailed());
    }

    @Test
    void shouldFailFastWhenEnabledWithoutModelPaths() {
        contextRunner
                .withPropertyValues("train.face.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }
}
