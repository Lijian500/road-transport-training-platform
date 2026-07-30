package me.lj.train.face.adapter.model;

import java.io.Serializable;

/**
 * 图片中的人脸矩形区域。
 */
public record FaceBox(
        int x,
        int y,
        int width,
        int height,
        double confidence
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public FaceBox {
        if (x < 0 || y < 0 || width < 0 || height < 0) {
            throw new IllegalArgumentException("人脸位置不能为负数");
        }
        if (!Double.isFinite(confidence) || confidence < 0D || confidence > 1D) {
            throw new IllegalArgumentException("人脸置信度必须在0到1之间");
        }
    }
}
