package me.lj.train.face.adapter.opencv;

import me.lj.train.common.core.result.Result;
import me.lj.train.face.adapter.FaceErrorCode;
import me.lj.train.face.adapter.FaceVerifier;
import me.lj.train.face.adapter.model.FaceBox;
import me.lj.train.face.adapter.model.FaceComparisonResult;
import me.lj.train.face.adapter.model.FaceComparisonStatus;
import me.lj.train.face.adapter.model.FaceDetectionResult;
import nu.pattern.OpenCV;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.objdetect.FaceDetectorYN;
import org.opencv.objdetect.FaceRecognizerSF;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 基于OpenCV YuNet检测器和SFace识别器的人脸核验实现。
 *
 * <p>OpenCV的模型对象不是并发安全对象，因此同一实例内串行执行模型推理；
 * 图片解码和入参校验不占用模型锁。</p>
 */
public final class OpenCvFaceVerifier implements FaceVerifier {

    private static final int INITIAL_INPUT_SIZE = 320;
    private static final int FACE_OUTPUT_COLUMNS = 15;
    private static volatile boolean nativeLoaded;

    private final OpenCvFaceVerifierConfig config;
    private final FaceDetectorYN detector;
    private final FaceRecognizerSF recognizer;
    private final Lock modelLock = new ReentrantLock();

    public OpenCvFaceVerifier(OpenCvFaceVerifierConfig config) {
        this.config = Objects.requireNonNull(config, "人脸核验配置不能为空");
        requireReadableModel(config.detectionModelPath(), "YuNet");
        requireReadableModel(config.recognitionModelPath(), "SFace");
        loadNativeLibrary();
        try {
            detector = FaceDetectorYN.create(
                    config.detectionModelPath().toString(),
                    "",
                    new Size(INITIAL_INPUT_SIZE, INITIAL_INPUT_SIZE),
                    config.detectionScoreThreshold(),
                    config.nmsThreshold(),
                    config.topK()
            );
            recognizer = FaceRecognizerSF.create(config.recognitionModelPath().toString(), "");
        } catch (RuntimeException exception) {
            throw new IllegalStateException(FaceErrorCode.MODEL_NOT_READY.getMessage(), exception);
        }
    }

    @Override
    public Result<FaceDetectionResult> detect(byte[] imageBytes) {
        long startedAt = System.nanoTime();
        Mat image = null;
        Mat faces = null;
        try {
            image = decodeImage(imageBytes);
            modelLock.lock();
            try {
                faces = detectFaces(image);
                return Result.ok(toDetectionResult(faces, image, elapsedMillis(startedAt)));
            } finally {
                modelLock.unlock();
            }
        } catch (FaceAdapterException exception) {
            return Result.failed(exception.errorCode, exception.getMessage());
        } catch (RuntimeException exception) {
            return Result.failed(FaceErrorCode.INFERENCE_FAILED);
        } finally {
            release(faces);
            release(image);
        }
    }

    @Override
    public Result<FaceComparisonResult> compare(byte[] referenceImageBytes, byte[] candidateImageBytes) {
        long startedAt = System.nanoTime();
        Mat referenceImage = null;
        Mat candidateImage = null;
        Mat referenceFaces = null;
        Mat candidateFaces = null;
        Mat referenceFace = null;
        Mat candidateFace = null;
        Mat alignedReference = null;
        Mat alignedCandidate = null;
        Mat referenceFeature = null;
        Mat candidateFeature = null;
        try {
            referenceImage = decodeImage(referenceImageBytes);
            candidateImage = decodeImage(candidateImageBytes);
            modelLock.lock();
            try {
                long detectionStartedAt = System.nanoTime();
                referenceFaces = detectFaces(referenceImage);
                FaceDetectionResult referenceDetection = toDetectionResult(
                        referenceFaces, referenceImage, elapsedMillis(detectionStartedAt));

                detectionStartedAt = System.nanoTime();
                candidateFaces = detectFaces(candidateImage);
                FaceDetectionResult candidateDetection = toDetectionResult(
                        candidateFaces, candidateImage, elapsedMillis(detectionStartedAt));

                FaceComparisonStatus invalidStatus = findInvalidStatus(referenceDetection, candidateDetection);
                if (invalidStatus != null) {
                    return Result.ok(FaceComparisonResult.notComparable(
                            invalidStatus,
                            config.similarityThreshold(),
                            referenceDetection,
                            candidateDetection,
                            elapsedMillis(startedAt)
                    ));
                }

                referenceFace = referenceFaces.row(0);
                candidateFace = candidateFaces.row(0);
                alignedReference = new Mat();
                alignedCandidate = new Mat();
                referenceFeature = new Mat();
                candidateFeature = new Mat();
                recognizer.alignCrop(referenceImage, referenceFace, alignedReference);
                recognizer.alignCrop(candidateImage, candidateFace, alignedCandidate);
                recognizer.feature(alignedReference, referenceFeature);
                // SFace会复用内部输出缓冲区，必须克隆后再提取下一张脸的特征。
                Mat stableReferenceFeature = referenceFeature.clone();
                referenceFeature.release();
                referenceFeature = stableReferenceFeature;
                recognizer.feature(alignedCandidate, candidateFeature);
                Mat stableCandidateFeature = candidateFeature.clone();
                candidateFeature.release();
                candidateFeature = stableCandidateFeature;
                double similarity = recognizer.match(
                        referenceFeature, candidateFeature, FaceRecognizerSF.FR_COSINE);
                return Result.ok(FaceComparisonResult.compared(
                        similarity,
                        config.similarityThreshold(),
                        referenceDetection,
                        candidateDetection,
                        elapsedMillis(startedAt)
                ));
            } finally {
                modelLock.unlock();
            }
        } catch (FaceAdapterException exception) {
            return Result.failed(exception.errorCode, exception.getMessage());
        } catch (RuntimeException exception) {
            return Result.failed(FaceErrorCode.INFERENCE_FAILED);
        } finally {
            release(candidateFeature);
            release(referenceFeature);
            release(alignedCandidate);
            release(alignedReference);
            release(candidateFace);
            release(referenceFace);
            release(candidateFaces);
            release(referenceFaces);
            release(candidateImage);
            release(referenceImage);
        }
    }

