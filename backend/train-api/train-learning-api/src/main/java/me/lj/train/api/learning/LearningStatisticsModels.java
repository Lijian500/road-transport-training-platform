package me.lj.train.api.learning;

import java.io.Serializable;
import java.util.List;

/**
 * 管理端有效学时统计RPC模型。
 */
public final class LearningStatisticsModels {

    private LearningStatisticsModels() {
    }

    /** 可按计划或指定任务集合汇总，任务集合用于分页明细聚合。 */
    public record LearningDurationQuery(
            Long planId,
            List<Long> taskIds) implements Serializable {
    }

    /** 单个培训任务的规定学时和有效学时。 */
    public record TaskDurationView(
            Long taskId,
            long requiredDurationMillis,
            long effectiveDurationMillis) implements Serializable {
    }

    /** 查询范围内的学时汇总。 */
    public record LearningDurationSummaryView(
            long requiredDurationMillis,
            long effectiveDurationMillis,
            List<TaskDurationView> tasks) implements Serializable {
    }
}
