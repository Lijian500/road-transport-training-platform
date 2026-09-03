package me.lj.train.api.training;

import me.lj.train.api.training.TrainingStatisticsModels.ParticipantStatisticsQuery;
import me.lj.train.api.training.TrainingStatisticsModels.ParticipantStatisticsView;
import me.lj.train.api.training.TrainingStatisticsModels.PlanStatisticsQuery;
import me.lj.train.api.training.TrainingStatisticsModels.PlanStatisticsView;
import me.lj.train.api.training.TrainingStatisticsModels.StatisticsOverviewView;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.Result;

/**
 * 企业培训状态和考试结果统计RPC。
 */
public interface TrainingStatisticsService {

    Result<StatisticsOverviewView> overview(Long planId);

    Result<PageResult<PlanStatisticsView>> pagePlans(PlanStatisticsQuery query);

    Result<PageResult<ParticipantStatisticsView>> pageParticipants(
            ParticipantStatisticsQuery query);
}
