package me.lj.train.learning.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.lj.train.api.training.LearningTaskEvents;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * 学习服务调度、时钟和RabbitMQ配置。
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(LearningProperties.class)
public class LearningConfiguration {

    @Bean
    public Clock learningClock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    @ConditionalOnProperty(prefix = "learning.mq", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public TopicExchange trainingEventExchange() {
        return new TopicExchange(LearningTaskEvents.EXCHANGE, true, false);
    }

    @Bean
    @ConditionalOnProperty(prefix = "learning.mq", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public Queue learningTaskProjectionQueue() {
        return new Queue(LearningTaskEvents.QUEUE, true);
    }

    @Bean
    @ConditionalOnProperty(prefix = "learning.mq", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public Binding learningTaskProjectionBinding(
            TopicExchange trainingEventExchange, Queue learningTaskProjectionQueue) {
        return BindingBuilder.bind(learningTaskProjectionQueue)
                .to(trainingEventExchange).with("learning.task.study.*.v1");
    }

    @Bean
    @ConditionalOnProperty(prefix = "learning.mq", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public MessageConverter learningEventMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
