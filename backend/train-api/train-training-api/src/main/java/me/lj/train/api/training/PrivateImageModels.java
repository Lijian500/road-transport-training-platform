package me.lj.train.api.training;

import me.lj.train.api.training.StorageModels.SignedRequestView;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 私有图片直传及受控读取RPC模型。
 */
public final class PrivateImageModels {

    private PrivateImageModels() {
    }

    public record CreateFaceReferenceUploadSessionCommand(
            Long userId,
            String originalFilename,
            String contentType,
            long fileSizeBytes,
            Long clientLastModified) implements Serializable {
    }

    public record PrivateImageUploadSessionView(
            Long id,
            Long userId,
            Long storageObjectId,
            String originalFilename,
            long fileSizeBytes,
            String status,
            LocalDateTime expiresAt,
            SignedRequestView uploadRequest) implements Serializable {
    }

    public record PrivateImageUploadCompleteView(
            Long sessionId,
            Long userId,
            Long storageObjectId,
            String status) implements Serializable {
    }

    public record PrivateImageContentView(
            Long storageObjectId,
            String contentType,
            byte[] content,
            String sha256) implements Serializable {
    }
}
