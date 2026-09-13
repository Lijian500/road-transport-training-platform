package me.lj.train.api.training;

import me.lj.train.api.training.PrivateImageModels.CreateFaceReferenceUploadSessionCommand;
import me.lj.train.api.training.PrivateImageModels.PrivateImageContentView;
import me.lj.train.api.training.PrivateImageModels.PrivateImageUploadCompleteView;
import me.lj.train.api.training.PrivateImageModels.PrivateImageUploadSessionView;
import me.lj.train.api.training.StorageModels.SignedRequestView;
import me.lj.train.common.core.result.Result;

/**
 * 人脸登记照等私有图片的对象存储RPC接口。
 */
public interface PrivateImageStorageService {

    Result<PrivateImageUploadSessionView> createFaceReferenceUploadSession(
            CreateFaceReferenceUploadSessionCommand command);

    Result<PrivateImageUploadCompleteView> completeFaceReferenceUpload(Long sessionId);

    Result<PrivateImageContentView> read(Long storageObjectId);

    Result<SignedRequestView> previewUrl(Long storageObjectId);

    /** 保存当前学员的学习核验照片，仅供档案预览。 */
    Result<Long> saveLearningPhoto(byte[] content);

    /** 校验企业及档案权限后生成短期照片预览地址。 */
    Result<SignedRequestView> learningPhotoPreview(Long storageObjectId);

    Result<?> delete(Long storageObjectId);
}
