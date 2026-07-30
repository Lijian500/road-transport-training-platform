package me.lj.train.face.adapter.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FaceResultTest {

    @Test
    void shouldBuildDetectionStateFromFaceCountAndProtectFaceList() {
        List<FaceBox> source = new ArrayList<>();
        source.add(new FaceBox(10, 20, 100, 120, 0.98D));

        FaceDetectionResult result = FaceDetectionResult.of(source, 8L);
        source.clear();

        assertThat(result.faceCount()).isEqualTo(1);
        assertThat(result.hasFace()).isTrue();
        assertThat(result.multipleFaces()).isFalse();
        assertThat(result.faces()).hasSize(1);
        assertThatThrownBy(() -> result.faces().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectInconsistentDetectionState() {
        assertThatThrownBy(() -> new FaceDetectionResult(
                2,
                true,
                true,
                List.of(new FaceBox(0, 0, 10, 10, 0.9D)),
                1L
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReturnMatchAndDisplayPercentage() {
        FaceDetectionResult detection = singleFaceDetection();

        FaceComparisonResult result = FaceComparisonResult.compared(
                0.82D, 0.363D, detection, detection, 15L);

        assertThat(result.status()).isEqualTo(FaceComparisonStatus.MATCH);
        assertThat(result.comparable()).isTrue();
        assertThat(result.samePerson()).isTrue();
        assertThat(result.similarity()).isEqualTo(0.82D);
        assertThat(result.similarityPercent()).isEqualTo(82D);
    }

    @Test
    void shouldReturnExplicitStatusWhenImageCannotBeCompared() {
        FaceDetectionResult noFace = FaceDetectionResult.of(List.of(), 2L);
        FaceDetectionResult singleFace = singleFaceDetection();

        FaceComparisonResult result = FaceComparisonResult.notComparable(
                FaceComparisonStatus.REFERENCE_NO_FACE,
                0.363D,
                noFace,
                singleFace,
                6L
        );

        assertThat(result.comparable()).isFalse();
        assertThat(result.samePerson()).isFalse();
        assertThat(result.similarity()).isNull();
        assertThat(result.getMessage()).isEqualTo("登记照中未检测到人脸");
    }

    private static FaceDetectionResult singleFaceDetection() {
        return FaceDetectionResult.of(
                List.of(new FaceBox(10, 10, 80, 80, 0.99D)),
                3L
        );
    }
}
