package me.lj.train.training.storage;

import me.lj.train.training.config.OssStorageProperties;
import me.lj.train.training.storage.ObjectStorageService.ObjectMetadata;
import me.lj.train.training.storage.ObjectStorageService.SignedRequest;
import me.lj.train.training.storage.ObjectStorageService.StoredPart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实OSS联调；仅在显式提供专用测试Bucket与密钥时执行。
 */
@EnabledIfEnvironmentVariable(named = "OSS_INTEGRATION_ENABLED", matches = "(?i)true")
class AliyunOssIntegrationTest {

    private static final int FIRST_PART_SIZE = 128 * 1024;
    private static final int SECOND_PART_SIZE = 64 * 1024;

    @Test
    void shouldUploadPartsPreviewRangeAndDeletePrivateObject() throws Exception {
        assertThat(System.getenv("OSS_BUCKET")).isNotBlank();
        assertThat(System.getenv("OSS_ACCESS_KEY_ID")).isNotBlank();
        assertThat(System.getenv("OSS_ACCESS_KEY_SECRET")).isNotBlank();

        OssStorageProperties properties = integrationProperties();
        AliyunOssStorageService storage = new AliyunOssStorageService(properties);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        String objectKey = properties.getObjectPrefix()
                + "/integration/" + UUID.randomUUID().toString().replace("-", "") + ".mp4";
        String uploadId = null;
        boolean completed = false;
        byte[] file = testMp4();

        try {
            assertThat(storage.isEnabled()).as(storage.disabledMessage()).isTrue();
            uploadId = storage.initiateMultipartUpload(objectKey, "video/mp4");

            uploadPart(httpClient, storage, objectKey, uploadId, 1,
                    Arrays.copyOfRange(file, 0, FIRST_PART_SIZE));
            uploadPart(httpClient, storage, objectKey, uploadId, 2,
                    Arrays.copyOfRange(file, FIRST_PART_SIZE, file.length));

            List<StoredPart> parts = storage.listParts(objectKey, uploadId);
            assertThat(parts).extracting(StoredPart::partNumber).containsExactly(1, 2);
            assertThat(parts).extracting(StoredPart::sizeBytes)
                    .containsExactly((long) FIRST_PART_SIZE, (long) SECOND_PART_SIZE);

            storage.completeMultipartUpload(objectKey, uploadId, parts);
            completed = true;
            ObjectMetadata metadata = storage.headObject(objectKey);
            assertThat(metadata).isNotNull();
            assertThat(metadata.sizeBytes()).isEqualTo(file.length);
            assertThat(metadata.contentType()).startsWith("video/mp4");
            assertThat(storage.readObjectPrefix(objectKey, 12))
                    .containsExactly(Arrays.copyOf(file, 12));

            SignedRequest preview = storage.presignGet(objectKey, Duration.ofMinutes(5));
            HttpRequest.Builder previewRequest = signedRequest(preview)
                    .header("Range", "bytes=4-11")
                    .GET();
            HttpResponse<byte[]> previewResponse = httpClient.send(
                    previewRequest.build(), HttpResponse.BodyHandlers.ofByteArray());
            assertThat(previewResponse.statusCode()).isEqualTo(206);
            assertThat(previewResponse.body()).containsExactly(
                    new byte[] {'f', 't', 'y', 'p', 'i', 's', 'o', 'm'});
        } finally {
            if (!completed && uploadId != null) {
                storage.abortMultipartUpload(objectKey, uploadId);
            }
            if (storage.headObject(objectKey) != null) {
                storage.deleteObject(objectKey);
            }
            assertThat(storage.headObject(objectKey)).isNull();
            storage.close();
        }
    }

    private OssStorageProperties integrationProperties() {
        OssStorageProperties properties = new OssStorageProperties();
        properties.setEnabled(true);
        properties.setRegion(environment("OSS_REGION", "cn-hangzhou"));
        properties.setEndpoint(environment(
                "OSS_ENDPOINT", "https://oss-cn-hangzhou.aliyuncs.com"));
        properties.setBucket(System.getenv("OSS_BUCKET"));
        properties.setObjectPrefix(environment("OSS_OBJECT_PREFIX", "road-training"));
        return properties;
    }

    private void uploadPart(
            HttpClient httpClient,
            AliyunOssStorageService storage,
            String objectKey,
            String uploadId,
            int partNumber,
            byte[] content) throws Exception {
        SignedRequest signed = storage.presignUploadPart(
                objectKey, uploadId, partNumber, Duration.ofMinutes(5));
        HttpRequest request = signedRequest(signed)
                .method(signed.method(), HttpRequest.BodyPublishers.ofByteArray(content))
                .build();
        HttpResponse<byte[]> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode())
                .as("上传第%d片失败：%s", partNumber, new String(response.body()))
                .isBetween(200, 299);
        assertThat(response.headers().firstValue("ETag")).isPresent();
    }

    private HttpRequest.Builder signedRequest(SignedRequest signed) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(signed.url()))
                .timeout(Duration.ofMinutes(2));
        for (Map.Entry<String, String> header : signed.headers().entrySet()) {
            String name = header.getKey().toLowerCase(Locale.ROOT);
            if (!"host".equals(name) && !"content-length".equals(name)) {
                builder.header(header.getKey(), header.getValue());
            }
        }
        return builder;
    }

    private byte[] testMp4() {
        byte[] file = new byte[FIRST_PART_SIZE + SECOND_PART_SIZE];
        byte[] header = {0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
        System.arraycopy(header, 0, file, 0, header.length);
        for (int index = header.length; index < file.length; index++) {
            file[index] = (byte) (index % 251);
        }
        return file;
    }

    private String environment(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
