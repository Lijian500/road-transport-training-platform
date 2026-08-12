package me.lj.train.training.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateWrapper;
import me.lj.train.api.training.CourseModels.ChangeCourseStatusCommand;
import me.lj.train.api.training.CourseModels.DeleteCoursewareCommand;
import me.lj.train.api.training.CourseModels.ReorderCoursewaresCommand;
import me.lj.train.api.training.CourseModels.UpdateCourseCommand;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;
import me.lj.train.training.mapper.CourseMapper;
import me.lj.train.training.mapper.CoursewareMapper;
import me.lj.train.training.mapper.StorageObjectMapper;
import me.lj.train.training.mapper.UploadSessionMapper;
import me.lj.train.training.model.entity.CourseEntity;
import me.lj.train.training.model.entity.CoursewareEntity;
import me.lj.train.training.model.entity.StorageObjectEntity;
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

import java.util.Collections;
import java.util.Arrays;
import java.util.List;

import static me.lj.train.training.constant.TrainingConstants.COURSE_DISABLED;
import static me.lj.train.training.constant.TrainingConstants.COURSE_DRAFT;
import static me.lj.train.training.constant.TrainingConstants.COURSE_ENABLED;
import static me.lj.train.training.constant.TrainingConstants.OBJECT_PENDING_DELETE;
import static me.lj.train.training.constant.TrainingConstants.OBJECT_RETAINED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceImplTest {

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

    private CourseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CourseServiceImpl(
                transactionManager,
                courseMapper,
                coursewareMapper,
                storageObjectMapper,
                uploadSessionMapper);
        UserContext.set(enterpriseAdministrator());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldRejectPlatformAdministratorBeforeReadingEnterpriseCourse() {
        LoginUser platformAdministrator = enterpriseAdministrator();
        platformAdministrator.setEnterpriseId(null);
        platformAdministrator.setPlatformAdmin(true);
        UserContext.set(platformAdministrator);

        Result<?> result = service.get(100L);

        assertThat(result.getCode()).isEqualTo(AppErrorCode.DATA_SCOPE_VIOLATION.getCode());
        verifyNoInteractions(courseMapper, coursewareMapper, storageObjectMapper, uploadSessionMapper);
    }

    @Test
    void shouldRejectCrossEnterpriseCourseReturnedByMapper() {
        CourseEntity foreignCourse = course(COURSE_DRAFT, false, 60);
        foreignCourse.setEnterpriseId(21L);
        when(courseMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(foreignCourse);

        Result<?> result = service.get(100L);

        assertThat(result.getCode()).isEqualTo(AppErrorCode.DATA_SCOPE_VIOLATION.getCode());
        verifyNoInteractions(coursewareMapper, storageObjectMapper, uploadSessionMapper);
    }

    @Test
    void shouldKeepEnabledCourseReadOnly() {
        beginTransaction();
        when(courseMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(course(COURSE_ENABLED, true, 60));

        Result<?> result = service.update(new UpdateCourseCommand(
                100L, "更新后的课程", null, 60, false, 20, 30));

        assertThat(result.getCode()).isEqualTo(AppErrorCode.COURSE_STATE_INVALID.getCode());
        verify(courseMapper, never()).updateByCondition(any(CourseEntity.class), any());
        verify(transactionManager).rollback(transactionStatus);
    }

    @Test
    void shouldEnableDraftWithReadyVideoAndNoActiveUpload() {
        beginTransaction();
        CourseEntity draft = course(COURSE_DRAFT, false, 60);
        CourseEntity enabled = course(COURSE_ENABLED, true, 60);
        CoursewareEntity courseware = courseware(201L, 301L, 90);
        when(courseMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(draft, enabled);
        when(coursewareMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(courseware));
        when(uploadSessionMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        when(storageObjectMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(storageObject(301L));

        Result<?> result = service.changeStatus(new ChangeCourseStatusCommand(100L, COURSE_ENABLED));

        assertThat(result.isSuccess()).isTrue();
        assertThat(((me.lj.train.api.training.CourseModels.CourseView) result.getData()).status())
                .isEqualTo(COURSE_ENABLED);
        ArgumentCaptor<CourseEntity> captor = ArgumentCaptor.forClass(CourseEntity.class);
        verify(courseMapper).updateByCondition(captor.capture(), any());
        assertThat(((UpdateWrapper<?>) captor.getValue()).getUpdates())
                .containsEntry("status", COURSE_ENABLED)
                .containsEntry("ever_enabled", true);
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void shouldRejectEnableWhenRequiredDurationExceedsReadyVideos() {
        beginTransaction();
        when(courseMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(course(COURSE_DRAFT, false, 120));
        when(coursewareMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(courseware(201L, 301L, 90)));

        Result<?> result = service.changeStatus(new ChangeCourseStatusCommand(100L, COURSE_ENABLED));

        assertThat(result.getCode()).isEqualTo(AppErrorCode.COURSE_ENABLE_INVALID.getCode());
        verify(courseMapper, never()).updateByCondition(any(CourseEntity.class), any());
        verifyNoInteractions(uploadSessionMapper, storageObjectMapper);
        verify(transactionManager).rollback(transactionStatus);
    }

    @Test
    void shouldRejectEnableWhileUploadSessionIsIncomplete() {
        beginTransaction();
        when(courseMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(course(COURSE_DISABLED, true, 60));
        when(coursewareMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(courseware(201L, 301L, 90)));
        when(uploadSessionMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);

        Result<?> result = service.changeStatus(new ChangeCourseStatusCommand(100L, COURSE_ENABLED));

        assertThat(result.getCode()).isEqualTo(AppErrorCode.COURSE_ENABLE_INVALID.getCode());
        verify(courseMapper, never()).updateByCondition(any(CourseEntity.class), any());
        verify(transactionManager).rollback(transactionStatus);
    }

    @Test
    void shouldQueueDraftCoverAndCoursewareObjectsWhenDeletingCourse() {
        beginTransaction();
        CourseEntity draft = course(COURSE_DRAFT, false, 60);
        draft.setCoverObjectId(302L);
        when(courseMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(draft);
        when(coursewareMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(courseware(201L, 301L, 90)));

        Result<?> result = service.delete(100L);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<StorageObjectEntity> objectCaptor =
                ArgumentCaptor.forClass(StorageObjectEntity.class);
        verify(storageObjectMapper, org.mockito.Mockito.times(2))
                .updateByCondition(objectCaptor.capture(), any());
        assertThat(objectCaptor.getAllValues())
                .allSatisfy(object -> assertThat(((UpdateWrapper<?>) object).getUpdates())
                        .containsEntry("status", OBJECT_PENDING_DELETE));
        verify(coursewareMapper).updateByCondition(any(CoursewareEntity.class), any());
        verify(uploadSessionMapper).updateByCondition(any(), any());
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void shouldRetainHistoricalObjectWhenDeletingCoursewareFromDisabledCourse() {
        beginTransaction();
        when(courseMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(course(COURSE_DISABLED, true, 60));
        when(coursewareMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(courseware(201L, 301L, 90));

        Result<?> result = service.deleteCourseware(new DeleteCoursewareCommand(100L, 201L));

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<StorageObjectEntity> captor =
                ArgumentCaptor.forClass(StorageObjectEntity.class);
        verify(storageObjectMapper).updateByCondition(captor.capture(), any());
        assertThat(((UpdateWrapper<?>) captor.getValue()).getUpdates())
                .containsEntry("status", OBJECT_RETAINED);
        verify(coursewareMapper).updateByCondition(any(CoursewareEntity.class), any());
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void shouldPersistCompleteCoursewareOrder() {
        beginTransaction();
        when(courseMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(course(COURSE_DISABLED, true, 60));
        when(coursewareMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Arrays.asList(
                courseware(201L, 301L, 60), courseware(202L, 302L, 60)));

        Result<?> result = service.reorderCoursewares(
                new ReorderCoursewaresCommand(100L, Arrays.asList(202L, 201L)));

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<CoursewareEntity> captor = ArgumentCaptor.forClass(CoursewareEntity.class);
        verify(coursewareMapper, org.mockito.Mockito.times(2))
                .updateByCondition(captor.capture(), any());
        assertThat(captor.getAllValues())
                .extracting(value -> ((UpdateWrapper<?>) value).getUpdates().get("sort_order"))
                .containsExactly(1, 2);
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

    private CourseEntity course(String status, boolean everEnabled, int requiredDurationSeconds) {
        CourseEntity course = new CourseEntity();
        course.setId(100L);
        course.setEnterpriseId(20L);
        course.setCourseName("安全驾驶");
        course.setRequiredDurationSeconds(requiredDurationSeconds);
        course.setProgressReportIntervalSeconds(20);
        course.setStudyToleranceSeconds(30);
        course.setStatus(status);
        course.setEverEnabled(everEnabled);
        return course;
    }

    private CoursewareEntity courseware(Long id, Long objectId, int durationSeconds) {
        CoursewareEntity courseware = new CoursewareEntity();
        courseware.setId(id);
        courseware.setEnterpriseId(20L);
        courseware.setCourseId(100L);
        courseware.setStorageObjectId(objectId);
        courseware.setCoursewareTitle("第一章");
        courseware.setDurationSeconds(durationSeconds);
        courseware.setSortOrder(1);
        return courseware;
    }

    private StorageObjectEntity storageObject(Long id) {
        StorageObjectEntity object = new StorageObjectEntity();
        object.setId(id);
        object.setEnterpriseId(20L);
        object.setOriginalFilename("lesson.mp4");
        object.setContentType("video/mp4");
        object.setFileSize(1024L);
        return object;
    }
}
