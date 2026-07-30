package me.lj.train.face.adapter.opencv;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenCvFaceVerifierConfigTest {

    @Test
    void shouldUseDocumentedDefaults() {
        OpenCvFaceVerifierConfig config = OpenCvFaceVerifierConfig.builder(
                Path.of("yunet.onnx"),
                Path.of("sface.onnx")
        ).build();

        assertThat(config.detectionScoreThreshold()).isEqualTo(0.9F);
        assertThat(config.nmsThreshold()).isEqualTo(0.3F);
        assertThat(config.topK()).isEqualTo(5_000);
        assertThat(config.similarityThreshold()).isEqualTo(0.363D);
        assertThat(config.maxImageBytes()).isEqualTo(10 * 1024 * 1024);
        assertThat(config.maxImagePixels()).isEqualTo(25_000_000L);
    }

    @Test
    void shouldRejectUnsafeLimitsAndThresholds() {
        assertThatThrownBy(() -> OpenCvFaceVerifierConfig.builder(
                Path.of("yunet.onnx"),
                Path.of("sface.onnx")
        ).similarityThreshold(1.1D).build())
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> OpenCvFaceVerifierConfig.builder(
                Path.of("yunet.onnx"),
                Path.of("sface.onnx")
        ).maxImagePixels(0L).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldFailFastWhenModelFileDoesNotExist() {
        OpenCvFaceVerifierConfig config = OpenCvFaceVerifierConfig.builder(
                Path.of("missing-yunet.onnx"),
                Path.of("missing-sface.onnx")
        ).build();

        assertThatThrownBy(() -> new OpenCvFaceVerifier(config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("YuNet模型文件不存在或不可读");
    }
}
