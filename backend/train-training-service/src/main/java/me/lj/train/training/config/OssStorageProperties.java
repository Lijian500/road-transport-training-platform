package me.lj.train.training.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云OSS非敏感配置，AccessKey仅从进程环境变量读取。
 */
@ConfigurationProperties(prefix = "training.storage.oss")
public class OssStorageProperties {

    private boolean enabled;
    private String region = "cn-hangzhou";
    private String endpoint = "https://oss-cn-hangzhou.aliyuncs.com";
    private String bucket;
    private String objectPrefix = "road-training";
    private int uploadUrlTtlSeconds = 900;
    private int previewUrlTtlSeconds = 1800;
    private int learningUrlTtlSeconds = 900;
    private int uploadSessionHours = 24;
    private long partSizeBytes = 8_388_608L;
    private long maxVideoBytes = 5_368_709_120L;
    private long maxCoverBytes = 5_242_880L;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getObjectPrefix() { return objectPrefix; }
    public void setObjectPrefix(String value) { this.objectPrefix = value; }
    public int getUploadUrlTtlSeconds() { return uploadUrlTtlSeconds; }
    public void setUploadUrlTtlSeconds(int value) { this.uploadUrlTtlSeconds = value; }
    public int getPreviewUrlTtlSeconds() { return previewUrlTtlSeconds; }
    public void setPreviewUrlTtlSeconds(int value) { this.previewUrlTtlSeconds = value; }
    public int getLearningUrlTtlSeconds() { return learningUrlTtlSeconds; }
    public void setLearningUrlTtlSeconds(int value) { this.learningUrlTtlSeconds = value; }
    public int getUploadSessionHours() { return uploadSessionHours; }
    public void setUploadSessionHours(int value) { this.uploadSessionHours = value; }
    public long getPartSizeBytes() { return partSizeBytes; }
    public void setPartSizeBytes(long value) { this.partSizeBytes = value; }
    public long getMaxVideoBytes() { return maxVideoBytes; }
    public void setMaxVideoBytes(long value) { this.maxVideoBytes = value; }
    public long getMaxCoverBytes() { return maxCoverBytes; }
    public void setMaxCoverBytes(long value) { this.maxCoverBytes = value; }
}
