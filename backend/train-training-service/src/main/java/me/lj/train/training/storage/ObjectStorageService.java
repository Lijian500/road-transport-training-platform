package me.lj.train.training.storage;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 课程文件对象存储抽象。
 */
public interface ObjectStorageService {

    boolean isEnabled();

    String disabledMessage();

    String bucketName();

    String initiateMultipartUpload(String objectKey, String contentType);

    SignedRequest presignPut(String objectKey, String contentType, Duration ttl);

    SignedRequest presignUploadPart(
            String objectKey, String uploadId, int partNumber, Duration ttl);

    SignedRequest presignGet(String objectKey, Duration ttl);

    List<StoredPart> listParts(String objectKey, String uploadId);

    void completeMultipartUpload(String objectKey, String uploadId, List<StoredPart> parts);

    void abortMultipartUpload(String objectKey, String uploadId);

    ObjectMetadata headObject(String objectKey);

    byte[] readObjectPrefix(String objectKey, int length);

    byte[] readObject(String objectKey);

    void deleteObject(String objectKey);

    record SignedRequest(
            String url,
            String method,
            Map<String, String> headers,
            Instant expiresAt) {
    }

    record StoredPart(
            int partNumber,
            long sizeBytes,
            String etag,
            Instant lastModified) {
    }

    record ObjectMetadata(
            long sizeBytes,
            String contentType,
            String etag) {
    }
}
