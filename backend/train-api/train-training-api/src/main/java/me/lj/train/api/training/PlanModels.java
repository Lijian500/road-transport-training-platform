package me.lj.train.api.training;

import me.lj.train.common.core.page.PageRequest;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 培训计划、发布快照与学员任务RPC模型。
 */
public final class PlanModels {

    private PlanModels() {
    }

    public record PlanQuery(
            int pageNumber,
            int pageSize,
            String keyword,
            String status) implements Serializable {

        public PageRequest toPageRequest() {
            return new PageRequest(pageNumber, pageSize);
        }
    }

    public record StudentPlanQuery(
            int pageNumber,
            int pageSize,
            String status) implements Serializable {

        public PageRequest toPageRequest() {
            return new PageRequest(pageNumber, pageSize);
        }
    }

    public record CreatePlanCommand(
            String name,
            String description,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean examRequired,
            Long examPaperId,
            Integer examPassScore,
            boolean faceCheckEnabled,
            int faceCheckMinIntervalSeconds,
            int faceCheckMaxIntervalSeconds,
            int faceCheckTimeoutSeconds,
            int faceCheckMaxAttempts) implements Serializable {
    }

    public record UpdatePlanCommand(
            Long id,
            String name,
            String description,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean examRequired,
            Long examPaperId,
            Integer examPassScore,
            boolean faceCheckEnabled,
            int faceCheckMinIntervalSeconds,
            int faceCheckMaxIntervalSeconds,
            int faceCheckTimeoutSeconds,
            int faceCheckMaxAttempts,
            List<Long> courseIds,
            List<Long> userIds) implements Serializable {
    }

    public record PlanCourseOptionView(
            Long courseId,
            String name,
            int requiredDurationSeconds,
            int coursewareCount,
            long totalVideoDurationSeconds) implements Serializable {
    }

    public record PlanParticipantOptionView(
            Long userId,
            Long orgId,
            String orgName,
            String username,
            String displayName,
            boolean faceReferenceEnrolled) implements Serializable {
    }

    public record PlanCoursewareSnapshotView(
            Long id,
            Long sourceCoursewareId,
            Long storageObjectId,
            String title,
            int durationSeconds,
            int sortOrder) implements Serializable {
    }

    public record PlanCourseView(
            Long id,
            Long courseId,
            String courseName,
            int requiredDurationSeconds,
            boolean allowSeek,
            int progressReportIntervalSeconds,
            int studyToleranceSeconds,
            int sortOrder,
            List<PlanCoursewareSnapshotView> coursewares) implements Serializable {
    }

    public record PlanUserView(
            Long id,
            Long userId,
            Long orgId,
            String orgName,
            String username,
            String displayName,
            String assignmentStatus,
            String studyStatus,
            String examStatus,
            String completionStatus,
            LocalDateTime completedAt) implements Serializable {
    }

    public record StudentPlanCoursewareView(
            Long id,
            String title,
            int durationSeconds,
            int sortOrder) implements Serializable {
    }

    public record StudentPlanCourseView(
            Long id,
            String courseName,
            int requiredDurationSeconds,
            boolean allowSeek,
            int progressReportIntervalSeconds,
            int studyToleranceSeconds,
            int sortOrder,
            List<StudentPlanCoursewareView> coursewares) implements Serializable {
    }

    public record PlanView(
            Long id,
            String name,
            String description,
            LocalDateTime startAt,
            LocalDateTime endAt,
            String status,
            boolean examRequired,
            Long examPaperId,
            Integer examPassScore,
            Integer examDurationMinutes,
            boolean faceCheckEnabled,
            int faceCheckMinIntervalSeconds,
            int faceCheckMaxIntervalSeconds,
            int faceCheckTimeoutSeconds,
            int faceCheckMaxAttempts,
            List<PlanCourseView> courses,
            List<PlanUserView> users,
            LocalDateTime publishedAt,
            LocalDateTime cancelledAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) implements Serializable {
    }

    /** 批量查询任务要求学时，未开始学习时也取发布快照的完整要求。 */
    public record StudentPlanDurationView(Long taskId, Long planId,
            long requiredDurationMillis) implements Serializable { }

    public record StudentPlanView(
            Long taskId,
            Long planId,
            String name,
            String description,
            LocalDateTime startAt,
            LocalDateTime endAt,
            String status,
            String assignmentStatus,
            String studyStatus,
            String examStatus,
            String completionStatus,
            boolean examRequired,
            Integer examPassScore,
            Integer examDurationMinutes,
            boolean faceCheckEnabled,
            int faceCheckTimeoutSeconds,
            int faceCheckMaxAttempts,
            List<StudentPlanCourseView> courses,
            LocalDateTime publishedAt) implements Serializable {
    }
}
