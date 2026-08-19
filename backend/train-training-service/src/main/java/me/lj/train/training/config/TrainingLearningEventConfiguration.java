package me.lj.train.training.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.lj.train.api.training.LearningTaskEvents;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 学习任务投影RabbitMQ拓扑。
 */
@Configuration
@ConditionalOnProperty(prefix = "learning.mq", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class TrainingLearningEventConfiguration {

    @Bean
    public TopicExchange trainingEventExchange() {
        return new TopicExchange(LearningTaskEvents.EXCHANGE, true, false);
    }

    @Bean
    public Queue learningTaskProjectionQueue() {
        return new Queue(LearningTaskEvents.QUEUE, true);
    }

    @Bean
    public Binding learningTaskProjectionBinding(
            TopicExchange trainingEventExchange, Queue learningTaskProjectionQueue) {
        return BindingBuilder.bind(learningTaskProjectionQueue)
                .to(trainingEventExchange).with("learning.task.study.*.v1");
    }

    @Bean
    public MessageConverter learningEventMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
