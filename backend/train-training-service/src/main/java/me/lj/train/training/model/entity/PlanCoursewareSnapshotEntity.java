package me.lj.train.training.model.entity;

import com.mybatisflex.annotation.Table;

/**
 * 培训计划发布时的课件清单快照实体。
 */
@Table("train_plan_courseware_snapshot")
public class PlanCoursewareSnapshotEntity extends TrainingAuditEntity {

    private Long enterpriseId;
    private Long planId;
    private Long planCourseId;
    private Long courseId;
    private Long sourceCoursewareId;
    private Long storageObjectId;
    private String coursewareTitle;
    private int durationSeconds;
    private int sortOrder;

    public Long getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(Long value) { this.enterpriseId = value; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long value) { this.planId = value; }
    public Long getPlanCourseId() { return planCourseId; }
    public void setPlanCourseId(Long value) { this.planCourseId = value; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long value) { this.courseId = value; }
    public Long getSourceCoursewareId() { return sourceCoursewareId; }
    public void setSourceCoursewareId(Long value) { this.sourceCoursewareId = value; }
    public Long getStorageObjectId() { return storageObjectId; }
    public void setStorageObjectId(Long value) { this.storageObjectId = value; }
    public String getCoursewareTitle() { return coursewareTitle; }
    public void setCoursewareTitle(String value) { this.coursewareTitle = value; }
    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int value) { this.durationSeconds = value; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int value) { this.sortOrder = value; }
}
