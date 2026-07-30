package me.lj.train.face.adapter.opencv;

import me.lj.train.common.core.result.Result;
import me.lj.train.face.adapter.FaceErrorCode;
import me.lj.train.face.adapter.model.FaceComparisonResult;
import me.lj.train.face.adapter.model.FaceComparisonStatus;
import me.lj.train.face.adapter.model.FaceDetectionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 使用官方模型和公开测试图执行的可选真实推理测试。
 */
@EnabledIfSystemProperty(named = "face.integration.enabled", matches = "true")
class OpenCvFaceVerifierIntegrationTest {

    @Test
    void shouldDetectMultipleFacesAndCompareIdentities() throws Exception {
        OpenCvFaceVerifier verifier = new OpenCvFaceVerifier(
                OpenCvFaceVerifierConfig.builder(
                        Path.of(requiredProperty("face.detection.model")),
                        Path.of(requiredProperty("face.recognition.model"))
                ).build()
        );
        byte[] reference = Files.readAllBytes(Path.of(requiredProperty("face.reference.image")));
        byte[] differentPerson = Files.readAllBytes(Path.of(requiredProperty("face.different.image")));
        byte[] multipleFaces = Files.readAllBytes(Path.of(requiredProperty("face.multiple.image")));

        Result<FaceDetectionResult> singleDetection = verifier.detect(reference);
        Result<FaceDetectionResult> multipleDetection = verifier.detect(multipleFaces);
        Result<FaceComparisonResult> sameComparison = verifier.compare(reference, reference);
        Result<FaceComparisonResult> differentComparison = verifier.compare(reference, differentPerson);
        Result<FaceComparisonResult> multipleComparison = verifier.compare(reference, multipleFaces);
        byte[] blankImage = blankImage();
        Result<FaceDetectionResult> blankDetection = verifier.detect(blankImage);
        Result<FaceComparisonResult> blankComparison = verifier.compare(blankImage, reference);
        Result<FaceDetectionResult> invalidDetection = verifier.detect(new byte[]{1, 2, 3});

        assertThat(singleDetection.isSuccess()).isTrue();
        assertThat(singleDetection.getData().faceCount()).isEqualTo(1);
        assertThat(multipleDetection.isSuccess()).isTrue();
        assertThat(multipleDetection.getData().multipleFaces()).isTrue();
        assertThat(multipleDetection.getData().faceCount()).isGreaterThanOrEqualTo(2);
        assertThat(blankDetection.isSuccess()).isTrue();
        assertThat(blankDetection.getData().hasFace()).isFalse();
        assertThat(invalidDetection.isSuccess()).isFalse();
        assertThat(invalidDetection.getCode()).isEqualTo(FaceErrorCode.INVALID_IMAGE.getCode());

        assertThat(sameComparison.isSuccess()).isTrue();
        assertThat(sameComparison.getData().samePerson()).isTrue();
        assertThat(sameComparison.getData().similarity()).isGreaterThan(0.99D);

        assertThat(differentComparison.isSuccess()).isTrue();
        assertThat(differentComparison.getData().comparable()).isTrue();
        assertThat(differentComparison.getData().similarity())
                .isLessThan(differentComparison.getData().threshold());
        assertThat(differentComparison.getData().samePerson()).isFalse();
        assertThat(multipleComparison.getData().comparable()).isFalse();
        assertThat(multipleComparison.getData().status())
                .isEqualTo(FaceComparisonStatus.CANDIDATE_MULTIPLE_FACES);
        assertThat(blankComparison.getData().comparable()).isFalse();
        assertThat(blankComparison.getData().status())
                .isEqualTo(FaceComparisonStatus.REFERENCE_NO_FACE);
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少测试参数: " + name);
        }
        return value;
    }

    private static byte[] blankImage() throws Exception {
        BufferedImage image = new BufferedImage(320, 320, BufferedImage.TYPE_3BYTE_BGR);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return output.toByteArray();
    }
}
