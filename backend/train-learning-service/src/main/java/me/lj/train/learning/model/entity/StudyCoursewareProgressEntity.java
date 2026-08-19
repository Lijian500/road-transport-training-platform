package me.lj.train.learning.model.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/** 逐课件播放进度实体。 */
@Table("study_courseware_progress")
public class StudyCoursewareProgressEntity {

    @Id(keyType = KeyType.None)
    private Long id;
    private Long enterpriseId;
    private Long userId;
    private Long taskId;
    private Long planId;
    private Long planCourseId;
    private Long coursewareSnapshotId;
    private String coursewareTitle;
    private int sortOrder;
    private long durationMs;
    private long confirmedPositionMs;
    private long maxConfirmedPositionMs;
    private String status;
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
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long value) { this.taskId = value; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long value) { this.planId = value; }
    public Long getPlanCourseId() { return planCourseId; }
    public void setPlanCourseId(Long value) { this.planCourseId = value; }
    public Long getCoursewareSnapshotId() { return coursewareSnapshotId; }
    public void setCoursewareSnapshotId(Long value) { this.coursewareSnapshotId = value; }
    public String getCoursewareTitle() { return coursewareTitle; }
    public void setCoursewareTitle(String value) { this.coursewareTitle = value; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int value) { this.sortOrder = value; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long value) { this.durationMs = value; }
    public long getConfirmedPositionMs() { return confirmedPositionMs; }
    public void setConfirmedPositionMs(long value) { this.confirmedPositionMs = value; }
    public long getMaxConfirmedPositionMs() { return maxConfirmedPositionMs; }
    public void setMaxConfirmedPositionMs(long value) { this.maxConfirmedPositionMs = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime value) { this.completedAt = value; }
    public int getVersion() { return version; }
    public void setVersion(int value) { this.version = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { this.updatedAt = value; }
}
