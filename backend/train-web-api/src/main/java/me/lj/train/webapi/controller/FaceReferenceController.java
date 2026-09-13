package me.lj.train.webapi.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import me.lj.train.api.admin.FaceReferenceModels.BindFaceReferenceCommand;
import me.lj.train.api.admin.FaceReferenceModels.FaceReferenceChangeView;
import me.lj.train.api.admin.FaceReferenceModels.FaceReferenceView;
import me.lj.train.api.admin.FaceReferenceService;
import me.lj.train.api.learning.FaceCheckModels.FaceReferenceValidationView;
import me.lj.train.api.learning.FaceCheckService;
import me.lj.train.api.training.PrivateImageModels.CreateFaceReferenceUploadSessionCommand;
import me.lj.train.api.training.PrivateImageModels.PrivateImageContentView;
import me.lj.train.api.training.PrivateImageModels.PrivateImageUploadCompleteView;
import me.lj.train.api.training.PrivateImageModels.PrivateImageUploadSessionView;
import me.lj.train.api.training.PrivateImageStorageService;
import me.lj.train.api.training.StorageModels.SignedRequestView;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.webapi.support.RpcResultSupport;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 本人或有权管理员维护登记照；RPC服务逐层校验用户及对象归属。
 */
@RestController
@RequestMapping("/api/admin/users/{userId}/face-reference")
public class FaceReferenceController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FaceReferenceController.class);

    @DubboReference(check = false, timeout = 10000, retries = 0)
    private FaceReferenceService faceReferenceService;

    @DubboReference(check = false, timeout = 15000, retries = 0)
    private PrivateImageStorageService privateImageStorageService;

    @DubboReference(check = false, timeout = 15000, retries = 0)
    private FaceCheckService faceCheckService;

    /** 查询用户当前登记状态。 */
    @GetMapping
    public Result<FaceReferenceResponse> get(@PathVariable Long userId) {
        return Result.ok(toResponse(RpcResultSupport.unwrap(faceReferenceService.get(userId))));
    }

    /** 创建登记照私有OSS直传会话。 */
    @PostMapping("/upload-sessions")
    public Result<PrivateImageUploadSessionView> createUploadSession(
            @PathVariable Long userId,
            @Valid @RequestBody FaceReferenceFileRequest request) {
        return Result.ok(RpcResultSupport.unwrap(
                privateImageStorageService.createFaceReferenceUploadSession(
                        new CreateFaceReferenceUploadSessionCommand(
                                userId, request.originalFilename(), request.contentType(),
                                request.fileSizeBytes(), request.clientLastModified()))));
    }

    /**
     * 完成OSS上传，确认图片中只有一张人脸后再原子替换用户登记照指针。
     */
    @PostMapping("/upload-sessions/{sessionId}/complete")
    public Result<FaceReferenceResponse> completeUploadSession(
            @PathVariable Long userId,
            @PathVariable Long sessionId) {
        PrivateImageUploadCompleteView completed = RpcResultSupport.unwrap(
                privateImageStorageService.completeFaceReferenceUpload(sessionId));
        if (!userId.equals(completed.userId())) {
            safeDelete(completed.storageObjectId());
            throw new BusinessException(AppErrorCode.DATA_SCOPE_VIOLATION);
        }
        try {
            PrivateImageContentView content = RpcResultSupport.unwrap(
                    privateImageStorageService.read(completed.storageObjectId()));
            FaceReferenceValidationView validation = RpcResultSupport.unwrap(
                    faceCheckService.validateReference(content.content()));
            if (!validation.valid()) {
                throw new BusinessException(AppErrorCode.PARAM_INVALID, validation.message());
            }
            FaceReferenceChangeView changed = RpcResultSupport.unwrap(
                    faceReferenceService.bind(new BindFaceReferenceCommand(
                            userId, completed.storageObjectId())));
            if (changed.previousStorageObjectId() != null
                    && !changed.previousStorageObjectId().equals(completed.storageObjectId())) {
                safeDelete(changed.previousStorageObjectId());
            }
            return Result.ok(toResponse(changed.current()));
        } catch (RuntimeException exception) {
            safeDelete(completed.storageObjectId());
            throw exception;
        }
    }

    /** 获取登记照短期预览地址。 */
    @GetMapping("/preview-url")
    public Result<SignedRequestView> previewUrl(@PathVariable Long userId) {
        FaceReferenceView reference = RpcResultSupport.unwrap(faceReferenceService.get(userId));
        if (!reference.enrolled() || reference.storageObjectId() == null) {
            throw new BusinessException(AppErrorCode.RESOURCE_NOT_FOUND, "用户尚未登记人脸照片");
        }
        return Result.ok(RpcResultSupport.unwrap(
                privateImageStorageService.previewUrl(reference.storageObjectId())));
    }

    /** 删除当前登记照，并将原OSS对象交给存储服务清理。 */
    @DeleteMapping
    public Result<?> remove(@PathVariable Long userId) {
        FaceReferenceChangeView changed = RpcResultSupport.unwrap(
                faceReferenceService.remove(userId));
        if (changed.previousStorageObjectId() != null) {
            safeDelete(changed.previousStorageObjectId());
        }
        return Result.ok();
    }

    /** 对象清理失败不回滚已经完成的人脸元数据变更，由存储清理任务继续补偿。 */
    private void safeDelete(Long storageObjectId) {
        if (storageObjectId == null) {
            return;
        }
        try {
            RpcResultSupport.ensureSuccess(privateImageStorageService.delete(storageObjectId));
        } catch (RuntimeException exception) {
            LOGGER.warn("登记照OSS对象清理失败，storageObjectId={}", storageObjectId, exception);
        }
    }

    private FaceReferenceResponse toResponse(FaceReferenceView view) {
        return new FaceReferenceResponse(view.userId(), view.enrolled(), view.updatedAt());
    }

    public record FaceReferenceFileRequest(
            @NotBlank(message = "原文件名不能为空") String originalFilename,
            @NotBlank(message = "文件类型不能为空") String contentType,
            @Min(value = 1, message = "文件大小必须大于0") long fileSizeBytes,
            Long clientLastModified) {
    }

    public record FaceReferenceResponse(
            Long userId,
            boolean enrolled,
            LocalDateTime updatedAt) {
    }
}
