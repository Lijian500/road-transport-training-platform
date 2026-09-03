package me.lj.train.api.training;

import me.lj.train.common.core.page.PageRequest;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 题库、试卷和学员考试RPC模型。
 */
public final class ExamModels {

    private ExamModels() {
    }

    public record QuestionQuery(
            int pageNumber,
            int pageSize,
            String keyword,
            String questionType,
            String status) implements Serializable {

        public PageRequest toPageRequest() {
            return new PageRequest(pageNumber, pageSize);
        }
    }

    public record CreateQuestionCommand(
            String questionType,
            String content,
            List<String> options,
            String correctAnswer,
            String analysis) implements Serializable {
    }

    public record UpdateQuestionCommand(
            Long id,
            String questionType,
            String content,
            List<String> options,
            String correctAnswer,
            String analysis) implements Serializable {
    }

    public record QuestionView(
            Long id,
            String questionType,
            String content,
            List<String> options,
            String correctAnswer,
            String analysis,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) implements Serializable {
    }

    public record PaperQuery(
            int pageNumber,
            int pageSize,
            String keyword,
            String status) implements Serializable {

        public PageRequest toPageRequest() {
            return new PageRequest(pageNumber, pageSize);
        }
    }

    public record CreatePaperCommand(
            String name,
            String description,
            int durationMinutes,
            int passScore,
            int questionScore,
            List<Long> manualQuestionIds,
            int randomFillCount) implements Serializable {
    }

    public record UpdatePaperCommand(
            Long id,
            String name,
            String description,
            int durationMinutes,
            int passScore,
            int questionScore,
            List<Long> manualQuestionIds,
            int randomFillCount) implements Serializable {
    }

    public record PaperQuestionView(
            Long id,
            Long sourceQuestionId,
            String selectionType,
            String questionType,
            String content,
            List<String> options,
            String correctAnswer,
            String analysis,
            int score,
            int sortOrder) implements Serializable {
    }

    public record PaperView(
            Long id,
            String name,
            String description,
            int durationMinutes,
            int passScore,
            int questionScore,
            int manualQuestionCount,
            int randomQuestionCount,
            int totalScore,
            String status,
            List<PaperQuestionView> questions,
            LocalDateTime enabledAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) implements Serializable {
    }

    public record PaperOptionView(
            Long id,
            String name,
            int durationMinutes,
            int passScore,
            int totalScore,
            int questionCount) implements Serializable {
    }

    public record AnswerCommand(
            Long paperQuestionId,
            String answer) implements Serializable {
    }

    public record SaveAnswersCommand(
            Long recordId,
            List<AnswerCommand> answers) implements Serializable {
    }

    public record ExamQuestionView(
            Long paperQuestionId,
            String questionType,
            String content,
            List<String> options,
            int score,
            int sortOrder,
            String answer) implements Serializable {
    }

    public record ExamRecordView(
            Long id,
            Long taskId,
            Long planId,
            Long paperId,
            String paperName,
            String status,
            LocalDateTime startedAt,
            LocalDateTime deadlineAt,
            LocalDateTime submittedAt,
            int passScore,
            int totalScore,
            Integer score,
            Boolean passed,
            List<ExamQuestionView> questions) implements Serializable {
    }
}
