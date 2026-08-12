package me.lj.train.webapi.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import me.lj.train.api.training.CourseModels.ChangeCourseStatusCommand;
import me.lj.train.api.training.CourseModels.CourseQuery;
import me.lj.train.api.training.CourseModels.CourseView;
import me.lj.train.api.training.CourseModels.CoursewareView;
import me.lj.train.api.training.CourseModels.CreateCourseCommand;
import me.lj.train.api.training.CourseModels.DeleteCoursewareCommand;
import me.lj.train.api.training.CourseModels.ReorderCoursewaresCommand;
import me.lj.train.api.training.CourseModels.UpdateCourseCommand;
import me.lj.train.api.training.CourseModels.UpdateCoursewareCommand;
import me.lj.train.api.training.CourseService;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.Result;
import me.lj.train.webapi.security.RequirePermission;
import me.lj.train.webapi.support.RpcResultSupport;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 企业课程与课件管理REST接口。
 */
@RestController
@RequestMapping("/api/training/courses")
public class CourseController {

    @DubboReference(check = false, timeout = 8000, retries = 0)
    private CourseService courseService;

    @GetMapping
    @RequirePermission("admin:course:view")
    public Result<PageResult<CourseView>> page(
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.ok(RpcResultSupport.unwrap(courseService.page(
                new CourseQuery(pageNumber, pageSize, keyword, status))));
    }

    @PostMapping
    @RequirePermission("admin:course:create")
    public Result<CourseView> create(@Valid @RequestBody CourseRequest request) {
        return Result.ok(RpcResultSupport.unwrap(courseService.create(new CreateCourseCommand(
                request.name(), request.description(), request.requiredDurationSeconds(),
                request.allowSeek(), request.progressReportIntervalSeconds(),
                request.studyToleranceSeconds()))));
    }

    @GetMapping("/{id}")
    @RequirePermission("admin:course:view")
    public Result<CourseView> get(@PathVariable Long id) {
        return Result.ok(RpcResultSupport.unwrap(courseService.get(id)));
    }

    @PutMapping("/{id}")
    @RequirePermission("admin:course:update")
    public Result<CourseView> update(
            @PathVariable Long id,
            @Valid @RequestBody CourseRequest request) {
        return Result.ok(RpcResultSupport.unwrap(courseService.update(new UpdateCourseCommand(
                id, request.name(), request.description(), request.requiredDurationSeconds(),
                request.allowSeek(), request.progressReportIntervalSeconds(),
                request.studyToleranceSeconds()))));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("admin:course:delete")
    public Result<?> delete(@PathVariable Long id) {
        RpcResultSupport.ensureSuccess(courseService.delete(id));
        return Result.ok();
    }

    @PatchMapping("/{id}/status")
    @RequirePermission("admin:course:status")
    public Result<CourseView> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody CourseStatusRequest request) {
        return Result.ok(RpcResultSupport.unwrap(courseService.changeStatus(
                new ChangeCourseStatusCommand(id, request.status()))));
    }

    @PutMapping("/{courseId}/coursewares/{id}")
    @RequirePermission("admin:courseware:manage")
    public Result<CoursewareView> updateCourseware(
            @PathVariable Long courseId,
            @PathVariable Long id,
            @Valid @RequestBody CoursewareRequest request) {
        return Result.ok(RpcResultSupport.unwrap(courseService.updateCourseware(
                new UpdateCoursewareCommand(courseId, id, request.title()))));
    }

    @DeleteMapping("/{courseId}/coursewares/{id}")
    @RequirePermission("admin:courseware:manage")
    public Result<?> deleteCourseware(@PathVariable Long courseId, @PathVariable Long id) {
        RpcResultSupport.ensureSuccess(courseService.deleteCourseware(
                new DeleteCoursewareCommand(courseId, id)));
        return Result.ok();
    }

    @PutMapping("/{courseId}/coursewares/order")
    @RequirePermission("admin:courseware:manage")
    public Result<?> reorderCoursewares(
            @PathVariable Long courseId,
            @Valid @RequestBody ReorderRequest request) {
        RpcResultSupport.ensureSuccess(courseService.reorderCoursewares(
                new ReorderCoursewaresCommand(courseId, request.coursewareIds())));
        return Result.ok();
    }

    @DeleteMapping("/{courseId}/cover")
    @RequirePermission("admin:courseware:manage")
    public Result<?> deleteCover(@PathVariable Long courseId) {
        RpcResultSupport.ensureSuccess(courseService.deleteCover(courseId));
        return Result.ok();
    }

    public record CourseRequest(
            @NotBlank(message = "课程名称不能为空") String name,
            String description,
            @Min(value = 1, message = "规定时长必须大于0") int requiredDurationSeconds,
            boolean allowSeek,
            @Min(value = 10, message = "进度上报间隔不能小于10秒")
            @Max(value = 30, message = "进度上报间隔不能超过30秒")
            int progressReportIntervalSeconds,
            @Min(value = 0, message = "学时误差不能小于0秒")
            @Max(value = 300, message = "学时误差不能超过300秒")
            int studyToleranceSeconds) {
    }

    public record CourseStatusRequest(
            @NotBlank(message = "状态不能为空") String status) {
    }

    public record CoursewareRequest(
            @NotBlank(message = "课件标题不能为空") String title) {
    }

    public record ReorderRequest(@NotNull(message = "课件排序不能为空") List<Long> coursewareIds) {
    }
}
