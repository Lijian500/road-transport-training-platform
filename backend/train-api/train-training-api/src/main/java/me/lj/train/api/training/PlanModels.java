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
            boolean examRequired) implements Serializable {
    }

    public record UpdatePlanCommand(
            Long id,
            String name,
            String description,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean examRequired,
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
            String displayName) implements Serializable {
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
            Integer examPassScore,
            List<PlanCourseView> courses,
            List<PlanUserView> users,
            LocalDateTime publishedAt,
            LocalDateTime cancelledAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) implements Serializable {
    }

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
            List<StudentPlanCourseView> courses,
            LocalDateTime publishedAt) implements Serializable {
    }
}
