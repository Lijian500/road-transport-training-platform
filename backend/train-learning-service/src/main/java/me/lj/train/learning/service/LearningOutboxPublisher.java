package me.lj.train.learning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateWrapper;
import me.lj.train.api.training.LearningTaskEvents;
import me.lj.train.api.training.LearningTaskEvents.LearningTaskEvent;
import me.lj.train.learning.config.LearningProperties;
import me.lj.train.learning.mapper.MqOutboxMapper;
import me.lj.train.learning.model.entity.MqOutboxEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static me.lj.train.learning.model.table.MqOutboxTableDef.MQ_OUTBOX;

/**
 * 带发布确认和退避重试的Outbox发送器。
 */
@Component
@ConditionalOnProperty(prefix = "learning.mq", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class LearningOutboxPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(LearningOutboxPublisher.class);
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_FAILED = "FAILED";

    private final MqOutboxMapper outboxMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final LearningProperties properties;
    private final TransactionTemplate transactionTemplate;

    public LearningOutboxPublisher(
            MqOutboxMapper outboxMapper,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            LearningProperties properties,
            PlatformTransactionManager transactionManager) {
        this.outboxMapper = outboxMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${learning.outbox-poll-interval-ms:1000}")
    public void publishPending() {
        // FAILED表示已超过告警阈值，仍需按nextRetryAt自动补偿，避免任务状态永久不同步。
        List<MqOutboxEntity> pending = outboxMapper.selectListByQuery(QueryWrapper.create()
                .where(MQ_OUTBOX.STATUS.in(STATUS_PENDING, STATUS_FAILED))
                .and(MQ_OUTBOX.NEXT_RETRY_AT.le(LocalDateTime.now()))
                .orderBy(MQ_OUTBOX.ID.asc())
                .limit(properties.getOutboxBatchSize()));
        for (MqOutboxEntity event : pending) {
            publishOne(event);
        }
    }

    private void publishOne(MqOutboxEntity outbox) {
        try {
            LearningTaskEvent event = objectMapper.readValue(
                    outbox.getPayload(), LearningTaskEvent.class);
            CorrelationData correlationData = new CorrelationData(outbox.getEventId());
            rabbitTemplate.convertAndSend(
                    LearningTaskEvents.EXCHANGE, outbox.getRoutingKey(), event, correlationData);
            CorrelationData.Confirm confirm = correlationData.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.isAck()) {
                throw new IllegalStateException(confirm.getReason());
            }
            transactionTemplate.executeWithoutResult(status -> {
                MqOutboxEntity update = UpdateWrapper.of(MqOutboxEntity.class)
                        .set(MQ_OUTBOX.STATUS, "SENT")
                        .set(MQ_OUTBOX.SENT_AT, LocalDateTime.now())
                        .set(MQ_OUTBOX.LAST_ERROR, null).toEntity();
                outboxMapper.updateByCondition(update, MQ_OUTBOX.ID.eq(outbox.getId())
                        .and(MQ_OUTBOX.STATUS.in(STATUS_PENDING, STATUS_FAILED)));
            });
        } catch (Exception exception) {
            LOGGER.warn("学习任务事件发送失败，eventId={}", outbox.getEventId(), exception);
            recordFailure(outbox, exception);
        }
    }

    private void recordFailure(MqOutboxEntity outbox, Exception exception) {
        transactionTemplate.executeWithoutResult(status -> {
            int retryCount = outbox.getRetryCount() + 1;
            String nextStatus = retryCount >= properties.getOutboxMaxRetries()
                    ? STATUS_FAILED : STATUS_PENDING;
            long delaySeconds = Math.min(300L, 1L << Math.min(retryCount, 8));
            String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage();
            if (message.length() > 500) {
                message = message.substring(0, 500);
            }
            MqOutboxEntity update = UpdateWrapper.of(MqOutboxEntity.class)
                    .set(MQ_OUTBOX.STATUS, nextStatus)
                    .set(MQ_OUTBOX.RETRY_COUNT, retryCount)
                    .set(MQ_OUTBOX.NEXT_RETRY_AT, LocalDateTime.now().plusSeconds(delaySeconds))
                    .set(MQ_OUTBOX.LAST_ERROR, message).toEntity();
            outboxMapper.updateByCondition(update, MQ_OUTBOX.ID.eq(outbox.getId())
                    .and(MQ_OUTBOX.STATUS.in(STATUS_PENDING, STATUS_FAILED)));
        });
    }
}
