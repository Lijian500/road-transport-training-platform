package me.lj.train.training.model.entity;

import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 视频课件实体。
 */
@Table("train_courseware")
public class CoursewareEntity extends TrainingAuditEntity {

    private Long enterpriseId;
    private Long courseId;
    private Long storageObjectId;
    private String coursewareTitle;
    private int durationSeconds;
    private int sortOrder;
    private Long deletedBy;
    private LocalDateTime deletedAt;

    public Long getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(Long enterpriseId) { this.enterpriseId = enterpriseId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getStorageObjectId() { return storageObjectId; }
    public void setStorageObjectId(Long storageObjectId) { this.storageObjectId = storageObjectId; }
    public String getCoursewareTitle() { return coursewareTitle; }
    public void setCoursewareTitle(String title) { this.coursewareTitle = title; }
    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public Long getDeletedBy() { return deletedBy; }
    public void setDeletedBy(Long deletedBy) { this.deletedBy = deletedBy; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
