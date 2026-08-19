package me.lj.train.api.training;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 学员学习资格、课程规则和播放授权模型。
 */
public final class LearningAccessModels {

    private LearningAccessModels() {
    }

    public record LearningTaskQuery(Long planId) implements Serializable {
    }

    public record LearningPlaybackCommand(
            Long taskId,
            Long planId,
            Long planCourseId,
            Long coursewareSnapshotId) implements Serializable {
    }

    public record LearningCoursewareRuleView(
            Long id,
            String title,
            int durationSeconds,
            int sortOrder) implements Serializable {
    }

    public record LearningCourseRuleView(
            Long id,
            Long sourceCourseId,
            String courseName,
            int requiredDurationSeconds,
            boolean allowSeek,
            int progressReportIntervalSeconds,
            int studyToleranceSeconds,
            int sortOrder,
            List<LearningCoursewareRuleView> coursewares) implements Serializable {
    }

    public record LearningTaskContextView(
            Long taskId,
            Long planId,
            String planName,
            LocalDateTime startAt,
            LocalDateTime endAt,
            String planStatus,
            String assignmentStatus,
            String taskStudyStatus,
            String taskCompletionStatus,
            List<LearningCourseRuleView> courses) implements Serializable {
    }
}
