package me.lj.train.training.storage;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.PresignOptions;
import com.aliyun.sdk.service.oss2.credentials.StaticCredentialsProvider;
import com.aliyun.sdk.service.oss2.exceptions.ServiceException;
import com.aliyun.sdk.service.oss2.models.AbortMultipartUploadRequest;
import com.aliyun.sdk.service.oss2.models.CompleteMultipartUpload;
import com.aliyun.sdk.service.oss2.models.CompleteMultipartUploadRequest;
import com.aliyun.sdk.service.oss2.models.DeleteObjectRequest;
import com.aliyun.sdk.service.oss2.models.GetObjectRequest;
import com.aliyun.sdk.service.oss2.models.GetObjectResult;
import com.aliyun.sdk.service.oss2.models.HeadObjectRequest;
import com.aliyun.sdk.service.oss2.models.HeadObjectResult;
import com.aliyun.sdk.service.oss2.models.InitiateMultipartUploadRequest;
import com.aliyun.sdk.service.oss2.models.ListPartsRequest;
import com.aliyun.sdk.service.oss2.models.ListPartsResult;
import com.aliyun.sdk.service.oss2.models.Part;
import com.aliyun.sdk.service.oss2.models.PresignResult;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.models.UploadPartRequest;
import jakarta.annotation.PreDestroy;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.training.config.OssStorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 阿里云OSS Java SDK V2封装；密钥只读取标准进程环境变量。
 */
