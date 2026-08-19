package me.lj.train.training.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateWrapper;
import me.lj.train.api.admin.TrainingParticipantModels.ParticipantView;
import me.lj.train.api.training.PlanModels.PlanView;
import me.lj.train.api.training.PlanModels.UpdatePlanCommand;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;
import me.lj.train.training.mapper.CourseMapper;
import me.lj.train.training.mapper.CoursewareMapper;
import me.lj.train.training.mapper.PlanCourseMapper;
import me.lj.train.training.mapper.PlanCoursewareSnapshotMapper;
import me.lj.train.training.mapper.PlanMapper;
import me.lj.train.training.mapper.PlanUserMapper;
import me.lj.train.training.model.entity.CourseEntity;
import me.lj.train.training.model.entity.CoursewareEntity;
import me.lj.train.training.model.entity.PlanCourseEntity;
import me.lj.train.training.model.entity.PlanCoursewareSnapshotEntity;
import me.lj.train.training.model.entity.PlanEntity;
import me.lj.train.training.model.entity.PlanUserEntity;
import me.lj.train.training.support.ParticipantDirectoryClient;
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

import java.time.LocalDateTime;
import java.util.Collections;

import static me.lj.train.training.constant.TrainingConstants.PLAN_DRAFT;
import static me.lj.train.training.constant.TrainingConstants.PLAN_PUBLISHED;
import static me.lj.train.training.constant.TrainingPermissions.PLAN_PUBLISH;
import static me.lj.train.training.constant.TrainingPermissions.PLAN_UPDATE;
import static me.lj.train.training.constant.TrainingPermissions.PLAN_VIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanServiceImplTest {

    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;
    @Mock private PlanMapper planMapper;
    @Mock private PlanCourseMapper planCourseMapper;
    @Mock private PlanCoursewareSnapshotMapper snapshotMapper;
    @Mock private PlanUserMapper planUserMapper;
    @Mock private CourseMapper courseMapper;
    @Mock private CoursewareMapper coursewareMapper;
    @Mock private ParticipantDirectoryClient participantClient;
    @Mock private PlanLifecycleService lifecycleService;
    @Mock private PlanViewAssembler viewAssembler;

    private PlanServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PlanServiceImpl(
                transactionManager, planMapper, planCourseMapper, snapshotMapper, planUserMapper,
                courseMapper, coursewareMapper, participantClient, lifecycleService, viewAssembler);
        UserContext.set(operator(20L, PLAN_VIEW, PLAN_UPDATE, PLAN_PUBLISH));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldRejectCrossEnterprisePlan() {
        PlanEntity foreignPlan = plan(PLAN_DRAFT);
        foreignPlan.setEnterpriseId(21L);
        when(planMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(foreignPlan);

        Result<?> result = service.get(100L);

        assertThat(result.getCode()).isEqualTo(AppErrorCode.DATA_SCOPE_VIOLATION.getCode());
        verifyNoInteractions(viewAssembler);
    }

    @Test
    void shouldKeepPublishedPlanImmutable() {
        prepareTransaction();
        PlanEntity published = plan(PLAN_PUBLISHED);
        when(planMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(published);

        Result<?> result = service.update(new UpdatePlanCommand(
                100L, "更新计划", null, LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(2), false,
                Collections.singletonList(300L), Collections.singletonList(400L)));

        assertThat(result.getCode()).isEqualTo(AppErrorCode.PLAN_STATE_INVALID.getCode());
        verify(planMapper, never()).updateByCondition(any(PlanEntity.class), any());
        verify(transactionManager).rollback(transactionStatus);
    }

    @Test
    void shouldRejectPublishWithoutCourseAndParticipant() {
        prepareTransaction();
        when(planMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(plan(PLAN_DRAFT));
        when(planCourseMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(planUserMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        Result<?> result = service.publish(100L);

        assertThat(result.getCode()).isEqualTo(AppErrorCode.PLAN_PUBLISH_INVALID.getCode());
        verify(planMapper, never()).updateByCondition(any(PlanEntity.class), any());
        verify(transactionManager).rollback(transactionStatus);
    }

    @Test
    void shouldPublishAndFreezeCoursewareAndParticipantSnapshots() {
        prepareTransaction();
        PlanEntity draft = plan(PLAN_DRAFT);
        PlanEntity published = plan(PLAN_PUBLISHED);
        PlanCourseEntity selectedCourse = new PlanCourseEntity();
        selectedCourse.setCourseId(300L);
        PlanUserEntity selectedUser = new PlanUserEntity();
        selectedUser.setUserId(400L);
        CourseEntity course = enabledCourse();
        CoursewareEntity courseware = courseware();
        ParticipantView participant = new ParticipantView(
                400L, 20L, 21L, "安全部", "student", "张三");
        PlanView expected = planView(PLAN_PUBLISHED);
        when(planMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(draft, published);
        when(planCourseMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(selectedCourse));
        when(planUserMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(selectedUser));
        when(courseMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(course));
        when(coursewareMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(courseware));
        when(participantClient.validate(Collections.singletonList(400L)))
                .thenReturn(Collections.singletonList(participant));
        when(viewAssembler.toPlanView(published, true)).thenReturn(expected);

        Result<PlanView> result = service.publish(100L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isSameAs(expected);
        ArgumentCaptor<PlanCourseEntity> courseCaptor = ArgumentCaptor.forClass(PlanCourseEntity.class);
        verify(planCourseMapper).insertSelective(courseCaptor.capture());
        assertThat(courseCaptor.getValue().getCourseName()).isEqualTo("安全生产基础");
        assertThat(courseCaptor.getValue().getRequiredDurationSeconds()).isEqualTo(1800);
        ArgumentCaptor<PlanCoursewareSnapshotEntity> snapshotCaptor =
                ArgumentCaptor.forClass(PlanCoursewareSnapshotEntity.class);
        verify(snapshotMapper).insertSelective(snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue().getSourceCoursewareId()).isEqualTo(301L);
        assertThat(snapshotCaptor.getValue().getStorageObjectId()).isEqualTo(501L);
        ArgumentCaptor<PlanUserEntity> taskCaptor = ArgumentCaptor.forClass(PlanUserEntity.class);
        verify(planUserMapper).insertSelective(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getDisplayName()).isEqualTo("张三");
        assertThat(taskCaptor.getValue().getOrgName()).isEqualTo("安全部");
        ArgumentCaptor<PlanEntity> publishCaptor = ArgumentCaptor.forClass(PlanEntity.class);
        verify(planMapper).updateByCondition(publishCaptor.capture(), any());
        assertThat(((UpdateWrapper<?>) publishCaptor.getValue()).getUpdates())
                .containsEntry("status", PLAN_PUBLISHED)
                .containsKey("published_at");
        verify(transactionManager).commit(transactionStatus);
    }

    /** 准备需要本地事务的测试场景。 */
    private void prepareTransaction() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
    }

    private LoginUser operator(Long enterpriseId, String... permissions) {
        LoginUser user = new LoginUser();
        user.setUserId(10L);
        user.setEnterpriseId(enterpriseId);
        user.setPermissions(java.util.Arrays.asList(permissions));
        return user;
    }

    private PlanEntity plan(String status) {
        PlanEntity plan = new PlanEntity();
        plan.setId(100L);
        plan.setEnterpriseId(20L);
        plan.setPlanName("八月安全培训");
        plan.setStartAt(LocalDateTime.now().plusDays(1));
        plan.setEndAt(LocalDateTime.now().plusDays(10));
        plan.setStatus(status);
        plan.setExamRequired(false);
        return plan;
    }

    private CourseEntity enabledCourse() {
        CourseEntity course = new CourseEntity();
        course.setId(300L);
        course.setEnterpriseId(20L);
        course.setCourseName("安全生产基础");
        course.setRequiredDurationSeconds(1800);
        course.setAllowSeek(false);
        course.setProgressReportIntervalSeconds(20);
        course.setStudyToleranceSeconds(30);
        course.setStatus("ENABLED");
        return course;
    }

    private CoursewareEntity courseware() {
        CoursewareEntity courseware = new CoursewareEntity();
        courseware.setId(301L);
        courseware.setEnterpriseId(20L);
        courseware.setCourseId(300L);
        courseware.setStorageObjectId(501L);
        courseware.setCoursewareTitle("第一章");
        courseware.setDurationSeconds(1900);
        courseware.setSortOrder(1);
        return courseware;
    }

    private PlanView planView(String status) {
        return new PlanView(
                100L, "八月安全培训", null,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(10), status,
                false, null, Collections.emptyList(), Collections.emptyList(),
                LocalDateTime.now(), null, LocalDateTime.now(), LocalDateTime.now());
    }
}
