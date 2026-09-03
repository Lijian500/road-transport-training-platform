package me.lj.train.training.storage;

import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.training.config.OssStorageProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadFileValidatorTest {

    private final OssStorageProperties properties = new OssStorageProperties();

    @Test
    void shouldAcceptMp4FtypHeader() {
        ObjectStorageService.ObjectMetadata metadata =
                new ObjectStorageService.ObjectMetadata(100L, "video/mp4", "etag");
        byte[] header = {0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};

        UploadFileValidator.validateObject("VIDEO", "video/mp4", 100L, metadata, header);
    }

    @Test
    void shouldRejectVideoWithoutFtypHeader() {
        ObjectStorageService.ObjectMetadata metadata =
                new ObjectStorageService.ObjectMetadata(100L, "video/mp4", "etag");

        assertThatThrownBy(() -> UploadFileValidator.validateObject(
                "VIDEO", "video/mp4", 100L, metadata, new byte[] {0, 1, 2, 3, 4, 5, 6, 7}))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MP4");
    }

    @Test
    void shouldAcceptJpegPngAndWebpHeaders() {
        ObjectStorageService.ObjectMetadata jpeg =
                new ObjectStorageService.ObjectMetadata(10L, "image/jpeg", "jpeg");
        ObjectStorageService.ObjectMetadata png =
                new ObjectStorageService.ObjectMetadata(10L, "image/png", "png");
        ObjectStorageService.ObjectMetadata webp =
                new ObjectStorageService.ObjectMetadata(10L, "image/webp", "webp");

        UploadFileValidator.validateObject("COVER", "image/jpeg", 10L, jpeg,
                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        UploadFileValidator.validateObject("COVER", "image/png", 10L, png,
                new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        UploadFileValidator.validateObject("COVER", "image/webp", 10L, webp,
                new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'});
    }

    @Test
    void shouldRejectObjectSizeAndContentTypeMismatch() {
        byte[] header = {0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
        ObjectStorageService.ObjectMetadata wrongSize =
                new ObjectStorageService.ObjectMetadata(99L, "video/mp4", "etag");
        ObjectStorageService.ObjectMetadata wrongType =
                new ObjectStorageService.ObjectMetadata(100L, "application/octet-stream", "etag");

        assertThatThrownBy(() -> UploadFileValidator.validateObject(
                "VIDEO", "video/mp4", 100L, wrongSize, header))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("大小");
        assertThatThrownBy(() -> UploadFileValidator.validateObject(
                "VIDEO", "video/mp4", 100L, wrongType, header))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容类型");
    }

    @Test
    void shouldValidateDeclaredTypeSizeAndDuration() {
        UploadFileValidator.FileDeclaration declaration = UploadFileValidator.validateVideo(
                "安全驾驶.MP4", "video/mp4", properties.getPartSizeBytes(), 60, properties);

        assertThat(declaration.extension()).isEqualTo("mp4");
        assertThatThrownBy(() -> UploadFileValidator.validateVideo(
                "安全驾驶.avi", "video/mp4", 100L, 60, properties))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> UploadFileValidator.validateCover(
                "封面.png", "image/jpeg", 100L, properties))
                .isInstanceOf(BusinessException.class);
    }
}
