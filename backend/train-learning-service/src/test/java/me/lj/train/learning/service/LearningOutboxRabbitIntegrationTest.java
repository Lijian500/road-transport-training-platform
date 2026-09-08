package me.lj.train.learning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateWrapper;
import me.lj.train.api.training.LearningTaskEvents;
import me.lj.train.api.training.LearningTaskEvents.LearningTaskEvent;
import me.lj.train.learning.config.LearningProperties;
import me.lj.train.learning.mapper.MqOutboxMapper;
import me.lj.train.learning.model.entity.MqOutboxEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 真实RabbitMQ投递实验：只使用独立demo.verify路由与队列，不中断共享Broker。
 * 持久化Mapper使用测试替身，此结果不等同于MySQL和业务服务重启恢复验收。
 */
@EnabledIfEnvironmentVariable(named = "RABBITMQ_INTEGRATION_ENABLED", matches = "(?i)true")
class LearningOutboxRabbitIntegrationTest {

    /** 路由缺失保留重试；补齐队列并重建发送器后投递，重复发送保留同一幂等ID。 */
    @Test
    void shouldRecoverUnroutableEventAndKeepIdentityAcrossPublisherRecreation() throws Exception {
        String route = "demo.verify." + UUID.randomUUID();
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        CachingConnectionFactory connection = connection();
        RabbitAdmin admin = new RabbitAdmin(connection);
        RabbitTemplate template = new RabbitTemplate(connection);
        template.setMandatory(true);
        template.setMessageConverter(new Jackson2JsonMessageConverter(json));
        MqOutboxMapper mapper = mock(MqOutboxMapper.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(mock(TransactionStatus.class));
        LearningTaskEvent event = new LearningTaskEvent(route, route, LocalDateTime.now(),
                1L, 2L, 3L, 4L);
        MqOutboxEntity outbox = new MqOutboxEntity();
        outbox.setId(1L);
        outbox.setEventId(event.eventId());
        outbox.setRoutingKey(route);
        outbox.setPayload(json.writeValueAsString(event));
        outbox.setStatus("PENDING");
        outbox.setRetryCount(0);
        when(mapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Collections.singletonList(outbox));
        try {
            // 与应用声明一致；测试路由不匹配任何正式业务的精确绑定。
            admin.declareExchange(new TopicExchange(LearningTaskEvents.EXCHANGE));
            LearningOutboxPublisher publisher = new LearningOutboxPublisher(
                    mapper, template, json, new LearningProperties(), transactions);
            publisher.publishPending();
            ArgumentCaptor<MqOutboxEntity> updates = ArgumentCaptor.forClass(MqOutboxEntity.class);
            verify(mapper).updateByCondition(updates.capture(), any(QueryCondition.class));
            assertThat(((UpdateWrapper<?>) updates.getValue()).getUpdates().values())
                    .contains("PENDING").doesNotContain("SENT");

            admin.declareQueue(new Queue(route, true, false, false));
            admin.declareBinding(new Binding(route, Binding.DestinationType.QUEUE,
                    LearningTaskEvents.EXCHANGE, route, null));
            new LearningOutboxPublisher(mapper, template, json, new LearningProperties(), transactions)
                    .publishPending();
            publisher.publishPending();
            verify(mapper, times(3)).updateByCondition(updates.capture(), any(QueryCondition.class));
            assertThat(((UpdateWrapper<?>) updates.getValue()).getUpdates().values()).contains("SENT");
            for (int index = 0; index < 2; index++) {
                Object received = template.receiveAndConvert(route, 10000);
                assertThat(received).isInstanceOf(LearningTaskEvent.class);
                assertThat(((LearningTaskEvent) received).eventId()).isEqualTo(event.eventId());
            }
            assertThat(template.receive(route)).isNull();
        } finally {
            try { admin.deleteQueue(route); } finally { connection.destroy(); }
        }
    }

    /** 显式读取测试连接，凭据只通过环境传入，不进入测试参数和输出。 */
    private CachingConnectionFactory connection() {
        CachingConnectionFactory connection = new CachingConnectionFactory(
                required("RABBITMQ_HOST"), Integer.parseInt(required("RABBITMQ_PORT")));
        connection.setUsername(required("RABBITMQ_USERNAME"));
        connection.setPassword(required("RABBITMQ_PASSWORD"));
        connection.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connection.setPublisherReturns(true);
        return connection;
    }

    /** 缺少真实连接配置时失败，不降级到默认Broker。 */
    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少" + name);
        return value;
    }
}
