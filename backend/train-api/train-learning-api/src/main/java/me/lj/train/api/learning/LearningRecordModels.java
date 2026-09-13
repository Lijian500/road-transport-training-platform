package me.lj.train.api.learning;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/** 学习档案只读视图，照片仅返回短期预览地址，不暴露图片摘要或幂等响应原文。 */
public final class LearningRecordModels {
    private LearningRecordModels() { }

    /** 按任务查询会话；时间范围按会话创建时间筛选。 */
    public record SessionQuery(int pageNumber, int pageSize, Long taskId,
            String status, LocalDateTime fromTime, LocalDateTime toTime) implements Serializable { }
    /** 已生成的课程学习进度。 */
    public record CourseRecordView(Long planCourseId, String courseName,
            long requiredDurationMillis, long effectiveDurationMillis,
            String status, LocalDateTime completedAt) implements Serializable { }
    /** 学习会话及终止原因。 */
    public record SessionRecordView(Long id, Long taskId, Long planId,
            Long planCourseId, String courseName, String status,
            LocalDateTime createdAt, LocalDateTime signedInAt,
            LocalDateTime startedAt, LocalDateTime signedOutAt,
            LocalDateTime terminatedAt, String terminationReason,
            String signInPhotoUrl, String signOutPhotoUrl) implements Serializable { }
    /** 已受理学习事件及本次计入的有效学时。 */
    public record EventRecordView(Long id, String requestId, long sequence,
            String eventType, String fromStatus, String toStatus,
            long reportedPositionMillis, long confirmedPositionMillis,
            long creditedDurationMillis, String resultCode,
            LocalDateTime serverTime) implements Serializable { }
    /** 单次抽验提交记录。 */
    public record FaceAttemptView(int attemptNo, String result, String failureReason,
            Double similarity, long elapsedMillis, LocalDateTime createdAt, String photoUrl) implements Serializable { }
    /** 以任务为主展示抽验，未提交照片的超时任务也会返回。 */
    public record FaceRecordView(Long id, String status, LocalDateTime triggeredAt,
            LocalDateTime deadlineAt, LocalDateTime completedAt, String failureReason,
            int attemptCount, List<FaceAttemptView> attempts) implements Serializable { }
}
