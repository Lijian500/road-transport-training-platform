package me.lj.train.training.storage;

import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.training.mapper.StorageObjectMapper;
import me.lj.train.training.mapper.PrivateImageUploadSessionMapper;
import me.lj.train.training.mapper.UploadSessionMapper;
import me.lj.train.training.model.entity.StorageObjectEntity;
import me.lj.train.training.model.entity.UploadSessionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDateTime;
import java.util.Collections;

import static me.lj.train.training.constant.TrainingConstants.OBJECT_PENDING_DELETE;
import static me.lj.train.training.constant.TrainingConstants.UPLOAD_INITIATED;
import static me.lj.train.training.constant.TrainingConstants.UPLOAD_VIDEO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageCleanupSchedulerTest {

    @Mock
    private UploadSessionMapper uploadSessionMapper;
    @Mock
    private PrivateImageUploadSessionMapper privateImageUploadSessionMapper;
    @Mock
    private StorageObjectMapper storageObjectMapper;
    @Mock
    private ObjectStorageService objectStorageService;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;

    private StorageCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new StorageCleanupScheduler(
                uploadSessionMapper,
                privateImageUploadSessionMapper,
                storageObjectMapper,
                objectStorageService,
                transactionManager);
    }

    @Test
    void shouldSkipCleanupWhenOssIsDisabled() {
        when(objectStorageService.isEnabled()).thenReturn(false);

        scheduler.cleanup();

        verify(uploadSessionMapper, never()).selectListByQuery(any(QueryWrapper.class));
        verify(privateImageUploadSessionMapper, never()).selectListByQuery(any(QueryWrapper.class));
        verify(storageObjectMapper, never()).selectListByQuery(any(QueryWrapper.class));
    }

    @Test
    void shouldAbortExpiredMultipartAndRetryPendingObjectDeletion() {
        when(objectStorageService.isEnabled()).thenReturn(true);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        UploadSessionEntity session = expiredVideoSession();
        StorageObjectEntity object = pendingObject();
        when(uploadSessionMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(session));
        when(storageObjectMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(object));
        when(objectStorageService.headObject(session.getObjectKey())).thenReturn(null);

        scheduler.cleanup();

        verify(objectStorageService).abortMultipartUpload("expired/video.mp4", "upload-expired");
        verify(objectStorageService).deleteObject("pending/old.mp4");
        verify(uploadSessionMapper).updateByCondition(any(UploadSessionEntity.class), any());
        verify(storageObjectMapper).updateByCondition(any(StorageObjectEntity.class), any());
    }

    @Test
    void shouldKeepPendingStateWhenOssDeletionFails() {
        when(objectStorageService.isEnabled()).thenReturn(true);
        when(uploadSessionMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(storageObjectMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(pendingObject()));
        doThrow(new IllegalStateException("temporary OSS failure"))
                .when(objectStorageService).deleteObject("pending/old.mp4");

        scheduler.cleanup();

        verify(storageObjectMapper, never()).updateByCondition(any(StorageObjectEntity.class), any());
        verify(transactionManager, never()).getTransaction(any(TransactionDefinition.class));
    }

    private UploadSessionEntity expiredVideoSession() {
        UploadSessionEntity session = new UploadSessionEntity();
        session.setId(400L);
        session.setUploadType(UPLOAD_VIDEO);
        session.setObjectKey("expired/video.mp4");
        session.setOssUploadId("upload-expired");
        session.setStatus(UPLOAD_INITIATED);
        session.setExpiresAt(LocalDateTime.now().minusHours(1));
        session.setUpdatedBy(10L);
        return session;
    }

    private StorageObjectEntity pendingObject() {
        StorageObjectEntity object = new StorageObjectEntity();
        object.setId(300L);
        object.setObjectKey("pending/old.mp4");
        object.setStatus(OBJECT_PENDING_DELETE);
        object.setUpdatedBy(10L);
        return object;
    }
}
