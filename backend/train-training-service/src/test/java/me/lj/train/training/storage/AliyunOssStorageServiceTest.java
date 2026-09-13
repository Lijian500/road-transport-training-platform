package me.lj.train.training.storage;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.exceptions.OperationException;
import com.aliyun.sdk.service.oss2.exceptions.ServiceException;
import com.aliyun.sdk.service.oss2.models.AbortMultipartUploadRequest;
import com.aliyun.sdk.service.oss2.models.HeadObjectRequest;
import com.aliyun.sdk.service.oss2.models.ListPartsRequest;
import com.aliyun.sdk.service.oss2.models.ListPartsResult;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.training.config.OssStorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.argThat;

/** 验证SDK V2包装异常不会阻断首次合并，也不会掩盖权限或网络故障。 */
class AliyunOssStorageServiceTest {

    private final OSSClient client = mock(OSSClient.class);
    private final AliyunOssStorageService storage = createStorage();

    /** 注入模拟客户端，不依赖本机OSS密钥或真实网络。 */
    private AliyunOssStorageService createStorage() {
        OssStorageProperties properties = new OssStorageProperties();
        properties.setEnabled(false);
        properties.setBucket("test-bucket");
        AliyunOssStorageService service = new AliyunOssStorageService(properties);
        ReflectionTestUtils.setField(service, "client", client);
        return service;
    }

    /** 合并前对象尚不存在，直接和包装的404都应返回空元数据。 */
    @Test
    void shouldReturnNullForMissingObject() {
        ServiceException missing = ServiceException.newBuilder().statusCode(404).build();
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(missing)
                .thenThrow(new OperationException("HeadObject", missing));
        assertThat(storage.headObject("video.mp4")).isNull();
        assertThat(storage.headObject("video.mp4")).isNull();
    }

    /** 403和网络故障必须继续报错，不能视作对象不存在。 */
    @Test
    void shouldPreservePermissionAndNetworkFailures() {
        OperationException forbidden = new OperationException("HeadObject",
                ServiceException.newBuilder().statusCode(403).build());
        OperationException network = new OperationException("HeadObject",
                new IllegalStateException("connection failed"));
        when(client.headObject(any(HeadObjectRequest.class))).thenThrow(forbidden).thenThrow(network);
        assertThatThrownBy(() -> storage.headObject("video.mp4"))
                .isInstanceOf(BusinessException.class).hasCause(forbidden);
        assertThatThrownBy(() -> storage.headObject("video.mp4"))
                .isInstanceOf(BusinessException.class).hasCause(network);
    }

    /** 重复取消已失效的上传会话应保持幂等。 */
    @Test
    void shouldIgnoreWrappedMissingUploadOnAbort() {
        when(client.abortMultipartUpload(any(AbortMultipartUploadRequest.class)))
                .thenThrow(new OperationException("AbortMultipartUpload",
                        ServiceException.newBuilder().statusCode(404).build()));
        assertThatCode(() -> storage.abortMultipartUpload("video.mp4", "upload-id"))
                .doesNotThrowAnyException();
    }

    /** 首页分页标记不能传null，否则SDK在发出请求前就抛出空指针异常。 */
    @Test
    void shouldListFirstPageFromZero() {
        when(client.listParts(any(ListPartsRequest.class)))
                .thenReturn(mock(ListPartsResult.class));
        assertThat(storage.listParts("video.mp4", "upload-id")).isEmpty();
        verify(client).listParts(argThat((ListPartsRequest request) -> request.partNumberMarker() == 0L));
    }
}
