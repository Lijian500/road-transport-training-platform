package me.lj.train.api.training;

import me.lj.train.api.training.LearningAccessModels.LearningPlaybackCommand;
import me.lj.train.api.training.LearningAccessModels.LearningTaskContextView;
import me.lj.train.api.training.LearningAccessModels.LearningTaskQuery;
import me.lj.train.api.training.StorageModels.SignedRequestView;
import me.lj.train.common.core.result.Result;

/**
 * 培训服务向学习服务提供的学员资格和视频签名RPC。
 */
public interface LearningAccessService {

    Result<LearningTaskContextView> getTaskContext(LearningTaskQuery query);

    Result<SignedRequestView> createCoursewarePlaybackUrl(LearningPlaybackCommand command);
}
