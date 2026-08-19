package me.lj.train.training.model.entity;

import com.mybatisflex.annotation.Table;

/**
 * 培训计划课程规则快照实体。
 */
@Table("train_plan_course")
public class PlanCourseEntity extends TrainingAuditEntity {

    private Long enterpriseId;
    private Long planId;
    private Long courseId;
    private String courseName;
    private int requiredDurationSeconds;
    private boolean allowSeek;
    private int progressReportIntervalSeconds;
    private int studyToleranceSeconds;
    private int sortOrder;

    public Long getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(Long value) { this.enterpriseId = value; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long value) { this.planId = value; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long value) { this.courseId = value; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String value) { this.courseName = value; }
    public int getRequiredDurationSeconds() { return requiredDurationSeconds; }
    public void setRequiredDurationSeconds(int value) { this.requiredDurationSeconds = value; }
    public boolean isAllowSeek() { return allowSeek; }
    public void setAllowSeek(boolean value) { this.allowSeek = value; }
    public int getProgressReportIntervalSeconds() { return progressReportIntervalSeconds; }
    public void setProgressReportIntervalSeconds(int value) { this.progressReportIntervalSeconds = value; }
    public int getStudyToleranceSeconds() { return studyToleranceSeconds; }
    public void setStudyToleranceSeconds(int value) { this.studyToleranceSeconds = value; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int value) { this.sortOrder = value; }
}
