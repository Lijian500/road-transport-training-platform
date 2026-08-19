package me.lj.train.api.learning;

import me.lj.train.api.learning.LearningModels.CourseProgressView;
import me.lj.train.api.learning.LearningModels.LearningEventResultView;
import me.lj.train.api.learning.LearningModels.LearningSessionView;
import me.lj.train.api.learning.LearningModels.OpenSessionCommand;
import me.lj.train.api.learning.LearningModels.PlanProgressView;
import me.lj.train.api.learning.LearningModels.PlaybackUrlCommand;
import me.lj.train.api.learning.LearningModels.SubmitEventCommand;
import me.lj.train.api.learning.LearningModels.TerminateSessionCommand;
import me.lj.train.api.training.StorageModels.SignedRequestView;
import me.lj.train.common.core.result.Result;

/**
 * 学习会话、状态转换、进度及播放授权RPC。
 */
public interface LearningSessionService {

    Result<PlanProgressView> getPlanProgress(Long planId);

    Result<CourseProgressView> getCourse(Long planId, Long planCourseId);

    Result<LearningSessionView> openSession(OpenSessionCommand command);

    Result<LearningSessionView> getActiveSession();

    Result<LearningSessionView> getSession(Long sessionId);

    Result<LearningEventResultView> submitEvent(SubmitEventCommand command);

    Result<?> terminateSession(TerminateSessionCommand command);

    Result<SignedRequestView> createPlaybackUrl(PlaybackUrlCommand command);
}
