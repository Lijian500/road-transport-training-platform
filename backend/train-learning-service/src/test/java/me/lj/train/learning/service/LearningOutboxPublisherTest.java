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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Outbox失败事件自动恢复投递测试。 */
@ExtendWith(MockitoExtension.class)
class LearningOutboxPublisherTest {

    @Mock private MqOutboxMapper outboxMapper;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;

    @Test
    void shouldRetryFailedEventAndMarkItSentAfterRabbitRecovers() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        MqOutboxEntity outbox = failedOutbox(objectMapper);
        when(outboxMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(outbox));
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq(LearningTaskEvents.EXCHANGE), eq(outbox.getRoutingKey()),
                any(LearningTaskEvent.class), any(CorrelationData.class));
        LearningOutboxPublisher publisher = new LearningOutboxPublisher(
                outboxMapper, rabbitTemplate, objectMapper, new LearningProperties(),
                transactionManager);

        publisher.publishPending();

        ArgumentCaptor<QueryWrapper> selectCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(outboxMapper).selectListByQuery(selectCaptor.capture());
        assertThat(selectCaptor.getValue().toSQL()).contains("'PENDING'").contains("'FAILED'");
        ArgumentCaptor<MqOutboxEntity> updateCaptor =
                ArgumentCaptor.forClass(MqOutboxEntity.class);
        ArgumentCaptor<QueryCondition> conditionCaptor =
                ArgumentCaptor.forClass(QueryCondition.class);
        verify(outboxMapper).updateByCondition(
                updateCaptor.capture(), conditionCaptor.capture());
        assertThat(((UpdateWrapper<?>) updateCaptor.getValue()).getUpdates().values())
                .contains("SENT");
        assertThat(QueryWrapper.create().where(conditionCaptor.getValue()).toSQL())
                .contains("'PENDING'")
                .contains("'FAILED'");
    }

    private MqOutboxEntity failedOutbox(ObjectMapper objectMapper) throws Exception {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 19, 16, 0);
        LearningTaskEvent event = new LearningTaskEvent(
                "event-one", LearningTaskEvents.COMPLETED_ROUTING_KEY, occurredAt,
                20L, 10L, 500L, 100L);
        MqOutboxEntity outbox = new MqOutboxEntity();
        outbox.setId(900L);
        outbox.setEventId(event.eventId());
        outbox.setRoutingKey(event.eventType());
        outbox.setPayload(objectMapper.writeValueAsString(event));
        outbox.setStatus("FAILED");
        outbox.setRetryCount(10);
        outbox.setNextRetryAt(occurredAt);
        return outbox;
    }
}
