package me.lj.train.api.admin;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户人脸登记照目录RPC模型。
 */
public final class FaceReferenceModels {

    private FaceReferenceModels() {
    }

    public record BindFaceReferenceCommand(
            Long userId,
            Long storageObjectId) implements Serializable {
    }

    public record FaceReferenceView(
            Long userId,
            Long storageObjectId,
            boolean enrolled,
            LocalDateTime updatedAt) implements Serializable {
    }

    /**
     * 返回变更前对象，调用方据此将旧OSS对象加入清理队列。
     */
    public record FaceReferenceChangeView(
            FaceReferenceView current,
            Long previousStorageObjectId) implements Serializable {
    }
}
