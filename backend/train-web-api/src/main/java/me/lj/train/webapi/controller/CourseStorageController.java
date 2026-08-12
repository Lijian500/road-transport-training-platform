package me.lj.train.webapi.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import me.lj.train.api.training.CourseStorageService;
import me.lj.train.api.training.StorageModels.CoursewarePreviewCommand;
import me.lj.train.api.training.StorageModels.CreateCoursewareUploadSessionCommand;
import me.lj.train.api.training.StorageModels.CreateCoverUploadSessionCommand;
import me.lj.train.api.training.StorageModels.CreatePartUrlsCommand;
import me.lj.train.api.training.StorageModels.SignedRequestView;
import me.lj.train.api.training.StorageModels.StorageCapabilityView;
import me.lj.train.api.training.StorageModels.UploadCompleteView;
import me.lj.train.api.training.StorageModels.UploadSessionView;
import me.lj.train.api.training.StorageModels.UploadedPartView;
import me.lj.train.common.core.result.Result;
import me.lj.train.webapi.security.RequirePermission;
import me.lj.train.webapi.support.RpcResultSupport;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 课程文件OSS直传与预览REST接口。
 */
@RestController
@RequestMapping("/api/training")
public class CourseStorageController {

    @DubboReference(check = false, timeout = 15000, retries = 0)
    private CourseStorageService storageService;

    @GetMapping("/storage/capability")
    @RequirePermission("admin:course:view")
    public Result<StorageCapabilityView> capability() {
        return Result.ok(RpcResultSupport.unwrap(storageService.capability()));
    }

    @PostMapping("/courses/{id}/cover/upload-sessions")
    @RequirePermission("admin:courseware:manage")
    public Result<UploadSessionView> createCoverUploadSession(
            @PathVariable Long id,
            @Valid @RequestBody FileRequest request) {
        return Result.ok(RpcResultSupport.unwrap(storageService.createCoverUploadSession(
                new CreateCoverUploadSessionCommand(
                        id, request.originalFilename(), request.contentType(),
                        request.fileSizeBytes(), request.clientLastModified()))));
    }

    @PostMapping("/courses/{id}/coursewares/upload-sessions")
    @RequirePermission("admin:courseware:manage")
    public Result<UploadSessionView> createCoursewareUploadSession(
            @PathVariable Long id,
            @Valid @RequestBody CoursewareFileRequest request) {
        return Result.ok(RpcResultSupport.unwrap(storageService.createCoursewareUploadSession(
                new CreateCoursewareUploadSessionCommand(
                        id, request.title(), request.originalFilename(), request.contentType(),
                        request.fileSizeBytes(), request.clientLastModified(),
                        request.durationSeconds()))));
    }

    @PostMapping("/upload-sessions/{id}/part-urls")
    @RequirePermission("admin:courseware:manage")
    public Result<List<SignedRequestView>> createPartUrls(
            @PathVariable Long id,
            @Valid @RequestBody PartUrlsRequest request) {
        return Result.ok(RpcResultSupport.unwrap(storageService.createPartUrls(
                new CreatePartUrlsCommand(id, request.partNumbers()))));
    }

    @GetMapping("/upload-sessions/{id}/parts")
    @RequirePermission("admin:courseware:manage")
    public Result<List<UploadedPartView>> listParts(@PathVariable Long id) {
        return Result.ok(RpcResultSupport.unwrap(storageService.listParts(id)));
    }

    @PostMapping("/upload-sessions/{id}/complete")
    @RequirePermission("admin:courseware:manage")
    public Result<UploadCompleteView> complete(@PathVariable Long id) {
        return Result.ok(RpcResultSupport.unwrap(storageService.complete(id)));
    }

    @DeleteMapping("/upload-sessions/{id}")
    @RequirePermission("admin:courseware:manage")
    public Result<?> cancel(@PathVariable Long id) {
        RpcResultSupport.ensureSuccess(storageService.cancel(id));
        return Result.ok();
    }

    @GetMapping("/courses/{id}/cover/preview-url")
    @RequirePermission("admin:course:view")
    public Result<SignedRequestView> coverPreviewUrl(@PathVariable Long id) {
        return Result.ok(RpcResultSupport.unwrap(storageService.coverPreviewUrl(id)));
    }

    @GetMapping("/courses/{courseId}/coursewares/{id}/preview-url")
    @RequirePermission("admin:course:view")
    public Result<SignedRequestView> coursewarePreviewUrl(
            @PathVariable Long courseId,
            @PathVariable Long id) {
        return Result.ok(RpcResultSupport.unwrap(storageService.coursewarePreviewUrl(
                new CoursewarePreviewCommand(courseId, id))));
    }

    public record FileRequest(
            @NotBlank(message = "原文件名不能为空") String originalFilename,
            @NotBlank(message = "文件类型不能为空") String contentType,
            @Min(value = 1, message = "文件大小必须大于0") long fileSizeBytes,
            Long clientLastModified) {
    }

    public record CoursewareFileRequest(
            @NotBlank(message = "课件标题不能为空") String title,
            @NotBlank(message = "原文件名不能为空") String originalFilename,
            @NotBlank(message = "文件类型不能为空") String contentType,
            @Min(value = 1, message = "文件大小必须大于0") long fileSizeBytes,
            Long clientLastModified,
            @Min(value = 1, message = "视频时长必须大于0")
            @Max(value = 86400, message = "视频时长不能超过24小时")
            int durationSeconds) {
    }

    public record PartUrlsRequest(
            @NotEmpty(message = "分片编号不能为空") List<Integer> partNumbers) {
    }
}
