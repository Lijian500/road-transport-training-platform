package me.lj.train.realtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.lj.train.api.learning.FaceCheckEvents;
import me.lj.train.api.learning.FaceCheckModels.FaceCheckRealtimeEvent;
import me.lj.train.realtime.connection.LearningConnection;
import me.lj.train.realtime.connection.LearningConnectionRegistry;
import me.lj.train.realtime.protocol.RealtimeMessages.ServerEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** 将人脸抽验业务事件推送给当前实例内的在线学习连接。 */
@Component
public class FaceCheckRealtimeConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            FaceCheckRealtimeConsumer.class);

    private final LearningConnectionRegistry connectionRegistry;
    private final ObjectMapper objectMapper;

    public FaceCheckRealtimeConsumer(
            LearningConnectionRegistry connectionRegistry,
            ObjectMapper objectMapper) {
        this.connectionRegistry = connectionRegistry;
        this.objectMapper = objectMapper;
    }

    /** 消费抽验事件；离线时以学习服务数据库状态和重连同步为准。 */
    @RabbitListener(queues = FaceCheckEvents.QUEUE)
    public void consume(FaceCheckRealtimeEvent event) {
        if (!valid(event)) {
            LOGGER.warn("忽略字段不完整的人脸抽验实时事件");
            return;
        }
        String message = toJson(new ServerEnvelope(
                event.eventType(), event.eventId(), String.valueOf(event.sessionId()),
                null, Instant.now(), event.faceCheck()));
        for (LearningConnection connection : connectionRegistry.find(
                event.enterpriseId(), event.userId(), event.sessionId())) {
            if (!connection.emit(message)) {
                LOGGER.debug("人脸抽验实时消息发送失败，connectionId={}",
                        connection.getConnectionId());
            }
        }
    }

    private boolean valid(FaceCheckRealtimeEvent event) {
        return event != null && event.eventId() != null && !event.eventId().isBlank()
                && (FaceCheckEvents.REQUIRED_EVENT.equals(event.eventType())
                || FaceCheckEvents.RESULT_EVENT.equals(event.eventType()))
                && event.enterpriseId() != null && event.userId() != null
                && event.sessionId() != null && event.faceCheck() != null;
    }

    private String toJson(ServerEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("人脸抽验实时消息序列化失败", exception);
        }
    }
}