@Component
public class AliyunOssStorageService implements ObjectStorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AliyunOssStorageService.class);

    private final OssStorageProperties properties;
    private final OSSClient client;
    private final String disabledMessage;

    public AliyunOssStorageService(OssStorageProperties properties) {
        this.properties = properties;
        String accessKeyId = System.getenv("OSS_ACCESS_KEY_ID");
        String accessKeySecret = System.getenv("OSS_ACCESS_KEY_SECRET");
        if (!properties.isEnabled()) {
            this.client = null;
            this.disabledMessage = "阿里云OSS未启用，请配置OSS_ENABLED=true后重启服务";
        } else if (!StringUtils.hasText(properties.getRegion())
                || !StringUtils.hasText(properties.getEndpoint())
                || !StringUtils.hasText(properties.getBucket())
                || !StringUtils.hasText(accessKeyId)
                || !StringUtils.hasText(accessKeySecret)) {
            this.client = null;
            this.disabledMessage = "阿里云OSS配置不完整，请补充Bucket和AccessKey环境变量";
            LOGGER.warn("阿里云OSS已声明启用但配置不完整，课程上传能力保持禁用");
        } else {
            this.client = OSSClient.newBuilder()
                    .region(properties.getRegion())
                    .endpoint(properties.getEndpoint())
                    .credentialsProvider(new StaticCredentialsProvider(accessKeyId, accessKeySecret))
                    .build();
            this.disabledMessage = null;
        }
    }

    @Override
    public boolean isEnabled() {
        return client != null;
    }

    @Override
    public String disabledMessage() {
        return disabledMessage;
    }

    @Override
    public String bucketName() {
        requireEnabled();
        return properties.getBucket();
    }

    @Override
    public String initiateMultipartUpload(String objectKey, String contentType) {
        return execute("初始化分片上传", () -> client.initiateMultipartUpload(
                        InitiateMultipartUploadRequest.newBuilder()
                                .bucket(properties.getBucket())
                                .key(objectKey)
                                .contentType(contentType)
                                .forbidOverwrite(true)
                                .build())
                .initiateMultipartUpload()
                .uploadId());
    }

    @Override
    public SignedRequest presignPut(String objectKey, String contentType, Duration ttl) {
        return execute("生成上传签名", () -> toSignedRequest(client.presign(
                PutObjectRequest.newBuilder()
                        .bucket(properties.getBucket())
                        .key(objectKey)
                        .contentType(contentType)
                        .forbidOverwrite(true)
                        .build(),
                PresignOptions.newBuilder().expiration(ttl).build())));
    }

    @Override
    public SignedRequest presignUploadPart(
            String objectKey, String uploadId, int partNumber, Duration ttl) {
        return execute("生成分片上传签名", () -> toSignedRequest(client.presign(
                UploadPartRequest.newBuilder()
                        .bucket(properties.getBucket())
                        .key(objectKey)
                        .uploadId(uploadId)
                        .partNumber((long) partNumber)
                        .build(),
                PresignOptions.newBuilder().expiration(ttl).build())));
    }

    @Override
    public SignedRequest presignGet(String objectKey, Duration ttl) {
        return execute("生成预览签名", () -> toSignedRequest(client.presign(
                GetObjectRequest.newBuilder()
                        .bucket(properties.getBucket())
                        .key(objectKey)
                        .build(),
                PresignOptions.newBuilder().expiration(ttl).build())));
    }

    @Override
    public List<StoredPart> listParts(String objectKey, String uploadId) {
        return execute("查询已上传分片", () -> {
            List<StoredPart> parts = new ArrayList<>();
            Long marker = null;
            boolean truncated;
            do {
                ListPartsResult result = client.listParts(ListPartsRequest.newBuilder()
                        .bucket(properties.getBucket())
                        .key(objectKey)
                        .uploadId(uploadId)
                        .partNumberMarker(marker)
                        .maxParts(1000L)
                        .build());
                if (result.parts() != null) {
                    result.parts().forEach(part -> parts.add(new StoredPart(
                            part.partNumber().intValue(),
                            part.size(),
                            part.eTag(),
                            part.lastModified())));
                }
                truncated = Boolean.TRUE.equals(result.isTruncated());
                marker = result.nextPartNumberMarker();
            } while (truncated);
            parts.sort(Comparator.comparingInt(StoredPart::partNumber));
            return parts;
        });
    }

    @Override
    public void completeMultipartUpload(String objectKey, String uploadId, List<StoredPart> parts) {
        executeVoid("完成分片上传", () -> {
            List<Part> ossParts = parts.stream()
                    .sorted(Comparator.comparingInt(StoredPart::partNumber))
                    .map(part -> Part.newBuilder()
                            .partNumber((long) part.partNumber())
                            .eTag(part.etag())
                            .build())
                    .collect(Collectors.toList());
            client.completeMultipartUpload(CompleteMultipartUploadRequest.newBuilder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .uploadId(uploadId)
                    .forbidOverwrite(true)
                    .completeMultipartUpload(CompleteMultipartUpload.newBuilder().parts(ossParts).build())
                    .build());
        });
    }

    @Override
    public void abortMultipartUpload(String objectKey, String uploadId) {
        try {
            executeVoid("取消分片上传", () -> client.abortMultipartUpload(
                    AbortMultipartUploadRequest.newBuilder()
                            .bucket(properties.getBucket())
                            .key(objectKey)
                            .uploadId(uploadId)
                            .build()));
        } catch (BusinessException exception) {
            if (!isNotFound(exception.getCause())) {
                throw exception;
            }
        }
    }

    @Override
    public ObjectMetadata headObject(String objectKey) {
        requireEnabled();
        try {
            HeadObjectResult result = client.headObject(HeadObjectRequest.newBuilder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .build());
            return new ObjectMetadata(result.contentLength(), result.contentType(), result.eTag());
        } catch (ServiceException exception) {
            if (isNotFound(exception)) {
                return null;
            }
            throw storageException("读取对象元数据", exception);
        } catch (RuntimeException exception) {
            throw storageException("读取对象元数据", exception);
        }
    }

    @Override
    public byte[] readObjectPrefix(String objectKey, int length) {
        return execute("读取对象文件头", () -> {
            try (GetObjectResult result = client.getObject(GetObjectRequest.newBuilder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .range("bytes=0-" + (length - 1))
                    .build())) {
                return result.body().readNBytes(length);
            } catch (IOException exception) {
                throw storageException("读取对象文件头", exception);
            } catch (Exception exception) {
                throw storageException("关闭对象读取流", exception);
            }
        });
    }

    @Override
    public byte[] readObject(String objectKey) {
        return execute("读取私有对象", () -> {
            try (GetObjectResult result = client.getObject(GetObjectRequest.newBuilder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .build())) {
                return result.body().readAllBytes();
            } catch (IOException exception) {
                throw storageException("读取私有对象", exception);
            } catch (Exception exception) {
                throw storageException("关闭对象读取流", exception);
            }
        });
    }

    @Override
    public void deleteObject(String objectKey) {
        executeVoid("删除对象", () -> client.deleteObject(DeleteObjectRequest.newBuilder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .build()));
    }

    @PreDestroy
    public void close() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception exception) {
                LOGGER.warn("关闭阿里云OSS客户端失败", exception);
            }
        }
    }

    private SignedRequest toSignedRequest(PresignResult result) {
        Map<String, String> headers = result.signedHeaders().orElse(Collections.emptyMap());
        Instant expiresAt = result.expiration().orElse(Instant.now());
        return new SignedRequest(result.url(), result.method(), headers, expiresAt);
    }

    private void requireEnabled() {
        if (client == null) {
            throw new BusinessException(AppErrorCode.UPLOAD_DISABLED, disabledMessage);
        }
    }

    private <T> T execute(String operation, StorageSupplier<T> supplier) {
        requireEnabled();
        try {
            return supplier.get();
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw storageException(operation, exception);
        }
    }

    private void executeVoid(String operation, Runnable action) {
        execute(operation, () -> {
            action.run();
            return null;
        });
    }

    private BusinessException storageException(String operation, Throwable exception) {
        LOGGER.error("阿里云OSS{}失败", operation, exception);
        BusinessException businessException = new BusinessException(
                AppErrorCode.STORAGE_OPERATION_FAILED, operation + "失败，请稍后重试");
        businessException.initCause(exception);
        return businessException;
    }

    private boolean isNotFound(Throwable exception) {
        return exception instanceof ServiceException serviceException
                && serviceException.statusCode() == 404;
    }

    @FunctionalInterface
    private interface StorageSupplier<T> {
        T get();
    }
}
