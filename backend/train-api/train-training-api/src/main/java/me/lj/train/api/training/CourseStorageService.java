package me.lj.train.api.training;

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

import java.util.List;

/**
 * 课程文件直传与预览RPC接口。
 */
public interface CourseStorageService {

    Result<StorageCapabilityView> capability();

    Result<UploadSessionView> createCoverUploadSession(CreateCoverUploadSessionCommand command);

    Result<UploadSessionView> createCoursewareUploadSession(CreateCoursewareUploadSessionCommand command);

    Result<List<SignedRequestView>> createPartUrls(CreatePartUrlsCommand command);

    Result<List<UploadedPartView>> listParts(Long sessionId);

    Result<UploadCompleteView> complete(Long sessionId);

    Result<?> cancel(Long sessionId);

    Result<SignedRequestView> coverPreviewUrl(Long courseId);

    Result<SignedRequestView> coursewarePreviewUrl(CoursewarePreviewCommand command);
}
