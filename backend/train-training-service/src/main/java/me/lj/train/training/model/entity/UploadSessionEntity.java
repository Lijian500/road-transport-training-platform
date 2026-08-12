package me.lj.train.training.model.entity;

import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 浏览器直传会话实体。
 */
@Table("train_upload_session")
public class UploadSessionEntity extends TrainingAuditEntity {

    private Long enterpriseId;
    private Long courseId;
    private Long storageObjectId;
    private Long coursewareId;
    private String uploadType;
    private String bucketName;
    private String objectKey;
    private String ossUploadId;
    private String originalFilename;
    private String expectedContentType;
    private long expectedFileSize;
    private Long clientLastModified;
    private Integer videoDurationSeconds;
    private String coursewareTitle;
    private long partSizeBytes;
    private int partCount;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime completedAt;

    public Long getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(Long value) { this.enterpriseId = value; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long value) { this.courseId = value; }
    public Long getStorageObjectId() { return storageObjectId; }
    public void setStorageObjectId(Long value) { this.storageObjectId = value; }
    public Long getCoursewareId() { return coursewareId; }
    public void setCoursewareId(Long value) { this.coursewareId = value; }
    public String getUploadType() { return uploadType; }
    public void setUploadType(String value) { this.uploadType = value; }
    public String getBucketName() { return bucketName; }
    public void setBucketName(String value) { this.bucketName = value; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String value) { this.objectKey = value; }
    public String getOssUploadId() { return ossUploadId; }
    public void setOssUploadId(String value) { this.ossUploadId = value; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String value) { this.originalFilename = value; }
    public String getExpectedContentType() { return expectedContentType; }
    public void setExpectedContentType(String value) { this.expectedContentType = value; }
    public long getExpectedFileSize() { return expectedFileSize; }
    public void setExpectedFileSize(long value) { this.expectedFileSize = value; }
    public Long getClientLastModified() { return clientLastModified; }
    public void setClientLastModified(Long value) { this.clientLastModified = value; }
    public Integer getVideoDurationSeconds() { return videoDurationSeconds; }
    public void setVideoDurationSeconds(Integer value) { this.videoDurationSeconds = value; }
    public String getCoursewareTitle() { return coursewareTitle; }
    public void setCoursewareTitle(String value) { this.coursewareTitle = value; }
    public long getPartSizeBytes() { return partSizeBytes; }
    public void setPartSizeBytes(long value) { this.partSizeBytes = value; }
    public int getPartCount() { return partCount; }
    public void setPartCount(int value) { this.partCount = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime value) { this.expiresAt = value; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime value) { this.completedAt = value; }
}
