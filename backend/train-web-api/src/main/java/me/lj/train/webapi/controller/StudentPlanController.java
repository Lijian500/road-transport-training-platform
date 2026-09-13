package me.lj.train.webapi.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import me.lj.train.api.learning.LearningRecordService;
import me.lj.train.api.learning.LearningStatisticsModels.TaskDurationView;
import me.lj.train.api.training.PlanModels.StudentPlanDurationView;
import me.lj.train.api.training.PlanModels.StudentPlanQuery;
import me.lj.train.api.training.PlanModels.StudentPlanView;
import me.lj.train.api.training.StudentPlanService;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.Result;
import me.lj.train.webapi.security.RequirePermission;
import me.lj.train.webapi.support.RpcResultSupport;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前登录学员培训任务REST接口。
 */
@RestController
@RequestMapping("/api/training/student/plans")
public class StudentPlanController {

    @DubboReference(check = false, timeout = 8000, retries = 0)
    private StudentPlanService studentPlanService;

    @DubboReference(check = false, timeout = 10000, retries = 0)
    private LearningRecordService learningRecordService;

    /** 先限定本人计划范围，再一次性读取有效学时，缺少进度记录按零处理。 */
    @GetMapping("/progress")
    @RequirePermission("student:plan:view")
    public Result<List<StudentPlanProgressView>> progress(@RequestParam List<Long> planIds) {
        List<StudentPlanDurationView> plans = RpcResultSupport.unwrap(
                studentPlanService.getMyPlanDurations(planIds));
        if (plans.isEmpty()) return Result.ok(List.of());
        Map<Long, Long> effective = RpcResultSupport.unwrap(learningRecordService.myDurations(
                plans.stream().map(StudentPlanDurationView::taskId).toList())).stream()
                .collect(Collectors.toMap(TaskDurationView::taskId, TaskDurationView::effectiveDurationMillis));
        return Result.ok(plans.stream().map(plan -> new StudentPlanProgressView(plan.planId(),
                plan.requiredDurationMillis(), effective.getOrDefault(plan.taskId(), 0L))).toList());
    }

    /** 学员任务列表的汇总进度，不返回逐视频明细。 */
    public record StudentPlanProgressView(Long planId, long requiredDurationMillis,
            long effectiveDurationMillis) { }

    @GetMapping
    @RequirePermission("student:plan:view")
    public Result<PageResult<StudentPlanView>> page(
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status) {
        return Result.ok(RpcResultSupport.unwrap(studentPlanService.pageMyPlans(
                new StudentPlanQuery(pageNumber, pageSize, status))));
    }

    @GetMapping("/{id}")
    @RequirePermission("student:plan:view")
    public Result<StudentPlanView> get(@PathVariable Long id) {
        return Result.ok(RpcResultSupport.unwrap(studentPlanService.getMyPlan(id)));
    }
}
