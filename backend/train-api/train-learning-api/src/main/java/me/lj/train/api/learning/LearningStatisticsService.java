package me.lj.train.api.learning;

import me.lj.train.api.learning.LearningStatisticsModels.LearningDurationQuery;
import me.lj.train.api.learning.LearningStatisticsModels.LearningDurationSummaryView;
import me.lj.train.common.core.result.Result;

/**
 * 学习服务有效学时统计RPC。
 */
public interface LearningStatisticsService {

    Result<LearningDurationSummaryView> summarize(LearningDurationQuery query);
}
