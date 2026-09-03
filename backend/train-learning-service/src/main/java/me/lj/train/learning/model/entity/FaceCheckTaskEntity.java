package me.lj.train.learning.model.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/** 人脸抽验任务实体。 */
@Table("face_check_task")
public class FaceCheckTaskEntity {

    @Id(keyType = KeyType.None)
    private Long id;
    private Long enterpriseId;
    private Long userId;
    private Long sessionId;
    private Long trainingTaskId;
    private Long planId;
    private Long planCourseId;
    private String status;
    private LocalDateTime triggeredAt;
    private LocalDateTime deadlineAt;
    private int attemptCount;
    private int maxAttempts;
    private String result;
    private String failureReason;
    private Double similarity;
    private LocalDateTime completedAt;
    private int version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(Long value) { this.enterpriseId = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { this.userId = value; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long value) { this.sessionId = value; }
    public Long getTrainingTaskId() { return trainingTaskId; }
    public void setTrainingTaskId(Long value) { this.trainingTaskId = value; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long value) { this.planId = value; }
    public Long getPlanCourseId() { return planCourseId; }
    public void setPlanCourseId(Long value) { this.planCourseId = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public LocalDateTime getTriggeredAt() { return triggeredAt; }
    public void setTriggeredAt(LocalDateTime value) { this.triggeredAt = value; }
    public LocalDateTime getDeadlineAt() { return deadlineAt; }
    public void setDeadlineAt(LocalDateTime value) { this.deadlineAt = value; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int value) { this.attemptCount = value; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int value) { this.maxAttempts = value; }
    public String getResult() { return result; }
    public void setResult(String value) { this.result = value; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String value) { this.failureReason = value; }
    public Double getSimilarity() { return similarity; }
    public void setSimilarity(Double value) { this.similarity = value; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime value) { this.completedAt = value; }
    public int getVersion() { return version; }
    public void setVersion(int value) { this.version = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { this.updatedAt = value; }
}
