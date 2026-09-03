package me.lj.train.webapi.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import me.lj.train.api.learning.FaceCheckModels.FaceCheckView;
import me.lj.train.api.learning.FaceCheckModels.SubmitFaceCheckCommand;
import me.lj.train.api.learning.FaceCheckService;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.webapi.security.RequirePermission;
import me.lj.train.webapi.support.RpcResultSupport;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

/**
 * 学员当前人脸抽验查询及候选照片提交接口。
 */
@RestController
@RequestMapping("/api/learning")
public class FaceCheckController {

    private static final String PERMISSION = "student:learning:study";
    private static final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");

    @DubboReference(check = false, timeout = 15000, retries = 0)
    private FaceCheckService faceCheckService;

    /** 查询学习会话当前待处理或最近完成的人脸抽验。 */
    @GetMapping("/sessions/{sessionId}/face-check")
    @RequirePermission(PERMISSION)
    public Result<FaceCheckView> current(@PathVariable Long sessionId) {
        return Result.ok(RpcResultSupport.unwrap(
                faceCheckService.getCurrentFaceCheck(sessionId)));
    }

    /** 通过multipart提交一次抽验照片，照片不进入WebSocket和业务日志。 */
    @PostMapping(
            value = "/face-checks/{taskId}/submissions",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission(PERMISSION)
    public Result<FaceCheckView> submit(
            @PathVariable Long taskId,
            @RequestParam
            @NotBlank(message = "请求ID不能为空")
            @Size(max = 64, message = "请求ID不能超过64个字符")
            String requestId,
            @RequestPart("photo") MultipartFile photo) {
        validatePhoto(photo);
        try {
            return Result.ok(RpcResultSupport.unwrap(faceCheckService.submitFaceCheck(
                    new SubmitFaceCheckCommand(taskId, requestId, photo.getBytes()))));
        } catch (IOException exception) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "抽验照片读取失败");
        }
    }

    /** 校验浏览器提交的图片声明，实际图片内容仍由人脸适配器解码复核。 */
    private void validatePhoto(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "请上传抽验照片");
        }
        if (photo.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "抽验照片不能超过5MB");
        }
        if (!IMAGE_CONTENT_TYPES.contains(photo.getContentType())) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "仅支持JPEG、PNG或WebP照片");
        }
    }
}
