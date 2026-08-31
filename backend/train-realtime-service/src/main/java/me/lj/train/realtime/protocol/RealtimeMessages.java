package me.lj.train.realtime.protocol;

import com.fasterxml.jackson.databind.JsonNode;

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
            Long seq,
            Instant sentAt,
            Object payload) {
    }

    public record BindPayload(String clientInstanceId, boolean reconnecting) {
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
