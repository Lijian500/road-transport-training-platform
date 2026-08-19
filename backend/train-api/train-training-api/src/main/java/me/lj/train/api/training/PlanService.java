package me.lj.train.api.training;

import me.lj.train.api.training.PlanModels.CreatePlanCommand;
import me.lj.train.api.training.PlanModels.PlanCourseOptionView;
import me.lj.train.api.training.PlanModels.PlanParticipantOptionView;
import me.lj.train.api.training.PlanModels.PlanQuery;
import me.lj.train.api.training.PlanModels.PlanView;
import me.lj.train.api.training.PlanModels.UpdatePlanCommand;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.Result;

import java.util.List;

/**
 * 企业培训计划管理RPC接口。
 */
public interface PlanService {

    Result<PageResult<PlanView>> page(PlanQuery query);

    Result<PlanView> create(CreatePlanCommand command);

    Result<PlanView> get(Long id);

    Result<PlanView> update(UpdatePlanCommand command);

    Result<?> delete(Long id);

    Result<PlanView> publish(Long id);

    Result<PlanView> cancel(Long id);

    Result<List<PlanCourseOptionView>> listCourseCandidates(String keyword);

    Result<List<PlanParticipantOptionView>> listParticipantCandidates(String keyword, Long orgId);
}
