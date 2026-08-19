package me.lj.train.training.model.entity;

import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 培训计划学员任务实体。
 */
@Table("train_plan_user")
public class PlanUserEntity extends TrainingAuditEntity {

    private Long enterpriseId;
    private Long planId;
    private Long userId;
    private Long orgId;
    private String orgName;
    private String username;
    private String displayName;
    private String assignmentStatus;
    private String studyStatus;
    private String examStatus;
    private String completionStatus;
    private LocalDateTime completedAt;

    public Long getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(Long value) { this.enterpriseId = value; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long value) { this.planId = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { this.userId = value; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long value) { this.orgId = value; }
    public String getOrgName() { return orgName; }
    public void setOrgName(String value) { this.orgName = value; }
    public String getUsername() { return username; }
    public void setUsername(String value) { this.username = value; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String value) { this.displayName = value; }
    public String getAssignmentStatus() { return assignmentStatus; }
    public void setAssignmentStatus(String value) { this.assignmentStatus = value; }
    public String getStudyStatus() { return studyStatus; }
    public void setStudyStatus(String value) { this.studyStatus = value; }
    public String getExamStatus() { return examStatus; }
    public void setExamStatus(String value) { this.examStatus = value; }
    public String getCompletionStatus() { return completionStatus; }
    public void setCompletionStatus(String value) { this.completionStatus = value; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime value) { this.completedAt = value; }
}
