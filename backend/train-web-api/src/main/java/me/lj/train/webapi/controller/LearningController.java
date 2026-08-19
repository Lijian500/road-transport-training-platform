package me.lj.train.webapi.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import me.lj.train.api.learning.LearningModels.CourseProgressView;
import me.lj.train.api.learning.LearningModels.LearningEventResultView;
import me.lj.train.api.learning.LearningModels.LearningSessionView;
import me.lj.train.api.learning.LearningModels.OpenSessionCommand;
import me.lj.train.api.learning.LearningModels.PlanProgressView;
import me.lj.train.api.learning.LearningModels.PlaybackUrlCommand;
import me.lj.train.api.learning.LearningModels.SubmitEventCommand;
import me.lj.train.api.learning.LearningModels.TerminateSessionCommand;
import me.lj.train.api.learning.LearningSessionService;
import me.lj.train.api.training.StorageModels.SignedRequestView;
import me.lj.train.common.core.result.Result;
import me.lj.train.webapi.security.RequirePermission;
import me.lj.train.webapi.support.RpcResultSupport;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学员普通HTTP学习会话、状态机、进度及播放授权接口。
 */
@RestController
@RequestMapping("/api/learning")
public class LearningController {

    private static final String PERMISSION = "student:learning:study";

    @DubboReference(check = false, timeout = 10000, retries = 0)
    private LearningSessionService learningSessionService;

    @GetMapping("/plans/{planId}/progress")
    @RequirePermission(PERMISSION)
    public Result<PlanProgressView> planProgress(@PathVariable Long planId) {
        return Result.ok(RpcResultSupport.unwrap(
                learningSessionService.getPlanProgress(planId)));
    }

    @GetMapping("/plans/{planId}/courses/{planCourseId}")
    @RequirePermission(PERMISSION)
    public Result<CourseProgressView> course(
            @PathVariable Long planId,
            @PathVariable Long planCourseId) {
        return Result.ok(RpcResultSupport.unwrap(
                learningSessionService.getCourse(planId, planCourseId)));
    }

    @PostMapping("/sessions")
    @RequirePermission(PERMISSION)
    public Result<LearningSessionView> openSession(
            @Valid @RequestBody OpenSessionRequest request) {
        return Result.ok(RpcResultSupport.unwrap(learningSessionService.openSession(
                new OpenSessionCommand(
                        request.planId(), request.planCourseId(), request.clientInstanceId()))));
    }

    @GetMapping("/sessions/active")
    @RequirePermission(PERMISSION)
    public Result<LearningSessionView> activeSession() {
        return Result.ok(RpcResultSupport.unwrap(
                learningSessionService.getActiveSession()));
    }

    @GetMapping("/sessions/{id}")
    @RequirePermission(PERMISSION)
    public Result<LearningSessionView> session(@PathVariable Long id) {
        return Result.ok(RpcResultSupport.unwrap(learningSessionService.getSession(id)));
    }

    @PostMapping("/sessions/{id}/events")
    @RequirePermission(PERMISSION)
    public Result<LearningEventResultView> event(
            @PathVariable Long id,
            @Valid @RequestBody LearningEventRequest request) {
        return Result.ok(RpcResultSupport.unwrap(learningSessionService.submitEvent(
                new SubmitEventCommand(
                        id, request.clientInstanceId(), request.requestId(), request.sequence(),
                        request.eventType(), request.coursewareSnapshotId(),
                        request.videoPositionMillis()))));
    }

    @PostMapping("/sessions/{id}/terminate")
    @RequirePermission(PERMISSION)
    public Result<?> terminate(@PathVariable Long id) {
        RpcResultSupport.ensureSuccess(learningSessionService.terminateSession(
                new TerminateSessionCommand(id)));
        return Result.ok();
    }

    @GetMapping("/sessions/{id}/coursewares/{snapshotId}/play-url")
    @RequirePermission(PERMISSION)
    public Result<SignedRequestView> playbackUrl(
            @PathVariable Long id,
            @PathVariable Long snapshotId,
            @RequestParam @NotBlank @Size(max = 64) String clientInstanceId) {
        return Result.ok(RpcResultSupport.unwrap(
                learningSessionService.createPlaybackUrl(new PlaybackUrlCommand(
                        id, clientInstanceId, snapshotId))));
    }

    public record OpenSessionRequest(
            @NotNull(message = "培训计划不能为空") Long planId,
            @NotNull(message = "计划课程不能为空") Long planCourseId,
            @NotBlank(message = "客户端实例ID不能为空")
            @Size(max = 64, message = "客户端实例ID不能超过64个字符")
            String clientInstanceId) {
    }

    public record LearningEventRequest(
            @NotBlank(message = "客户端实例ID不能为空")
            @Size(max = 64, message = "客户端实例ID不能超过64个字符")
            String clientInstanceId,
            @NotBlank(message = "请求ID不能为空")
            @Size(max = 64, message = "请求ID不能超过64个字符")
            String requestId,
            @Min(value = 1, message = "事件序号必须大于0") long sequence,
            @NotBlank(message = "事件类型不能为空") String eventType,
            Long coursewareSnapshotId,
            @Min(value = 0, message = "视频位置不能小于0") long videoPositionMillis) {
    }
}
