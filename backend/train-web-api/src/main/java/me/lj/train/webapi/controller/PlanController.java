package me.lj.train.webapi.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import me.lj.train.api.training.PlanModels.CreatePlanCommand;
import me.lj.train.api.training.PlanModels.PlanCourseOptionView;
import me.lj.train.api.training.PlanModels.PlanParticipantOptionView;
import me.lj.train.api.training.PlanModels.PlanQuery;
import me.lj.train.api.training.PlanModels.PlanView;
import me.lj.train.api.training.PlanModels.UpdatePlanCommand;
import me.lj.train.api.training.PlanService;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.Result;
import me.lj.train.webapi.security.RequirePermission;
import me.lj.train.webapi.support.RpcResultSupport;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 企业培训计划管理REST接口。
 */
@RestController
@RequestMapping("/api/training/plans")
public class PlanController {

    @DubboReference(check = false, timeout = 10000, retries = 0)
    private PlanService planService;

    @GetMapping
    @RequirePermission("admin:plan:view")
    public Result<PageResult<PlanView>> page(
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.ok(RpcResultSupport.unwrap(
                planService.page(new PlanQuery(pageNumber, pageSize, keyword, status))));
    }

    @PostMapping
    @RequirePermission("admin:plan:create")
    public Result<PlanView> create(@Valid @RequestBody CreatePlanRequest request) {
        return Result.ok(RpcResultSupport.unwrap(planService.create(new CreatePlanCommand(
                request.name(), request.description(), request.startAt(), request.endAt(),
                request.examRequired()))));
    }

    @GetMapping("/course-candidates")
    @RequirePermission({"admin:plan:create", "admin:plan:update"})
    public Result<List<PlanCourseOptionView>> courseCandidates(
            @RequestParam(required = false) String keyword) {
        return Result.ok(RpcResultSupport.unwrap(planService.listCourseCandidates(keyword)));
    }

    @GetMapping("/participant-candidates")
    @RequirePermission({"admin:plan:create", "admin:plan:update"})
    public Result<List<PlanParticipantOptionView>> participantCandidates(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long orgId) {
        return Result.ok(RpcResultSupport.unwrap(
                planService.listParticipantCandidates(keyword, orgId)));
    }

    @GetMapping("/{id}")
    @RequirePermission("admin:plan:view")
    public Result<PlanView> get(@PathVariable Long id) {
        return Result.ok(RpcResultSupport.unwrap(planService.get(id)));
    }

    @PutMapping("/{id}")
    @RequirePermission("admin:plan:update")
    public Result<PlanView> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePlanRequest request) {
        return Result.ok(RpcResultSupport.unwrap(planService.update(new UpdatePlanCommand(
                id, request.name(), request.description(), request.startAt(), request.endAt(),
                request.examRequired(), request.courseIds(), request.userIds()))));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("admin:plan:update")
    public Result<?> delete(@PathVariable Long id) {
        RpcResultSupport.ensureSuccess(planService.delete(id));
        return Result.ok();
    }

    @PostMapping("/{id}/publish")
    @RequirePermission("admin:plan:publish")
    public Result<PlanView> publish(@PathVariable Long id) {
        return Result.ok(RpcResultSupport.unwrap(planService.publish(id)));
    }

    @PostMapping("/{id}/cancel")
    @RequirePermission("admin:plan:cancel")
    public Result<PlanView> cancel(@PathVariable Long id) {
        return Result.ok(RpcResultSupport.unwrap(planService.cancel(id)));
    }

    public record CreatePlanRequest(
            @NotBlank(message = "计划名称不能为空") String name,
            String description,
            @NotNull(message = "开始时间不能为空") LocalDateTime startAt,
            @NotNull(message = "结束时间不能为空") LocalDateTime endAt,
            boolean examRequired) {
    }

    public record UpdatePlanRequest(
            @NotBlank(message = "计划名称不能为空") String name,
            String description,
            @NotNull(message = "开始时间不能为空") LocalDateTime startAt,
            @NotNull(message = "结束时间不能为空") LocalDateTime endAt,
            boolean examRequired,
            List<Long> courseIds,
            List<Long> userIds) {
    }
}
