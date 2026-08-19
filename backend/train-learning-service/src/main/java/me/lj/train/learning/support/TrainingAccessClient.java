package me.lj.train.learning.support;

import me.lj.train.api.training.LearningAccessModels.LearningPlaybackCommand;
import me.lj.train.api.training.LearningAccessModels.LearningTaskContextView;
import me.lj.train.api.training.LearningAccessModels.LearningTaskQuery;
import me.lj.train.api.training.LearningAccessService;
import me.lj.train.api.training.StorageModels.SignedRequestView;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

/**
 * 学习服务调用培训任务快照和播放签名的适配层。
 */
@Component
public class TrainingAccessClient {

    @DubboReference(check = false, timeout = 8000, retries = 0)
    private LearningAccessService learningAccessService;

    public LearningTaskContextView taskContext(Long planId) {
        return unwrap(learningAccessService.getTaskContext(new LearningTaskQuery(planId)));
    }

    public SignedRequestView playbackUrl(LearningPlaybackCommand command) {
        return unwrap(learningAccessService.createCoursewarePlaybackUrl(command));
    }

    private <T> T unwrap(Result<T> result) {
        if (result == null) {
            throw new BusinessException(AppErrorCode.SYSTEM_ERROR, "培训服务没有返回结果");
        }
        if (!result.isSuccess()) {
            throw new BusinessException(AppErrorCode.fromCode(result.getCode()), result.getMessage());
        }
        return result.getData();
    }
}
