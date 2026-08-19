package me.lj.train.learning.model.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/** 计划课程有效学时汇总实体。 */
@Table("study_progress")
public class StudyProgressEntity {

    @Id(keyType = KeyType.None)
    private Long id;
    private Long enterpriseId;
    private Long userId;
    private Long taskId;
    private Long planId;
    private Long planCourseId;
    private String courseName;
    private int sortOrder;
    private long requiredDurationMs;
    private long effectiveDurationMs;
    private boolean allowSeek;
    private int progressReportIntervalSeconds;
    private int studyToleranceSeconds;
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
    public String getCourseName() { return courseName; }
    public void setCourseName(String value) { this.courseName = value; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int value) { this.sortOrder = value; }
    public long getRequiredDurationMs() { return requiredDurationMs; }
    public void setRequiredDurationMs(long value) { this.requiredDurationMs = value; }
    public long getEffectiveDurationMs() { return effectiveDurationMs; }
    public void setEffectiveDurationMs(long value) { this.effectiveDurationMs = value; }
    public boolean isAllowSeek() { return allowSeek; }
    public void setAllowSeek(boolean value) { this.allowSeek = value; }
    public int getProgressReportIntervalSeconds() { return progressReportIntervalSeconds; }
    public void setProgressReportIntervalSeconds(int value) { this.progressReportIntervalSeconds = value; }
    public int getStudyToleranceSeconds() { return studyToleranceSeconds; }
    public void setStudyToleranceSeconds(int value) { this.studyToleranceSeconds = value; }
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
