package me.lj.train.face.adapter.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * 两张单人脸图片的比对结果。
 *
 * <p>{@code similarity}为SFace余弦相似度原始值，{@code similarityPercent}
 * 仅是限制到0到1后换算的展示百分比，并非身份概率。</p>
 */
public record FaceComparisonResult(
        FaceComparisonStatus status,
        boolean comparable,
        boolean samePerson,
        Double similarity,
        Double similarityPercent,
        double threshold,
        FaceDetectionResult referenceDetection,
        FaceDetectionResult candidateDetection,
        long elapsedMillis
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public FaceComparisonResult {
        Objects.requireNonNull(status, "比对状态不能为空");
        Objects.requireNonNull(referenceDetection, "登记照检测结果不能为空");
        Objects.requireNonNull(candidateDetection, "待核验照片检测结果不能为空");
        if (!Double.isFinite(threshold) || threshold < -1D || threshold > 1D) {
            throw new IllegalArgumentException("相似度阈值必须在-1到1之间");
        }
        if (elapsedMillis < 0L) {
            throw new IllegalArgumentException("处理耗时不能为负数");
        }
        if (comparable) {
            if (status != FaceComparisonStatus.MATCH && status != FaceComparisonStatus.NOT_MATCH) {
                throw new IllegalArgumentException("可比对结果状态不正确");
            }
            if (similarity == null || similarityPercent == null
                    || !Double.isFinite(similarity) || !Double.isFinite(similarityPercent)) {
                throw new IllegalArgumentException("可比对结果必须包含有效相似度");
            }
        } else if (samePerson || similarity != null || similarityPercent != null) {
            throw new IllegalArgumentException("不可比对结果不能包含身份结论或相似度");
        }
        if (!comparable
                && (status == FaceComparisonStatus.MATCH || status == FaceComparisonStatus.NOT_MATCH)) {
            throw new IllegalArgumentException("不可比对结果状态不正确");
        }
    }

    public String getMessage() {
        return status.getMessage();
    }

    public static FaceComparisonResult compared(
            double similarity,
            double threshold,
            FaceDetectionResult referenceDetection,
            FaceDetectionResult candidateDetection,
            long elapsedMillis
    ) {
        boolean samePerson = similarity >= threshold;
        double similarityPercent = Math.max(0D, Math.min(1D, similarity)) * 100D;
        FaceComparisonStatus status = samePerson
                ? FaceComparisonStatus.MATCH
                : FaceComparisonStatus.NOT_MATCH;
        return new FaceComparisonResult(
                status,
                true,
                samePerson,
                similarity,
                similarityPercent,
                threshold,
                referenceDetection,
                candidateDetection,
                elapsedMillis
        );
    }

    public static FaceComparisonResult notComparable(
            FaceComparisonStatus status,
            double threshold,
            FaceDetectionResult referenceDetection,
            FaceDetectionResult candidateDetection,
            long elapsedMillis
    ) {
        return new FaceComparisonResult(
                status,
                false,
                false,
                null,
                null,
                threshold,
                referenceDetection,
                candidateDetection,
                elapsedMillis
        );
    }
}
