package me.lj.train.learning.support;

import me.lj.train.api.admin.FaceReferenceModels.FaceReferenceView;
import me.lj.train.api.admin.FaceReferenceService;
import me.lj.train.api.training.PrivateImageModels.PrivateImageContentView;
import me.lj.train.api.training.PrivateImageStorageService;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

/**
 * 按当前学员身份读取登记照，学习服务仅保存照片对象引用。
 */
@Component
public class FaceReferenceImageClient {

    @DubboReference(check = false, timeout = 10000, retries = 0)
    private FaceReferenceService faceReferenceService;

    @DubboReference(check = false, timeout = 10000, retries = 0)
    private PrivateImageStorageService privateImageStorageService;

    /** 读取指定学员的登记照原始内容。 */
    public PrivateImageContentView read(Long userId) {
        FaceReferenceView reference = unwrap(
                faceReferenceService.get(userId), "用户服务没有返回登记照信息");
        if (reference == null || !reference.enrolled() || reference.storageObjectId() == null) {
            throw new BusinessException(AppErrorCode.FACE_REFERENCE_REQUIRED);
        }
        PrivateImageContentView content = unwrap(
                privateImageStorageService.read(reference.storageObjectId()),
                "对象存储服务没有返回登记照内容");
        if (content == null || content.content() == null || content.content().length == 0) {
            throw new BusinessException(AppErrorCode.FACE_CHECK_UNAVAILABLE,
                    "登记照暂时无法读取");
        }
        return content;
    }

    /** 保存当次核验照片并返回私有对象标识。 */
    public Long saveLearningPhoto(byte[] content) {
        Long id = unwrap(privateImageStorageService.saveLearningPhoto(content), "学习照片保存失败");
        if (id == null) throw new BusinessException(AppErrorCode.STORAGE_OPERATION_FAILED, "学习照片保存失败");
        return id;
    }

    /** 历史未留存照片时无需访问对象存储。 */
    public String learningPhotoUrl(Long id) {
        if (id == null) return null;
        return unwrap(privateImageStorageService.learningPhotoPreview(id), "学习照片预览失败").url();
    }

    private <T> T unwrap(Result<T> result, String emptyMessage) {
        if (result == null) {
            throw new BusinessException(AppErrorCode.FACE_CHECK_UNAVAILABLE, emptyMessage);
        }
        if (!result.isSuccess()) {
            AppErrorCode code = AppErrorCode.fromCode(result.getCode());
            throw new BusinessException(code, result.getMessage());
        }
        return result.getData();
    }
}
