package me.lj.train.face.adapter;

import me.lj.train.common.core.result.Result;
import me.lj.train.face.adapter.model.FaceComparisonResult;
import me.lj.train.face.adapter.model.FaceDetectionResult;

/**
 * 人脸检测与一对一核验统一接口。
 */
public interface FaceVerifier {

    /**
     * 检测图片中的人脸数量、位置和置信度。
     *
     * @param imageBytes 原始图片字节
     * @return 检测结果
     */
    Result<FaceDetectionResult> detect(byte[] imageBytes);

    /**
     * 比对两张图片中的单个人脸。
     *
     * <p>任一图片无人脸或包含多张人脸时不进行特征比对，并在结果中返回明确状态。</p>
     *
     * @param referenceImageBytes 登记照字节
     * @param candidateImageBytes 待核验照片字节
     * @return 人脸比对结果
     */
    Result<FaceComparisonResult> compare(byte[] referenceImageBytes, byte[] candidateImageBytes);
}
