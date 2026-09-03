package me.lj.train.training.model.entity;

import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 私有图片浏览器直传会话。
 */
@Table("train_private_image_upload_session")
public class PrivateImageUploadSessionEntity extends TrainingAuditEntity {

    private Long enterpriseId;
    private Long ownerUserId;
    private Long storageObjectId;
    private String uploadType;
    private String bucketName;
    private String objectKey;
    private String originalFilename;
    private String expectedContentType;
    private long expectedFileSize;
    private Long clientLastModified;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime completedAt;

    public Long getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(Long value) { this.enterpriseId = value; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long value) { this.ownerUserId = value; }
    public Long getStorageObjectId() { return storageObjectId; }
    public void setStorageObjectId(Long value) { this.storageObjectId = value; }
    public String getUploadType() { return uploadType; }
    public void setUploadType(String value) { this.uploadType = value; }
    public String getBucketName() { return bucketName; }
    public void setBucketName(String value) { this.bucketName = value; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String value) { this.objectKey = value; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String value) { this.originalFilename = value; }
    public String getExpectedContentType() { return expectedContentType; }
    public void setExpectedContentType(String value) { this.expectedContentType = value; }
    public long getExpectedFileSize() { return expectedFileSize; }
    public void setExpectedFileSize(long value) { this.expectedFileSize = value; }
    public Long getClientLastModified() { return clientLastModified; }
    public void setClientLastModified(Long value) { this.clientLastModified = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime value) { this.expiresAt = value; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime value) { this.completedAt = value; }
}
