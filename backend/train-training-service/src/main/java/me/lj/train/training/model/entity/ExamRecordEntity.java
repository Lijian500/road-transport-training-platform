package me.lj.train.training.model.entity;

import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/** 学员一次考试记录实体。 */
@Table("exam_record")
public class ExamRecordEntity extends TrainingAuditEntity {

    private Long enterpriseId;
    private Long taskId;
    private Long planId;
    private Long userId;
    private Long paperId;
    private String paperName;
    private int durationMinutes;
    private int passScore;
    private int totalScore;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime deadlineAt;
    private LocalDateTime submittedAt;
    private Integer score;
    private Boolean passed;

    public Long getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(Long value) { this.enterpriseId = value; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long value) { this.taskId = value; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long value) { this.planId = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { this.userId = value; }
    public Long getPaperId() { return paperId; }
    public void setPaperId(Long value) { this.paperId = value; }
    public String getPaperName() { return paperName; }
    public void setPaperName(String value) { this.paperName = value; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int value) { this.durationMinutes = value; }
    public int getPassScore() { return passScore; }
    public void setPassScore(int value) { this.passScore = value; }
    public int getTotalScore() { return totalScore; }
    public void setTotalScore(int value) { this.totalScore = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime value) { this.startedAt = value; }
    public LocalDateTime getDeadlineAt() { return deadlineAt; }
    public void setDeadlineAt(LocalDateTime value) { this.deadlineAt = value; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime value) { this.submittedAt = value; }
    public Integer getScore() { return score; }
    public void setScore(Integer value) { this.score = value; }
    public Boolean getPassed() { return passed; }
    public void setPassed(Boolean value) { this.passed = value; }
}
