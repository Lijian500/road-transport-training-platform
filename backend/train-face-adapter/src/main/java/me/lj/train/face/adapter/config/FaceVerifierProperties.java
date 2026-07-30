package me.lj.train.face.adapter.config;

import me.lj.train.face.adapter.opencv.OpenCvFaceVerifierConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * 人脸核验Spring Boot配置。
 */
@ConfigurationProperties(prefix = "train.face")
public class FaceVerifierProperties {

    private boolean enabled;
    private Path detectionModelPath;
    private Path recognitionModelPath;
    private float detectionScoreThreshold =
            OpenCvFaceVerifierConfig.DEFAULT_DETECTION_SCORE_THRESHOLD;
    private float nmsThreshold = OpenCvFaceVerifierConfig.DEFAULT_NMS_THRESHOLD;
    private int topK = OpenCvFaceVerifierConfig.DEFAULT_TOP_K;
    private double similarityThreshold = OpenCvFaceVerifierConfig.DEFAULT_SIMILARITY_THRESHOLD;
    private int maxImageBytes = OpenCvFaceVerifierConfig.DEFAULT_MAX_IMAGE_BYTES;
    private long maxImagePixels = OpenCvFaceVerifierConfig.DEFAULT_MAX_IMAGE_PIXELS;

    OpenCvFaceVerifierConfig toConfig() {
        return OpenCvFaceVerifierConfig.builder(detectionModelPath, recognitionModelPath)
                .detectionScoreThreshold(detectionScoreThreshold)
                .nmsThreshold(nmsThreshold)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .maxImageBytes(maxImageBytes)
                .maxImagePixels(maxImagePixels)
                .build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Path getDetectionModelPath() {
        return detectionModelPath;
    }

    public void setDetectionModelPath(Path detectionModelPath) {
        this.detectionModelPath = detectionModelPath;
    }

    public Path getRecognitionModelPath() {
        return recognitionModelPath;
    }

    public void setRecognitionModelPath(Path recognitionModelPath) {
        this.recognitionModelPath = recognitionModelPath;
    }

    public float getDetectionScoreThreshold() {
        return detectionScoreThreshold;
    }

    public void setDetectionScoreThreshold(float detectionScoreThreshold) {
        this.detectionScoreThreshold = detectionScoreThreshold;
    }

    public float getNmsThreshold() {
        return nmsThreshold;
    }

    public void setNmsThreshold(float nmsThreshold) {
        this.nmsThreshold = nmsThreshold;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public int getMaxImageBytes() {
        return maxImageBytes;
    }

    public void setMaxImageBytes(int maxImageBytes) {
        this.maxImageBytes = maxImageBytes;
    }

    public long getMaxImagePixels() {
        return maxImagePixels;
    }

    public void setMaxImagePixels(long maxImagePixels) {
        this.maxImagePixels = maxImagePixels;
    }
}
