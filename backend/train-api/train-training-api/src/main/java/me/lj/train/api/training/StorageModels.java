package me.lj.train.api.training;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 对象存储直传RPC请求及响应模型。
 */
public final class StorageModels {

    private StorageModels() {
    }

    public record CreateCoverUploadSessionCommand(
            Long courseId,
            String originalFilename,
            String contentType,
            long fileSizeBytes,
            Long clientLastModified) implements Serializable {
    }

    public record CreateCoursewareUploadSessionCommand(
            Long courseId,
            String title,
            String originalFilename,
            String contentType,
            long fileSizeBytes,
            Long clientLastModified,
            int durationSeconds) implements Serializable {
    }

    public record CreatePartUrlsCommand(
            Long sessionId,
            List<Integer> partNumbers) implements Serializable {
    }

    public record CoursewarePreviewCommand(Long courseId, Long coursewareId) implements Serializable {
    }

    public record StorageCapabilityView(
            boolean enabled,
            String message,
            String provider,
            long partSizeBytes,
            long maxVideoBytes,
            long maxCoverBytes,
            int uploadUrlTtlSeconds,
            int previewUrlTtlSeconds,
            List<String> videoContentTypes,
            List<String> coverContentTypes) implements Serializable {
    }

    public record SignedRequestView(
            Integer partNumber,
            String url,
            String method,
            Map<String, String> headers,
            LocalDateTime expiresAt) implements Serializable {
    }

    public record UploadSessionView(
            Long id,
            Long courseId,
            Long coursewareId,
            String uploadType,
            String originalFilename,
            long fileSizeBytes,
            Long clientLastModified,
            long partSizeBytes,
            int partCount,
            String status,
            LocalDateTime expiresAt,
            SignedRequestView uploadRequest) implements Serializable {
    }

    public record UploadedPartView(
            int partNumber,
            long sizeBytes,
            String etag,
            LocalDateTime lastModified) implements Serializable {
    }

    public record UploadCompleteView(
            Long sessionId,
            Long courseId,
            Long resourceId,
            Long storageObjectId,
            String uploadType,
            String status) implements Serializable {
    }
}
