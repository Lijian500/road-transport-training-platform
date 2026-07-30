package me.lj.train.face.adapter.opencv;

import java.nio.file.Path;
import java.util.Objects;

/**
 * OpenCV YuNet与SFace模型配置。
 */
public final class OpenCvFaceVerifierConfig {

    public static final float DEFAULT_DETECTION_SCORE_THRESHOLD = 0.9F;
    public static final float DEFAULT_NMS_THRESHOLD = 0.3F;
    public static final int DEFAULT_TOP_K = 5_000;
    public static final double DEFAULT_SIMILARITY_THRESHOLD = 0.363D;
    public static final int DEFAULT_MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    public static final long DEFAULT_MAX_IMAGE_PIXELS = 25_000_000L;

    private final Path detectionModelPath;
    private final Path recognitionModelPath;
    private final float detectionScoreThreshold;
    private final float nmsThreshold;
    private final int topK;
    private final double similarityThreshold;
    private final int maxImageBytes;
    private final long maxImagePixels;

    private OpenCvFaceVerifierConfig(Builder builder) {
        detectionModelPath = Objects.requireNonNull(builder.detectionModelPath, "YuNet模型路径不能为空")
                .toAbsolutePath()
                .normalize();
        recognitionModelPath = Objects.requireNonNull(builder.recognitionModelPath, "SFace模型路径不能为空")
                .toAbsolutePath()
                .normalize();
        detectionScoreThreshold = requireRange(
                builder.detectionScoreThreshold, 0F, 1F, "人脸检测置信度阈值");
        nmsThreshold = requireRange(builder.nmsThreshold, 0F, 1F, "NMS阈值");
        if (builder.topK <= 0) {
            throw new IllegalArgumentException("候选人脸数量上限必须大于0");
        }
        if (!Double.isFinite(builder.similarityThreshold)
                || builder.similarityThreshold < -1D
                || builder.similarityThreshold > 1D) {
            throw new IllegalArgumentException("人脸相似度阈值必须在-1到1之间");
        }
        if (builder.maxImageBytes <= 0) {
            throw new IllegalArgumentException("图片字节上限必须大于0");
        }
        if (builder.maxImagePixels <= 0L) {
            throw new IllegalArgumentException("图片像素上限必须大于0");
        }
        topK = builder.topK;
        similarityThreshold = builder.similarityThreshold;
        maxImageBytes = builder.maxImageBytes;
        maxImagePixels = builder.maxImagePixels;
    }

    public static Builder builder(Path detectionModelPath, Path recognitionModelPath) {
        return new Builder(detectionModelPath, recognitionModelPath);
    }

    public Path detectionModelPath() {
        return detectionModelPath;
    }

    public Path recognitionModelPath() {
        return recognitionModelPath;
    }

    public float detectionScoreThreshold() {
        return detectionScoreThreshold;
    }

    public float nmsThreshold() {
        return nmsThreshold;
    }

    public int topK() {
        return topK;
    }

    public double similarityThreshold() {
        return similarityThreshold;
    }

    public int maxImageBytes() {
        return maxImageBytes;
    }

    public long maxImagePixels() {
        return maxImagePixels;
    }

    private static float requireRange(float value, float min, float max, String fieldName) {
        if (!Float.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(fieldName + "必须在" + min + "到" + max + "之间");
        }
        return value;
    }

    public static final class Builder {

        private final Path detectionModelPath;
        private final Path recognitionModelPath;
        private float detectionScoreThreshold = DEFAULT_DETECTION_SCORE_THRESHOLD;
        private float nmsThreshold = DEFAULT_NMS_THRESHOLD;
        private int topK = DEFAULT_TOP_K;
        private double similarityThreshold = DEFAULT_SIMILARITY_THRESHOLD;
        private int maxImageBytes = DEFAULT_MAX_IMAGE_BYTES;
        private long maxImagePixels = DEFAULT_MAX_IMAGE_PIXELS;

        private Builder(Path detectionModelPath, Path recognitionModelPath) {
            this.detectionModelPath = detectionModelPath;
            this.recognitionModelPath = recognitionModelPath;
        }

        public Builder detectionScoreThreshold(float detectionScoreThreshold) {
            this.detectionScoreThreshold = detectionScoreThreshold;
            return this;
        }

        public Builder nmsThreshold(float nmsThreshold) {
            this.nmsThreshold = nmsThreshold;
            return this;
        }

        public Builder topK(int topK) {
            this.topK = topK;
            return this;
        }

        public Builder similarityThreshold(double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
            return this;
        }

        public Builder maxImageBytes(int maxImageBytes) {
            this.maxImageBytes = maxImageBytes;
            return this;
        }

        public Builder maxImagePixels(long maxImagePixels) {
            this.maxImagePixels = maxImagePixels;
            return this;
        }

        public OpenCvFaceVerifierConfig build() {
            return new OpenCvFaceVerifierConfig(this);
        }
    }
}
