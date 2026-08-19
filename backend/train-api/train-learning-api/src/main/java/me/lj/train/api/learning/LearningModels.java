package me.lj.train.api.learning;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 学习会话、课程进度及有效学时RPC模型。
 */
public final class LearningModels {

    private LearningModels() {
    }

    public record OpenSessionCommand(
            Long planId,
            Long planCourseId,
            String clientInstanceId) implements Serializable {
    }

    public record SubmitEventCommand(
            Long sessionId,
            String clientInstanceId,
            String requestId,
            long sequence,
            String eventType,
            Long coursewareSnapshotId,
            long videoPositionMillis) implements Serializable {
    }

    public record TerminateSessionCommand(Long sessionId) implements Serializable {
    }

    public record PlaybackUrlCommand(
            Long sessionId,
            String clientInstanceId,
            Long coursewareSnapshotId) implements Serializable {
    }

    public record CoursewareProgressView(
            Long coursewareSnapshotId,
            String title,
            int sortOrder,
            long durationMillis,
            long confirmedPositionMillis,
            long maxConfirmedPositionMillis,
            String status,
            LocalDateTime completedAt) implements Serializable {
    }

    public record CourseProgressView(
            Long planCourseId,
            String courseName,
            int sortOrder,
            long requiredDurationMillis,
            long effectiveDurationMillis,
            boolean allowSeek,
            int progressReportIntervalSeconds,
            int studyToleranceSeconds,
            String status,
            List<CoursewareProgressView> coursewares,
            LocalDateTime completedAt) implements Serializable {
    }

    public record PlanProgressView(
            Long taskId,
            Long planId,
            String taskStudyStatus,
            String taskCompletionStatus,
            boolean synchronizationPending,
            List<CourseProgressView> courses) implements Serializable {
    }

    public record LearningSessionView(
            Long id,
            Long taskId,
            Long planId,
            Long planCourseId,
            String courseName,
            String status,
            Long currentCoursewareSnapshotId,
            long lastSequence,
            long confirmedPositionMillis,
            long effectiveDurationMillis,
            long requiredDurationMillis,
            LocalDateTime lastEventAt,
            LocalDateTime createdAt) implements Serializable {
    }

    public record LearningEventResultView(
            Long sessionId,
            String requestId,
            long acceptedSequence,
            String status,
            Long currentCoursewareSnapshotId,
            long confirmedPositionMillis,
            long creditedDurationMillis,
            long effectiveDurationMillis,
            long requiredDurationMillis,
            boolean coursewareCompleted,
            boolean courseCompleted,
            LocalDateTime serverTime) implements Serializable {
    }
}
