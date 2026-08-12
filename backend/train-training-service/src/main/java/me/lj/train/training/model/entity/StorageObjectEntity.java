package me.lj.train.training.model.entity;

import com.mybatisflex.annotation.Table;

/**
 * OSS对象元数据实体。
 */
@Table("train_storage_object")
public class StorageObjectEntity extends TrainingAuditEntity {

    private Long enterpriseId;
    private String provider;
    private String bucketName;
    private String objectKey;
    private String originalFilename;
    private String objectType;
    private String contentType;
    private long fileSize;
    private String etag;
    private String status;

    public Long getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(Long enterpriseId) { this.enterpriseId = enterpriseId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getBucketName() { return bucketName; }
    public void setBucketName(String bucketName) { this.bucketName = bucketName; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String value) { this.originalFilename = value; }
    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public String getEtag() { return etag; }
    public void setEtag(String etag) { this.etag = etag; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
