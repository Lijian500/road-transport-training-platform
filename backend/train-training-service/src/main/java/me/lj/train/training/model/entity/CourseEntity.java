package me.lj.train.training.model.entity;

import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 培训课程实体。
 */
@Table("train_course")
public class CourseEntity extends TrainingAuditEntity {

    private Long enterpriseId;
    private String courseName;
    private String description;
    private Long coverObjectId;
    private int requiredDurationSeconds;
    private boolean allowSeek;
    private int progressReportIntervalSeconds;
    private int studyToleranceSeconds;
    private String status;
    private boolean everEnabled;
    private Long deletedBy;
    private LocalDateTime deletedAt;

    public Long getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(Long enterpriseId) { this.enterpriseId = enterpriseId; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getCoverObjectId() { return coverObjectId; }
    public void setCoverObjectId(Long coverObjectId) { this.coverObjectId = coverObjectId; }
    public int getRequiredDurationSeconds() { return requiredDurationSeconds; }
    public void setRequiredDurationSeconds(int value) { this.requiredDurationSeconds = value; }
    public boolean isAllowSeek() { return allowSeek; }
    public void setAllowSeek(boolean allowSeek) { this.allowSeek = allowSeek; }
    public int getProgressReportIntervalSeconds() { return progressReportIntervalSeconds; }
    public void setProgressReportIntervalSeconds(int value) { this.progressReportIntervalSeconds = value; }
    public int getStudyToleranceSeconds() { return studyToleranceSeconds; }
    public void setStudyToleranceSeconds(int value) { this.studyToleranceSeconds = value; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isEverEnabled() { return everEnabled; }
    public void setEverEnabled(boolean everEnabled) { this.everEnabled = everEnabled; }
    public Long getDeletedBy() { return deletedBy; }
    public void setDeletedBy(Long deletedBy) { this.deletedBy = deletedBy; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
