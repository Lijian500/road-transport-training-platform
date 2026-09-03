package me.lj.train.training.model.entity;

import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/** 试卷实体。 */
@Table("exam_paper")
public class ExamPaperEntity extends TrainingAuditEntity {

    private Long enterpriseId;
    private String paperName;
    private String description;
    private int durationMinutes;
    private int passScore;
    private int questionScore;
    private int manualQuestionCount;
    private int randomQuestionCount;
    private int totalScore;
    private String status;
    private Long enabledBy;
    private LocalDateTime enabledAt;
    private Long deletedBy;
    private LocalDateTime deletedAt;

    public Long getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(Long value) { this.enterpriseId = value; }
    public String getPaperName() { return paperName; }
    public void setPaperName(String value) { this.paperName = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { this.description = value; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int value) { this.durationMinutes = value; }
    public int getPassScore() { return passScore; }
    public void setPassScore(int value) { this.passScore = value; }
    public int getQuestionScore() { return questionScore; }
    public void setQuestionScore(int value) { this.questionScore = value; }
    public int getManualQuestionCount() { return manualQuestionCount; }
    public void setManualQuestionCount(int value) { this.manualQuestionCount = value; }
    public int getRandomQuestionCount() { return randomQuestionCount; }
    public void setRandomQuestionCount(int value) { this.randomQuestionCount = value; }
    public int getTotalScore() { return totalScore; }
    public void setTotalScore(int value) { this.totalScore = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public Long getEnabledBy() { return enabledBy; }
    public void setEnabledBy(Long value) { this.enabledBy = value; }
    public LocalDateTime getEnabledAt() { return enabledAt; }
    public void setEnabledAt(LocalDateTime value) { this.enabledAt = value; }
    public Long getDeletedBy() { return deletedBy; }
    public void setDeletedBy(Long value) { this.deletedBy = value; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime value) { this.deletedAt = value; }
}
