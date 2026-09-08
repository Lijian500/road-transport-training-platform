package me.lj.train.api.training;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 学员档案和首页的只读契约，不触发学习或考试状态变更。 */
public final class TrainingRecordModels {
    private TrainingRecordModels() { }

    /** 日期范围按培训开始日期筛选，首尾日期均包含。 */
    public record RecordQuery(int pageNumber, int pageSize, String keyword,
            String completionStatus, LocalDate fromDate, LocalDate toDate,
            String activity) implements Serializable { }

    /** 发布时冻结的课程信息，未开始学习时仍能展示规定学时。 */
    public record RecordCourseView(Long planCourseId, String courseName,
            long requiredDurationMillis, int sortOrder) implements Serializable { }

    /** 培训任务及已落库成绩；课程快照仅在详情接口返回。 */
    public record TrainingRecordView(Long taskId, Long planId, Long userId,
            String displayName, String planName, String planStatus,
            LocalDateTime startAt, LocalDateTime endAt, String studyStatus,
            String examStatus, String completionStatus, Integer examScore,
            Boolean examPassed, LocalDateTime completedAt, long requiredDurationMillis,
            List<RecordCourseView> courses) implements Serializable { }

    /** 待办仅包含当前有效期内尚未完成的任务。 */
    public record StudentOverviewView(long totalCount, long toStudyCount,
            long toExamCount, long completedCount,
            List<TrainingRecordView> recentTasks) implements Serializable { }
}
