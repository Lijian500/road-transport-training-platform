package me.lj.train.learning.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.api.training.LearningTaskEvents.LearningTaskEvent;
import me.lj.train.api.learning.FaceCheckModels.FaceCheckRealtimeEvent;
import me.lj.train.common.core.util.IdGenerator;
import me.lj.train.learning.mapper.MqOutboxMapper;
import me.lj.train.learning.model.entity.MqOutboxEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

import static me.lj.train.learning.model.table.MqOutboxTableDef.MQ_OUTBOX;

/**
 * 在学习事务内幂等写入任务状态Outbox事件。
 */
@Component
public class LearningOutboxService {

    private final MqOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    public LearningOutboxService(MqOutboxMapper outboxMapper, ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
    }

    public void appendTaskEvent(
            String eventType,
            String businessStage,
            Long enterpriseId,
            Long userId,
            Long taskId,
            Long planId,
            LocalDateTime occurredAt) {
        String businessKey = "TASK:" + taskId + ":" + businessStage;
        long existing = outboxMapper.selectCountByQuery(QueryWrapper.create()
                .where(MQ_OUTBOX.BUSINESS_KEY.eq(businessKey)));
        if (existing > 0) {
            return;
        }
        String eventId = UUID.randomUUID().toString();
        LearningTaskEvent event = new LearningTaskEvent(
                eventId, eventType, occurredAt, enterpriseId, userId, taskId, planId);
        MqOutboxEntity outbox = new MqOutboxEntity();
        outbox.setId(IdGenerator.nextId());
        outbox.setEventId(eventId);
        outbox.setBusinessKey(businessKey);
        outbox.setAggregateType("TRAINING_TASK");
        outbox.setAggregateId(taskId);
        outbox.setRoutingKey(eventType);
        outbox.setPayload(toJson(event));
        outbox.setStatus("PENDING");
        outbox.setNextRetryAt(occurredAt);
        try {
            outboxMapper.insertSelective(outbox);
        } catch (DuplicateKeyException ignored) {
            // 同一任务阶段只允许一个业务事件，并发事件由唯一业务键收敛。
        }
    }

    /** 在抽验事务内幂等写入实时通知Outbox。 */
    public void appendFaceCheckEvent(
            Long faceCheckTaskId,
            String businessStage,
            String routingKey,
            FaceCheckRealtimeEvent event,
            LocalDateTime occurredAt) {
        String businessKey = "FACE_CHECK:" + faceCheckTaskId + ":" + businessStage;
        if (outboxMapper.selectCountByQuery(QueryWrapper.create()
                .where(MQ_OUTBOX.BUSINESS_KEY.eq(businessKey))) > 0) {
            return;
        }
        MqOutboxEntity outbox = new MqOutboxEntity();
        outbox.setId(IdGenerator.nextId());
        outbox.setEventId(event.eventId());
        outbox.setBusinessKey(businessKey);
        outbox.setAggregateType("FACE_CHECK");
        outbox.setAggregateId(faceCheckTaskId);
        outbox.setRoutingKey(routingKey);
        outbox.setPayload(toJson(event));
        outbox.setStatus("PENDING");
        outbox.setNextRetryAt(occurredAt);
        try {
            outboxMapper.insertSelective(outbox);
        } catch (DuplicateKeyException ignored) {
            // 同一抽验阶段只发送一次实时消息。
        }
    }

    private String toJson(LearningTaskEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("学习任务事件序列化失败", exception);
        }
    }

    private String toJson(FaceCheckRealtimeEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("人脸抽验事件序列化失败", exception);
        }
    }
}
