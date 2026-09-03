package me.lj.train.api.training;

import me.lj.train.common.core.page.PageRequest;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 培训计划、参训任务和考试结果统计RPC模型。
 */
public final class TrainingStatisticsModels {

    private TrainingStatisticsModels() {
    }

    /** 管理端计划统计分页条件。 */
    public record PlanStatisticsQuery(
            int pageNumber,
            int pageSize,
            String keyword,
            String status) implements Serializable {

        public PageRequest toPageRequest() {
            return new PageRequest(pageNumber, pageSize);
        }
    }

    /** 管理端学员培训明细分页条件。 */
    public record ParticipantStatisticsQuery(
            int pageNumber,
            int pageSize,
            Long planId,
            String keyword,
            String completionStatus) implements Serializable {

        public PageRequest toPageRequest() {
            return new PageRequest(pageNumber, pageSize);
        }
    }

    /** 培训统计总览，完成率使用百分数表达。 */
    public record StatisticsOverviewView(
            long planCount,
            long participantCount,
            long startedCount,
            long studyCompletedCount,
            long examPassedCount,
            long completedCount,
            BigDecimal completionRate,
            long requiredDurationMillis) implements Serializable {
    }

    /** 单个计划的任务状态统计。 */
    public record PlanStatisticsView(
            Long planId,
            String planName,
            String status,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean examRequired,
            long participantCount,
            long startedCount,
            long studyCompletedCount,
            long examPassedCount,
            long completedCount,
            BigDecimal completionRate) implements Serializable {
    }

    /** 学员培训状态、考试成绩明细。 */
    public record ParticipantStatisticsView(
            Long taskId,
            Long planId,
            String planName,
            Long userId,
            Long orgId,
            String orgName,
            String username,
            String displayName,
            String studyStatus,
            String examStatus,
            String completionStatus,
            Integer examScore,
            Boolean examPassed,
            long requiredDurationMillis,
            LocalDateTime completedAt) implements Serializable {
    }
}
