package me.lj.train.training.model.entity;

import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 企业培训计划实体。
 */
@Table("train_plan")
public class PlanEntity extends TrainingAuditEntity {

    private Long enterpriseId;
    private String planName;
    private String description;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String status;
    private boolean examRequired;
    private Long examPaperId;
    private Integer examPassScore;
    private Long publishedBy;
    private LocalDateTime publishedAt;
    private Long cancelledBy;
    private LocalDateTime cancelledAt;
    private Long deletedBy;
    private LocalDateTime deletedAt;

    public Long getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(Long value) { this.enterpriseId = value; }
    public String getPlanName() { return planName; }
    public void setPlanName(String value) { this.planName = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { this.description = value; }
    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime value) { this.startAt = value; }
    public LocalDateTime getEndAt() { return endAt; }
    public void setEndAt(LocalDateTime value) { this.endAt = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public boolean isExamRequired() { return examRequired; }
    public void setExamRequired(boolean value) { this.examRequired = value; }
    public Long getExamPaperId() { return examPaperId; }
    public void setExamPaperId(Long value) { this.examPaperId = value; }
    public Integer getExamPassScore() { return examPassScore; }
    public void setExamPassScore(Integer value) { this.examPassScore = value; }
    public Long getPublishedBy() { return publishedBy; }
    public void setPublishedBy(Long value) { this.publishedBy = value; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime value) { this.publishedAt = value; }
    public Long getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(Long value) { this.cancelledBy = value; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime value) { this.cancelledAt = value; }
    public Long getDeletedBy() { return deletedBy; }
    public void setDeletedBy(Long value) { this.deletedBy = value; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime value) { this.deletedAt = value; }
}
