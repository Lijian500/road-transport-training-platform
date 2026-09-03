package me.lj.train.training.service;

import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.api.admin.FaceReferenceModels.FaceReferenceView;
import me.lj.train.api.training.PrivateImageModels.CreateFaceReferenceUploadSessionCommand;
import me.lj.train.api.training.PrivateImageModels.PrivateImageContentView;
import me.lj.train.api.training.PrivateImageModels.PrivateImageUploadSessionView;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;
import me.lj.train.training.config.OssStorageProperties;
import me.lj.train.training.mapper.PrivateImageUploadSessionMapper;
import me.lj.train.training.mapper.StorageObjectMapper;
import me.lj.train.training.model.entity.StorageObjectEntity;
import me.lj.train.training.storage.ObjectStorageService;
import me.lj.train.training.storage.ObjectStorageService.SignedRequest;
import me.lj.train.training.support.FaceReferenceDirectoryClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.Instant;
import java.util.Collections;

import static me.lj.train.training.constant.TrainingConstants.OBJECT_ACTIVE;
import static me.lj.train.training.constant.TrainingConstants.UPLOAD_FACE_REFERENCE;
import static me.lj.train.training.constant.TrainingPermissions.FACE_CHECK_MANAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrivateImageStorageServiceImplTest {

    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;
    @Mock private PrivateImageUploadSessionMapper uploadSessionMapper;
    @Mock private StorageObjectMapper storageObjectMapper;
    @Mock private ObjectStorageService objectStorageService;
    @Mock private FaceReferenceDirectoryClient directoryClient;

    private OssStorageProperties properties;
    private PrivateImageStorageServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new OssStorageProperties();
        service = new PrivateImageStorageServiceImpl(
                transactionManager, uploadSessionMapper, storageObjectMapper,
                objectStorageService, properties, directoryClient);
        UserContext.set(operator(10L, Collections.singletonList(FACE_CHECK_MANAGE)));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldCreatePrivateDirectUploadSession() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        when(objectStorageService.isEnabled()).thenReturn(true);
        when(objectStorageService.bucketName()).thenReturn("private-bucket");
        when(directoryClient.get(11L))
                .thenReturn(new FaceReferenceView(11L, null, false, null));
        when(objectStorageService.presignPut(any(String.class), any(String.class), any()))
                .thenReturn(new SignedRequest(
                        "https://oss.example/upload", "PUT", Collections.emptyMap(),
                        Instant.now().plusSeconds(900)));

        Result<PrivateImageUploadSessionView> result = service.createFaceReferenceUploadSession(
                new CreateFaceReferenceUploadSessionCommand(
                        11L, "face.jpg", "image/jpeg", 1024L, 1L));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().userId()).isEqualTo(11L);
        assertThat(result.getData().uploadRequest().method()).isEqualTo("PUT");
        verify(uploadSessionMapper).insertSelective(any());
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void shouldReadOnlyOwnersPrivateImageAndReturnDigest() {
        UserContext.set(operator(11L, Collections.emptyList()));
        when(objectStorageService.isEnabled()).thenReturn(true);
        StorageObjectEntity object = faceObject(11L);
        when(storageObjectMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(object);
        when(objectStorageService.readObject("users/11/face.jpg"))
                .thenReturn(new byte[] {1, 2, 3});

        Result<PrivateImageContentView> result = service.read(100L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().sha256())
                .isEqualTo("039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81");
    }

    @Test
    void shouldRejectOtherStudentReadingPrivateImage() {
        UserContext.set(operator(12L, Collections.emptyList()));
        when(objectStorageService.isEnabled()).thenReturn(true);
        when(storageObjectMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(faceObject(11L));

        Result<PrivateImageContentView> result = service.read(100L);

        assertThat(result.getCode()).isEqualTo(AppErrorCode.FORBIDDEN.getCode());
        verify(objectStorageService, never()).readObject(any(String.class));
    }

    private LoginUser operator(Long userId, java.util.List<String> permissions) {
        LoginUser user = new LoginUser();
        user.setUserId(userId);
        user.setEnterpriseId(20L);
        user.setPermissions(permissions);
        return user;
    }

    private StorageObjectEntity faceObject(Long ownerUserId) {
        StorageObjectEntity object = new StorageObjectEntity();
        object.setId(100L);
        object.setEnterpriseId(20L);
        object.setOwnerUserId(ownerUserId);
        object.setObjectKey("users/11/face.jpg");
        object.setObjectType(UPLOAD_FACE_REFERENCE);
        object.setContentType("image/jpeg");
        object.setFileSize(3L);
        object.setStatus(OBJECT_ACTIVE);
        return object;
    }
}
