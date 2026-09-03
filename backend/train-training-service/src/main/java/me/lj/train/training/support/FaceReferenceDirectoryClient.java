package me.lj.train.training.support;

import me.lj.train.api.admin.FaceReferenceModels.FaceReferenceView;
import me.lj.train.api.admin.FaceReferenceService;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

/**
 * 培训服务访问用户登记照目录的内部客户端。
 */
@Component
public class FaceReferenceDirectoryClient {

    @DubboReference(check = false, timeout = 5000, retries = 0)
    private FaceReferenceService faceReferenceService;

    public FaceReferenceView get(Long userId) {
        Result<FaceReferenceView> result = faceReferenceService.get(userId);
        if (result == null) {
            throw new BusinessException(AppErrorCode.SYSTEM_ERROR, "人脸登记目录未返回结果");
        }
        if (!result.isSuccess()) {
            throw new BusinessException(AppErrorCode.fromCode(result.getCode()), result.getMessage());
        }
        return result.getData();
    }
}
