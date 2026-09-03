package me.lj.train.api.learning;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学习人脸抽验任务、提交及实时事件模型。
 */
public final class FaceCheckModels {

    private FaceCheckModels() {
    }

    /** 提交一次人脸抽验照片。 */
    public record SubmitFaceCheckCommand(
            Long taskId,
            String requestId,
            byte[] imageBytes) implements Serializable {
    }

    /** 登记照绑定前的单人脸校验结果。 */
    public record FaceReferenceValidationView(
            boolean valid,
            String message,
            int faceCount) implements Serializable {
    }

    /** 学员可见的人脸抽验任务状态。 */
    public record FaceCheckView(
            Long taskId,
            Long sessionId,
            String status,
            LocalDateTime triggeredAt,
            LocalDateTime deadlineAt,
            int attemptCount,
            int maxAttempts,
            int remainingAttempts,
            String result,
            String failureReason,
            Double similarity,
            LocalDateTime completedAt) implements Serializable {
    }

    /** 学习服务发送给实时服务的人脸抽验事件。 */
    public record FaceCheckRealtimeEvent(
            String eventId,
            String eventType,
            LocalDateTime occurredAt,
            Long enterpriseId,
            Long userId,
            Long sessionId,
            FaceCheckView faceCheck) implements Serializable {
    }
}
