package me.lj.train.training.service;

import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.api.training.LearningAccessModels.LearningPlaybackCommand;
import me.lj.train.api.training.LearningAccessModels.LearningTaskContextView;
import me.lj.train.api.training.LearningAccessModels.LearningTaskQuery;
import me.lj.train.api.training.StorageModels.SignedRequestView;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;
import me.lj.train.training.config.OssStorageProperties;
import me.lj.train.training.mapper.PlanCourseMapper;
import me.lj.train.training.mapper.PlanCoursewareSnapshotMapper;
import me.lj.train.training.mapper.PlanMapper;
import me.lj.train.training.mapper.PlanUserMapper;
import me.lj.train.training.mapper.StorageObjectMapper;
import me.lj.train.training.model.entity.PlanCourseEntity;
import me.lj.train.training.model.entity.PlanCoursewareSnapshotEntity;
import me.lj.train.training.model.entity.PlanEntity;
import me.lj.train.training.model.entity.PlanUserEntity;
import me.lj.train.training.model.entity.StorageObjectEntity;
import me.lj.train.training.storage.ObjectStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 学员任务资格与租户隔离测试。 */
@ExtendWith(MockitoExtension.class)
class LearningAccessServiceImplTest {

    @Mock private PlatformTransactionManager transactionManager;
    @Mock private PlanMapper planMapper;
    @Mock private PlanCourseMapper planCourseMapper;
    @Mock private PlanCoursewareSnapshotMapper snapshotMapper;
    @Mock private PlanUserMapper planUserMapper;
    @Mock private StorageObjectMapper storageObjectMapper;
    @Mock private PlanLifecycleService lifecycleService;
    @Mock private ObjectStorageService objectStorageService;

    private LearningAccessServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LearningAccessServiceImpl(
                transactionManager, planMapper, planCourseMapper, snapshotMapper, planUserMapper,
                storageObjectMapper, lifecycleService, objectStorageService,
                new OssStorageProperties());
        UserContext.set(student());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldRejectStudentWhoIsNotAssignedToPlan() {
        when(planMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(activePlan());
        when(planUserMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);

        Result<LearningTaskContextView> result = service.getTaskContext(new LearningTaskQuery(100L));

        assertThat(result.getCode()).isEqualTo(AppErrorCode.LEARNING_ACCESS_DENIED.getCode());
        verifyNoInteractions(planCourseMapper, snapshotMapper, objectStorageService);
    }

    @Test
    void shouldReturnOnlyAssignedTaskContext() {
        PlanUserEntity task = new PlanUserEntity();
        task.setId(500L);
        task.setAssignmentStatus("ASSIGNED");
        task.setStudyStatus("NOT_STARTED");
        task.setCompletionStatus("NOT_COMPLETED");
        when(planMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(activePlan());
        when(planUserMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(task);
        when(planCourseMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        Result<LearningTaskContextView> result = service.getTaskContext(new LearningTaskQuery(100L));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().taskId()).isEqualTo(500L);
        assertThat(result.getData().courses()).isEmpty();
    }

    @Test
    void shouldAllowRetainedStorageObjectForPublishedSnapshotPlayback() {
        PlanUserEntity task = assignedTask();
        PlanCourseEntity course = new PlanCourseEntity();
        course.setId(200L);
        PlanCoursewareSnapshotEntity snapshot = new PlanCoursewareSnapshotEntity();
        snapshot.setId(300L);
        snapshot.setStorageObjectId(400L);
        StorageObjectEntity object = new StorageObjectEntity();
        object.setId(400L);
        object.setObjectKey("plans/100/video.mp4");
        object.setStatus("RETAINED");
        when(planMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(activePlan());
        when(planUserMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(task);
        when(planCourseMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(course);
        when(snapshotMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(snapshot);
        when(storageObjectMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(object);
        when(objectStorageService.isEnabled()).thenReturn(true);
        when(objectStorageService.presignGet(eq(object.getObjectKey()), any(Duration.class)))
                .thenReturn(new ObjectStorageService.SignedRequest(
                        "https://example.com/video", "GET", Map.of(),
                        Instant.parse("2026-08-19T09:00:00Z")));

        Result<SignedRequestView> result = service.createCoursewarePlaybackUrl(
                new LearningPlaybackCommand(500L, 100L, 200L, 300L));

        assertThat(result.isSuccess()).isTrue();
        org.mockito.ArgumentCaptor<QueryWrapper> queryCaptor =
                org.mockito.ArgumentCaptor.forClass(QueryWrapper.class);
        verify(storageObjectMapper).selectOneByQuery(queryCaptor.capture());
        assertThat(queryCaptor.getValue().toSQL())
                .contains("'ACTIVE'")
                .contains("'RETAINED'");
        verify(lifecycleService).refreshStatus(20L, 100L);
    }

    private PlanEntity activePlan() {
        PlanEntity plan = new PlanEntity();
        plan.setId(100L);
        plan.setEnterpriseId(20L);
        plan.setPlanName("安全培训");
        plan.setStatus("IN_PROGRESS");
        plan.setStartAt(LocalDateTime.now().minusHours(1));
        plan.setEndAt(LocalDateTime.now().plusHours(1));
        return plan;
    }

    private PlanUserEntity assignedTask() {
        PlanUserEntity task = new PlanUserEntity();
        task.setId(500L);
        task.setAssignmentStatus("ASSIGNED");
        task.setStudyStatus("NOT_STARTED");
        task.setCompletionStatus("NOT_COMPLETED");
        return task;
    }

    private LoginUser student() {
        LoginUser user = new LoginUser();
        user.setUserId(10L);
        user.setEnterpriseId(20L);
        user.setPermissions(Collections.singletonList("student:learning:study"));
        return user;
    }
}
