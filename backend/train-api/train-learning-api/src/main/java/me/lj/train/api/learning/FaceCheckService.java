package me.lj.train.api.learning;

import me.lj.train.api.learning.FaceCheckModels.FaceCheckView;
import me.lj.train.api.learning.FaceCheckModels.FaceReferenceValidationView;
import me.lj.train.api.learning.FaceCheckModels.SubmitFaceCheckCommand;
import me.lj.train.common.core.result.Result;

/**
 * 学员人脸抽验查询与照片提交RPC。
 */
public interface FaceCheckService {

    /** 查询签到、签退是否需要当次人脸验证。 */
    Result<Boolean> attendanceRequired(Long sessionId);

    /** 校验本人照片并签发绑定当前事件序号的短期凭据。 */
    Result<?> verifyAttendance(Long sessionId, String action, String clientInstanceId, byte[] imageBytes);


    /** 校验登记照中是否恰好包含一张可识别人脸，不保存图片。 */
    Result<FaceReferenceValidationView> validateReference(byte[] imageBytes);

    /** 查询指定学习会话当前待处理或最近完成的抽验任务。 */
    Result<FaceCheckView> getCurrentFaceCheck(Long sessionId);

    /** 提交抽验照片；相同任务和请求ID重复提交时返回首次处理结果。 */
    Result<FaceCheckView> submitFaceCheck(SubmitFaceCheckCommand command);
}
