package me.lj.train.api.training;

import java.util.List;
import me.lj.train.api.training.PlanModels.StudentPlanDurationView;
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

    /** 批量读取本人任务的要求学时，不初始化学习记录。 */
    Result<List<StudentPlanDurationView>> getMyPlanDurations(List<Long> planIds);
}
