package me.lj.train.training.model.entity;

import com.mybatisflex.annotation.Table;

/** 试卷题目及答案快照实体。 */
@Table("exam_paper_question")
public class ExamPaperQuestionEntity extends TrainingAuditEntity {

    private Long enterpriseId;
    private Long paperId;
    private Long sourceQuestionId;
    private String selectionType;
    private String questionType;
    private String content;
    private String optionsJson;
    private String correctAnswer;
    private String analysis;
    private int score;
    private int sortOrder;

    public Long getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(Long value) { this.enterpriseId = value; }
    public Long getPaperId() { return paperId; }
    public void setPaperId(Long value) { this.paperId = value; }
    public Long getSourceQuestionId() { return sourceQuestionId; }
    public void setSourceQuestionId(Long value) { this.sourceQuestionId = value; }
    public String getSelectionType() { return selectionType; }
    public void setSelectionType(String value) { this.selectionType = value; }
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
    public int getScore() { return score; }
    public void setScore(int value) { this.score = value; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int value) { this.sortOrder = value; }
}
