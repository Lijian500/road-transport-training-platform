package me.lj.train.realtime.protocol;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.IOException;
import java.time.Instant;

/** 实时学习协议消息模型。 */
public final class RealtimeMessages {

    private RealtimeMessages() {
    }

    public record ClientEnvelope(
            String type,
            String requestId,
            String studySessionId,
            Long seq,
            Instant sentAt,
            JsonNode payload) {
    }

    public record ServerEnvelope(
            String type,
            String requestId,
            String studySessionId,
            // 序号属于协议数值，不能使用业务Long ID的字符串序列化规则。
            @JsonSerialize(using = SequenceSerializer.class)
            Long seq,
            Instant sentAt,
            Object payload) {
    }

    public record BindPayload(String clientInstanceId, boolean reconnecting) {
    }

    /** 保持可空协议序号的JSON数字类型，不影响业务ID的精度保护。 */
    public static final class SequenceSerializer extends JsonSerializer<Long> {
        @Override
        public void serialize(Long value, JsonGenerator generator, SerializerProvider serializers)
                throws IOException {
            generator.writeNumber(value.longValue());
        }
    }

    public record LearningPayload(
            String coursewareSnapshotId,
            long videoPositionMillis) {
    }

    public record AckPayload(long acceptedSequence, String status) {
    }

    public record ErrorPayload(
            String code,
            String message,
            boolean retryable,
            boolean resyncRequired) {
    }

    public record PongPayload(Instant serverTime) {
    }

    public record SessionReplacedPayload(String message) {
    }
}
