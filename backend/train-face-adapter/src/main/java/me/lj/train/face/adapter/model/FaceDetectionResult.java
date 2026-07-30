package me.lj.train.face.adapter.model;

import java.io.Serializable;
import java.util.List;

/**
 * 单张图片的人脸检测结果。
 */
public record FaceDetectionResult(
        int faceCount,
        boolean hasFace,
        boolean multipleFaces,
        List<FaceBox> faces,
        long elapsedMillis
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public FaceDetectionResult {
        faces = List.copyOf(faces);
        if (faceCount != faces.size()) {
            throw new IllegalArgumentException("人脸数量必须与人脸列表一致");
        }
        if (hasFace != (faceCount > 0) || multipleFaces != (faceCount > 1)) {
            throw new IllegalArgumentException("人脸状态必须与人脸数量一致");
        }
        if (elapsedMillis < 0L) {
            throw new IllegalArgumentException("处理耗时不能为负数");
        }
    }

    public static FaceDetectionResult of(List<FaceBox> faces, long elapsedMillis) {
        int faceCount = faces.size();
        return new FaceDetectionResult(faceCount, faceCount > 0, faceCount > 1, faces, elapsedMillis);
    }
}
