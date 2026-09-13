package me.lj.train.realtime.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.lj.train.realtime.protocol.RealtimeMessages.ServerEnvelope;
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

    /** 使用实际序列化配置验证协议序号为数字，业务ID仍为字符串。 */
    @Test
    void shouldSerializeEnvelopeSequenceAsNumber() throws Exception {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new RealtimeConfiguration().realtimeJsonCustomizer().customize(builder);
        ObjectMapper objectMapper = builder.build();

        for (Long sequence : new Long[] {0L, 3L, null}) {
            ServerEnvelope envelope = new ServerEnvelope(
                    "STATE_SYNC", "request-1", "900", sequence, null,
                    new Payload(900L, null));
            var json = objectMapper.readTree(objectMapper.writeValueAsString(envelope));

            assertThat(json.path("payload").path("id").asText()).isEqualTo("900");
            assertThat(json.path("payload").path("id").isTextual()).isTrue();
            if (sequence == null) {
                assertThat(json.has("seq")).isFalse();
            } else {
                assertThat(json.path("seq").isIntegralNumber()).isTrue();
                assertThat(json.path("seq").longValue()).isEqualTo(sequence);
            }
        }
    }

    private record Payload(Long id, String optional) {
    }
}
