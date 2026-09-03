package me.lj.train.realtime.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.lj.train.api.learning.FaceCheckEvents;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 人脸抽验实时事件RabbitMQ拓扑。 */
@Configuration
public class FaceCheckEventConfiguration {

    @Bean
    public TopicExchange realtimeTrainingEventExchange() {
        return new TopicExchange(FaceCheckEvents.EXCHANGE, true, false);
    }

    @Bean
    public Queue realtimeFaceCheckQueue() {
        return new Queue(FaceCheckEvents.QUEUE, true);
    }

    @Bean
    public Binding realtimeFaceCheckBinding(
            TopicExchange realtimeTrainingEventExchange,
            Queue realtimeFaceCheckQueue) {
        return BindingBuilder.bind(realtimeFaceCheckQueue)
                .to(realtimeTrainingEventExchange).with("learning.face-check.*.v1");
    }

    @Bean
    public MessageConverter realtimeEventMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
