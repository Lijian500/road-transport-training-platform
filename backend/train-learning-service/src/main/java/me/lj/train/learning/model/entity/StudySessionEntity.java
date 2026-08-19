package me.lj.train.learning.model.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/** 在线学习会话实体。 */
@Table("study_session")
public class StudySessionEntity {

    @Id(keyType = KeyType.None)
    private Long id;
    private Long enterpriseId;
    private Long userId;
    private Long taskId;
    private Long planId;
    private Long planCourseId;
    private String clientInstanceId;
    private String courseName;
    private int sortOrder;
    private LocalDateTime planEndAt;
    private String status;
    private Long currentCoursewareSnapshotId;
    private long lastSequence;
    private long lastConfirmedPositionMs;
    private LocalDateTime lastEventAt;
    private LocalDateTime signedInAt;
    private LocalDateTime startedAt;
    private LocalDateTime pausedAt;
    private LocalDateTime completedAt;
    private LocalDateTime signedOutAt;
    private LocalDateTime terminatedAt;
    private String terminationReason;
    private int version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(Long value) { this.enterpriseId = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { this.userId = value; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long value) { this.taskId = value; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long value) { this.planId = value; }
    public Long getPlanCourseId() { return planCourseId; }
    public void setPlanCourseId(Long value) { this.planCourseId = value; }
    public String getClientInstanceId() { return clientInstanceId; }
    public void setClientInstanceId(String value) { this.clientInstanceId = value; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String value) { this.courseName = value; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int value) { this.sortOrder = value; }
    public LocalDateTime getPlanEndAt() { return planEndAt; }
    public void setPlanEndAt(LocalDateTime value) { this.planEndAt = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public Long getCurrentCoursewareSnapshotId() { return currentCoursewareSnapshotId; }
    public void setCurrentCoursewareSnapshotId(Long value) { this.currentCoursewareSnapshotId = value; }
    public long getLastSequence() { return lastSequence; }
    public void setLastSequence(long value) { this.lastSequence = value; }
    public long getLastConfirmedPositionMs() { return lastConfirmedPositionMs; }
    public void setLastConfirmedPositionMs(long value) { this.lastConfirmedPositionMs = value; }
    public LocalDateTime getLastEventAt() { return lastEventAt; }
    public void setLastEventAt(LocalDateTime value) { this.lastEventAt = value; }
    public LocalDateTime getSignedInAt() { return signedInAt; }
    public void setSignedInAt(LocalDateTime value) { this.signedInAt = value; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime value) { this.startedAt = value; }
    public LocalDateTime getPausedAt() { return pausedAt; }
    public void setPausedAt(LocalDateTime value) { this.pausedAt = value; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime value) { this.completedAt = value; }
    public LocalDateTime getSignedOutAt() { return signedOutAt; }
    public void setSignedOutAt(LocalDateTime value) { this.signedOutAt = value; }
    public LocalDateTime getTerminatedAt() { return terminatedAt; }
    public void setTerminatedAt(LocalDateTime value) { this.terminatedAt = value; }
    public String getTerminationReason() { return terminationReason; }
    public void setTerminationReason(String value) { this.terminationReason = value; }
    public int getVersion() { return version; }
    public void setVersion(int value) { this.version = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { this.updatedAt = value; }
}
