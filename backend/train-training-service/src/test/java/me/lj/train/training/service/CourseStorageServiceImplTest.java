package me.lj.train.training.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateWrapper;
import me.lj.train.api.training.StorageModels.CreateCoverUploadSessionCommand;
import me.lj.train.api.training.StorageModels.CreatePartUrlsCommand;
import me.lj.train.api.training.StorageModels.UploadCompleteView;
import me.lj.train.api.training.StorageModels.StorageCapabilityView;
import me.lj.train.api.training.StorageModels.UploadedPartView;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;
import me.lj.train.training.config.OssStorageProperties;
import me.lj.train.training.mapper.CourseMapper;
import me.lj.train.training.mapper.CoursewareMapper;
import me.lj.train.training.mapper.StorageObjectMapper;
import me.lj.train.training.mapper.UploadSessionMapper;
import me.lj.train.training.model.entity.CourseEntity;
import me.lj.train.training.model.entity.CoursewareEntity;
import me.lj.train.training.model.entity.StorageObjectEntity;
import me.lj.train.training.model.entity.UploadSessionEntity;
import me.lj.train.training.storage.ObjectStorageService;
import me.lj.train.training.storage.ObjectStorageService.ObjectMetadata;
import me.lj.train.training.storage.ObjectStorageService.StoredPart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static me.lj.train.training.constant.TrainingConstants.COURSE_DRAFT;
import static me.lj.train.training.constant.TrainingConstants.OBJECT_PENDING_DELETE;
import static me.lj.train.training.constant.TrainingConstants.OBJECT_RETAINED;
import static me.lj.train.training.constant.TrainingConstants.UPLOAD_CANCELLED;
import static me.lj.train.training.constant.TrainingConstants.UPLOAD_COMPLETED;
import static me.lj.train.training.constant.TrainingConstants.UPLOAD_COVER;
import static me.lj.train.training.constant.TrainingConstants.UPLOAD_INITIATED;
import static me.lj.train.training.constant.TrainingConstants.UPLOAD_VIDEO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseStorageServiceImplTest {

    private static final byte[] MP4_HEADER = {
            0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'
    };

    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;
    @Mock
    private CourseMapper courseMapper;
    @Mock
    private CoursewareMapper coursewareMapper;
    @Mock
    private StorageObjectMapper storageObjectMapper;
    @Mock
    private UploadSessionMapper uploadSessionMapper;
    @Mock
    private ObjectStorageService objectStorageService;

    private CourseStorageServiceImpl service;
    private OssStorageProperties properties;

    @BeforeEach
    void setUp() {
        properties = new OssStorageProperties();
        properties.setBucket("course-bucket");
        service = new CourseStorageServiceImpl(
                transactionManager,
                courseMapper,
                coursewareMapper,
                storageObjectMapper,
                uploadSessionMapper,
                objectStorageService,
                properties);
        UserContext.set(enterpriseAdministrator());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldExposeDisabledCapabilityWithoutTouchingCourseData() {
        when(objectStorageService.isEnabled()).thenReturn(false);
        when(objectStorageService.disabledMessage()).thenReturn("OSS配置不完整");

        Result<StorageCapabilityView> result = service.capability();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().enabled()).isFalse();
        assertThat(result.getData().message()).isEqualTo("OSS配置不完整");
        verifyNoInteractions(courseMapper, coursewareMapper, storageObjectMapper, uploadSessionMapper);
    }

    @Test
    void shouldReturnExplicitErrorWhenCreatingUploadWhileStorageIsDisabled() {
        when(objectStorageService.isEnabled()).thenReturn(false);
        when(objectStorageService.disabledMessage()).thenReturn("OSS未配置");

        Result<?> result = service.createCoverUploadSession(
                new CreateCoverUploadSessionCommand(
                        100L, "cover.png", "image/png", 128L, 1234L));

        assertThat(result.getCode()).isEqualTo(AppErrorCode.UPLOAD_DISABLED.getCode());
        assertThat(result.getMessage()).contains("OSS未配置");
        verifyNoInteractions(courseMapper, coursewareMapper, storageObjectMapper, uploadSessionMapper);
    }

    @Test
    void shouldReturnCompletedSessionIdempotentlyWithoutCallingOssAgain() {
        when(objectStorageService.isEnabled()).thenReturn(true);
        UploadSessionEntity completed = uploadSession(UPLOAD_COMPLETED, 2, 8L, 12L);
        when(uploadSessionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(completed);

        Result<UploadCompleteView> result = service.complete(400L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().status()).isEqualTo(UPLOAD_COMPLETED);
        verify(objectStorageService, never()).headObject(any());
        verifyNoInteractions(courseMapper, coursewareMapper, storageObjectMapper, transactionManager);
    }

    @Test
    void shouldBindDatabaseMetadataWhenOssWasAlreadyMergedBeforeRetry() {
        when(objectStorageService.isEnabled()).thenReturn(true);
        beginTransaction();
        UploadSessionEntity active = uploadSession(UPLOAD_INITIATED, 2, 8L, 12L);
        when(uploadSessionMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(active, active);
        when(courseMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(editableCourse(), editableCourse());
        ObjectMetadata metadata = new ObjectMetadata(12L, "video/mp4", "merged-etag");
        when(objectStorageService.headObject("courses/video.mp4")).thenReturn(metadata);
        when(objectStorageService.readObjectPrefix("courses/video.mp4", 32))
                .thenReturn(MP4_HEADER);
        when(storageObjectMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
        when(coursewareMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
        when(coursewareMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        Result<UploadCompleteView> result = service.complete(400L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().resourceId()).isEqualTo(500L);
        verify(objectStorageService, never()).listParts(any(), any());
        verify(objectStorageService, never()).completeMultipartUpload(any(), any(), any());
        ArgumentCaptor<StorageObjectEntity> objectCaptor =
                ArgumentCaptor.forClass(StorageObjectEntity.class);
        verify(storageObjectMapper).insertSelective(objectCaptor.capture());
        assertThat(objectCaptor.getValue().getEtag()).isEqualTo("merged-etag");
        verify(coursewareMapper).insertSelective(any(CoursewareEntity.class));
        verify(uploadSessionMapper).updateByCondition(any(UploadSessionEntity.class), any());
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void shouldRejectIncompleteOrWrongSizedMultipartParts() {
        when(objectStorageService.isEnabled()).thenReturn(true);
        UploadSessionEntity active = uploadSession(UPLOAD_INITIATED, 2, 8L, 12L);
        when(uploadSessionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(active);
        when(courseMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(editableCourse());
        when(objectStorageService.headObject("courses/video.mp4")).thenReturn(null);
        when(objectStorageService.listParts("courses/video.mp4", "upload-1"))
                .thenReturn(List.of(new StoredPart(1, 7L, "etag-1", Instant.now())));

        Result<?> result = service.complete(400L);

        assertThat(result.getCode()).isEqualTo(AppErrorCode.STORAGE_OBJECT_INVALID.getCode());
        verify(objectStorageService, never()).completeMultipartUpload(any(), any(), any());
        verifyNoInteractions(storageObjectMapper, coursewareMapper, transactionManager);
    }

    @Test
    void shouldRejectPartNumberOutsideSessionRange() {
        when(objectStorageService.isEnabled()).thenReturn(true);
        when(uploadSessionMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(uploadSession(UPLOAD_INITIATED, 2, 8L, 12L));

        Result<?> result = service.createPartUrls(
                new CreatePartUrlsCommand(400L, List.of(0, 3)));

        assertThat(result.getCode()).isEqualTo(AppErrorCode.PARAM_INVALID.getCode());
        verify(objectStorageService, never())
                .presignUploadPart(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void shouldReturnCompletePartViewWhenOssObjectWasAlreadyMerged() {
        when(objectStorageService.isEnabled()).thenReturn(true);
        when(uploadSessionMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(uploadSession(UPLOAD_INITIATED, 2, 8L, 12L));
        when(objectStorageService.headObject("courses/video.mp4"))
                .thenReturn(new ObjectMetadata(12L, "video/mp4", "merged-etag"));

        Result<List<UploadedPartView>> result = service.listParts(400L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData())
                .extracting(UploadedPartView::partNumber)
                .containsExactly(1, 2);
        assertThat(result.getData())
                .extracting(UploadedPartView::sizeBytes)
                .containsExactly(8L, 4L);
        verify(objectStorageService, never()).listParts(any(), any());
    }

    @Test
    void shouldQueueInvalidObjectWhenImmediateDeletionFails() {
        when(objectStorageService.isEnabled()).thenReturn(true);
        beginTransaction();
        UploadSessionEntity active = uploadSession(UPLOAD_INITIATED, 2, 8L, 12L);
        when(uploadSessionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(active);
        when(courseMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(editableCourse());
        ObjectMetadata metadata = new ObjectMetadata(12L, "video/mp4", "invalid-etag");
        when(objectStorageService.headObject("courses/video.mp4")).thenReturn(metadata);
        when(objectStorageService.readObjectPrefix("courses/video.mp4", 32))
                .thenReturn(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
        doThrow(new IllegalStateException("temporary failure"))
                .when(objectStorageService).deleteObject("courses/video.mp4");
        when(storageObjectMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);

        Result<?> result = service.complete(400L);

        assertThat(result.getCode()).isEqualTo(AppErrorCode.STORAGE_OBJECT_INVALID.getCode());
        ArgumentCaptor<StorageObjectEntity> captor =
                ArgumentCaptor.forClass(StorageObjectEntity.class);
        verify(storageObjectMapper).insertSelective(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OBJECT_PENDING_DELETE);
        ArgumentCaptor<UploadSessionEntity> sessionCaptor =
                ArgumentCaptor.forClass(UploadSessionEntity.class);
        verify(uploadSessionMapper).updateByCondition(sessionCaptor.capture(), any());
        assertThat(((UpdateWrapper<?>) sessionCaptor.getValue()).getUpdates())
                .containsEntry("status", UPLOAD_CANCELLED);
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void shouldKeepUploadedObjectWhenReadingHeaderTemporarilyFails() {
        when(objectStorageService.isEnabled()).thenReturn(true);
        UploadSessionEntity active = uploadSession(UPLOAD_INITIATED, 2, 8L, 12L);
        when(uploadSessionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(active);
        when(courseMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(editableCourse());
        when(objectStorageService.headObject("courses/video.mp4"))
                .thenReturn(new ObjectMetadata(12L, "video/mp4", "etag"));
        me.lj.train.common.core.exception.BusinessException storageFailure =
                new me.lj.train.common.core.exception.BusinessException(
                        AppErrorCode.STORAGE_OPERATION_FAILED, "读取对象文件头失败，请稍后重试");
        doThrow(storageFailure).when(objectStorageService)
                .readObjectPrefix("courses/video.mp4", 32);

        Result<?> result = service.complete(400L);

        assertThat(result.getCode()).isEqualTo(AppErrorCode.STORAGE_OPERATION_FAILED.getCode());
        verify(objectStorageService, never()).deleteObject(any());
        verifyNoInteractions(storageObjectMapper, coursewareMapper, transactionManager);
        verify(uploadSessionMapper, never()).updateByCondition(any(UploadSessionEntity.class), any());
    }

    @Test
    void shouldAbortAndDeleteMergedObjectWhenCancellingVideoSession() {
        when(objectStorageService.isEnabled()).thenReturn(true);
        beginTransaction();
        UploadSessionEntity active = uploadSession(UPLOAD_INITIATED, 2, 8L, 12L);
        when(uploadSessionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(active);
        when(objectStorageService.headObject("courses/video.mp4"))
                .thenReturn(new ObjectMetadata(12L, "video/mp4", "etag"));

        Result<?> result = service.cancel(400L);

        assertThat(result.isSuccess()).isTrue();
        verify(objectStorageService).abortMultipartUpload("courses/video.mp4", "upload-1");
        verify(objectStorageService).deleteObject("courses/video.mp4");
        ArgumentCaptor<UploadSessionEntity> captor =
                ArgumentCaptor.forClass(UploadSessionEntity.class);
        verify(uploadSessionMapper).updateByCondition(captor.capture(), any());
        assertThat(((UpdateWrapper<?>) captor.getValue()).getUpdates())
                .containsEntry("status", UPLOAD_CANCELLED);
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void shouldRetainOldCoverWhenReplacingCoverOfHistoricalCourse() {
        when(objectStorageService.isEnabled()).thenReturn(true);
        beginTransaction();
        UploadSessionEntity active = coverSession();
        CourseEntity course = editableCourse();
        course.setEverEnabled(true);
        course.setCoverObjectId(250L);
        when(uploadSessionMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(active, active);
        when(courseMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(course, course);
        ObjectMetadata metadata = new ObjectMetadata(12L, "image/png", "cover-etag");
        when(objectStorageService.headObject("courses/cover.png")).thenReturn(metadata);
        when(objectStorageService.readObjectPrefix("courses/cover.png", 32)).thenReturn(
                new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0});
        when(storageObjectMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);

        Result<UploadCompleteView> result = service.complete(401L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().resourceId()).isEqualTo(100L);
        verify(storageObjectMapper).insertSelective(any(StorageObjectEntity.class));
        ArgumentCaptor<StorageObjectEntity> oldCoverCaptor =
                ArgumentCaptor.forClass(StorageObjectEntity.class);
        verify(storageObjectMapper).updateByCondition(oldCoverCaptor.capture(), any());
        assertThat(((UpdateWrapper<?>) oldCoverCaptor.getValue()).getUpdates())
                .containsEntry("status", OBJECT_RETAINED);
        ArgumentCaptor<CourseEntity> courseCaptor = ArgumentCaptor.forClass(CourseEntity.class);
        verify(courseMapper).updateByCondition(courseCaptor.capture(), any());
        assertThat(((UpdateWrapper<?>) courseCaptor.getValue()).getUpdates())
                .containsEntry("cover_object_id", 301L);
        verify(transactionManager).commit(transactionStatus);
    }

    private void beginTransaction() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
    }

    private LoginUser enterpriseAdministrator() {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(10L);
        loginUser.setEnterpriseId(20L);
        loginUser.setPermissions(Collections.singletonList("*"));
        return loginUser;
    }

    private CourseEntity editableCourse() {
        CourseEntity course = new CourseEntity();
        course.setId(100L);
        course.setEnterpriseId(20L);
        course.setCourseName("安全驾驶");
        course.setStatus(COURSE_DRAFT);
        return course;
    }

    private UploadSessionEntity uploadSession(
            String status, int partCount, long partSizeBytes, long fileSizeBytes) {
        UploadSessionEntity session = new UploadSessionEntity();
        session.setId(400L);
        session.setEnterpriseId(20L);
        session.setCourseId(100L);
        session.setStorageObjectId(300L);
        session.setCoursewareId(500L);
        session.setUploadType(UPLOAD_VIDEO);
        session.setBucketName("course-bucket");
        session.setObjectKey("courses/video.mp4");
        session.setOssUploadId("upload-1");
        session.setOriginalFilename("video.mp4");
        session.setExpectedContentType("video/mp4");
        session.setExpectedFileSize(fileSizeBytes);
        session.setVideoDurationSeconds(60);
        session.setCoursewareTitle("第一章");
        session.setPartSizeBytes(partSizeBytes);
        session.setPartCount(partCount);
        session.setStatus(status);
        session.setExpiresAt(LocalDateTime.now().plusHours(1));
        session.setCreatedBy(10L);
        session.setUpdatedBy(10L);
        return session;
    }

    private UploadSessionEntity coverSession() {
        UploadSessionEntity session = new UploadSessionEntity();
        session.setId(401L);
        session.setEnterpriseId(20L);
        session.setCourseId(100L);
        session.setStorageObjectId(301L);
        session.setUploadType(UPLOAD_COVER);
        session.setBucketName("course-bucket");
        session.setObjectKey("courses/cover.png");
        session.setOriginalFilename("cover.png");
        session.setExpectedContentType("image/png");
        session.setExpectedFileSize(12L);
        session.setPartSizeBytes(8L);
        session.setPartCount(1);
        session.setStatus(UPLOAD_INITIATED);
        session.setExpiresAt(LocalDateTime.now().plusHours(1));
        session.setCreatedBy(10L);
        session.setUpdatedBy(10L);
        return session;
    }
}
