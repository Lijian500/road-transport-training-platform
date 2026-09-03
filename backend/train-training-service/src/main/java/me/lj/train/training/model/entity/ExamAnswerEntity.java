package me.lj.train.training.model.entity;

import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/** 学员答案实体。 */
@Table("exam_answer")
public class ExamAnswerEntity extends TrainingAuditEntity {

    private Long enterpriseId;
    private Long recordId;
    private Long paperQuestionId;
    private String answer;
    private Boolean correct;
    private Integer score;
    private LocalDateTime answeredAt;

    public Long getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(Long value) { this.enterpriseId = value; }
    public Long getRecordId() { return recordId; }
    public void setRecordId(Long value) { this.recordId = value; }
    public Long getPaperQuestionId() { return paperQuestionId; }
    public void setPaperQuestionId(Long value) { this.paperQuestionId = value; }
    public String getAnswer() { return answer; }
    public void setAnswer(String value) { this.answer = value; }
    public Boolean getCorrect() { return correct; }
    public void setCorrect(Boolean value) { this.correct = value; }
    public Integer getScore() { return score; }
    public void setScore(Integer value) { this.score = value; }
    public LocalDateTime getAnsweredAt() { return answeredAt; }
    public void setAnsweredAt(LocalDateTime value) { this.answeredAt = value; }
}
