package me.lj.train.api.admin;

import me.lj.train.api.admin.FaceReferenceModels.BindFaceReferenceCommand;
import me.lj.train.api.admin.FaceReferenceModels.FaceReferenceChangeView;
import me.lj.train.api.admin.FaceReferenceModels.FaceReferenceView;
import me.lj.train.common.core.result.Result;

/**
 * 人脸登记照元数据目录RPC接口。
 */
public interface FaceReferenceService {

    Result<FaceReferenceView> get(Long userId);

    Result<FaceReferenceChangeView> bind(BindFaceReferenceCommand command);

    Result<FaceReferenceChangeView> remove(Long userId);
}
