package me.lj.train.realtime.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeConfigurationTest {

    @Test
    void shouldEnableDubboConsumerByDefault() throws Exception {
        PropertySource<?> source = new YamlPropertySourceLoader()
                .load("realtime", new ClassPathResource("application.yml")).get(0);
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(source);

        Boolean enabled = new PropertySourcesPropertyResolver(sources)
                .getProperty("dubbo.enabled", Boolean.class);

        assertThat(enabled).isTrue();
    }

    @Test
    void shouldSerializeBusinessIdAsStringAndOmitNullFields() throws Exception {
        RealtimeConfiguration configuration = new RealtimeConfiguration();
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        configuration.realtimeJsonCustomizer().customize(builder);
        ObjectMapper objectMapper = builder.build();

        String json = objectMapper.writeValueAsString(new Payload(900L, null));

        assertThat(json).isEqualTo("{\"id\":\"900\"}");
    }

    private record Payload(Long id, String optional) {
    }
}
