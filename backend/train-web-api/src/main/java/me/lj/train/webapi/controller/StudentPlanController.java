package me.lj.train.webapi.controller;

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
