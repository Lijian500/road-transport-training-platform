package me.lj.train.training.model.entity;

import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/** 题库题目实体。 */
@Table("exam_question")
public class ExamQuestionEntity extends TrainingAuditEntity {

    private Long enterpriseId;
    private String questionType;
    private String content;
    private String optionsJson;
    private String correctAnswer;
    private String analysis;
    private String status;
    private Long deletedBy;
    private LocalDateTime deletedAt;

    public Long getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(Long value) { this.enterpriseId = value; }
    public String getQuestionType() { return questionType; }
    public void setQuestionType(String value) { this.questionType = value; }
    public String getContent() { return content; }
    public void setContent(String value) { this.content = value; }
    public String getOptionsJson() { return optionsJson; }
    public void setOptionsJson(String value) { this.optionsJson = value; }
    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String value) { this.correctAnswer = value; }
    public String getAnalysis() { return analysis; }
    public void setAnalysis(String value) { this.analysis = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public Long getDeletedBy() { return deletedBy; }
    public void setDeletedBy(Long value) { this.deletedBy = value; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime value) { this.deletedAt = value; }
}