    private Mat decodeImage(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new FaceAdapterException(FaceErrorCode.INVALID_IMAGE);
        }
        if (imageBytes.length > config.maxImageBytes()) {
            throw new FaceAdapterException(FaceErrorCode.IMAGE_TOO_LARGE);
        }
        MatOfByte encodedImage = new MatOfByte(imageBytes);
        Mat image = null;
        try {
            image = Imgcodecs.imdecode(encodedImage, Imgcodecs.IMREAD_COLOR);
            if (image.empty()) {
                throw new FaceAdapterException(FaceErrorCode.INVALID_IMAGE);
            }
            long pixels = (long) image.rows() * image.cols();
            if (pixels > config.maxImagePixels()) {
                throw new FaceAdapterException(FaceErrorCode.IMAGE_TOO_LARGE);
            }
            return image;
        } catch (RuntimeException exception) {
            release(image);
            if (exception instanceof FaceAdapterException faceAdapterException) {
                throw faceAdapterException;
            }
            throw new FaceAdapterException(FaceErrorCode.INVALID_IMAGE);
        } finally {
            encodedImage.release();
        }
    }

    private Mat detectFaces(Mat image) {
        Mat faces = new Mat();
        try {
            detector.setInputSize(image.size());
            detector.detect(image, faces);
            if (!faces.empty() && faces.cols() < FACE_OUTPUT_COLUMNS) {
                throw new IllegalStateException("YuNet返回结果列数不正确");
            }
            return faces;
        } catch (RuntimeException exception) {
            faces.release();
            throw exception;
        }
    }

    private static FaceDetectionResult toDetectionResult(Mat faces, Mat image, long elapsedMillis) {
        if (faces.empty()) {
            return FaceDetectionResult.of(List.of(), elapsedMillis);
        }
        List<FaceBox> faceBoxes = new ArrayList<>(faces.rows());
        for (int rowIndex = 0; rowIndex < faces.rows(); rowIndex++) {
            float[] values = new float[FACE_OUTPUT_COLUMNS];
            faces.get(rowIndex, 0, values);
            int x = clamp((int) Math.floor(values[0]), 0, image.cols());
            int y = clamp((int) Math.floor(values[1]), 0, image.rows());
            int right = clamp((int) Math.ceil(values[0] + values[2]), x, image.cols());
            int bottom = clamp((int) Math.ceil(values[1] + values[3]), y, image.rows());
            double confidence = Math.max(0D, Math.min(1D, values[14]));
            faceBoxes.add(new FaceBox(x, y, right - x, bottom - y, confidence));
        }
        return FaceDetectionResult.of(faceBoxes, elapsedMillis);
    }

    private static FaceComparisonStatus findInvalidStatus(
            FaceDetectionResult referenceDetection,
            FaceDetectionResult candidateDetection
    ) {
        if (!referenceDetection.hasFace()) {
            return FaceComparisonStatus.REFERENCE_NO_FACE;
        }
        if (referenceDetection.multipleFaces()) {
            return FaceComparisonStatus.REFERENCE_MULTIPLE_FACES;
        }
        if (!candidateDetection.hasFace()) {
            return FaceComparisonStatus.CANDIDATE_NO_FACE;
        }
        if (candidateDetection.multipleFaces()) {
            return FaceComparisonStatus.CANDIDATE_MULTIPLE_FACES;
        }
        return null;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private static void requireReadableModel(Path path, String modelName) {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalStateException(modelName + "模型文件不存在或不可读: " + path);
        }
    }

    private static void loadNativeLibrary() {
        if (nativeLoaded) {
            return;
        }
        synchronized (OpenCvFaceVerifier.class) {
            if (!nativeLoaded) {
                try {
                    OpenCV.loadLocally();
                    nativeLoaded = true;
                } catch (RuntimeException | UnsatisfiedLinkError exception) {
                    throw new IllegalStateException("OpenCV本地库加载失败", exception);
                }
            }
        }
    }

    private static void release(Mat mat) {
        if (mat != null) {
            mat.release();
        }
    }

    private static final class FaceAdapterException extends RuntimeException {

        private static final long serialVersionUID = 1L;
        private final FaceErrorCode errorCode;

        private FaceAdapterException(FaceErrorCode errorCode) {
            super(errorCode.getMessage());
            this.errorCode = errorCode;
        }
    }
}
