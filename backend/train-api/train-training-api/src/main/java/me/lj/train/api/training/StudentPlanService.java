package me.lj.train.api.training;

import me.lj.train.api.training.PlanModels.StudentPlanQuery;
import me.lj.train.api.training.PlanModels.StudentPlanView;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.Result;

/**
 * 当前登录学员培训任务RPC接口。
 */
public interface StudentPlanService {

    Result<PageResult<StudentPlanView>> pageMyPlans(StudentPlanQuery query);

    Result<StudentPlanView> getMyPlan(Long planId);
}
