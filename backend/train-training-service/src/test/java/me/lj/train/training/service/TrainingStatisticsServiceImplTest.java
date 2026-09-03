package me.lj.train.training.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.api.training.TrainingStatisticsModels.ParticipantStatisticsQuery;
import me.lj.train.api.training.TrainingStatisticsModels.ParticipantStatisticsView;
import me.lj.train.api.training.TrainingStatisticsModels.StatisticsOverviewView;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;
import me.lj.train.training.mapper.ExamRecordMapper;
import me.lj.train.training.mapper.PlanCourseMapper;
import me.lj.train.training.mapper.PlanMapper;
import me.lj.train.training.mapper.PlanUserMapper;
import me.lj.train.training.model.entity.ExamRecordEntity;
import me.lj.train.training.model.entity.PlanCourseEntity;
import me.lj.train.training.model.entity.PlanEntity;
import me.lj.train.training.model.entity.PlanUserEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static me.lj.train.training.constant.TrainingConstants.ASSIGNMENT_ASSIGNED;
import static me.lj.train.training.constant.TrainingConstants.COMPLETION_COMPLETED;
import static me.lj.train.training.constant.TrainingConstants.COMPLETION_NOT_COMPLETED;
import static me.lj.train.training.constant.TrainingConstants.EXAM_FAILED;
import static me.lj.train.training.constant.TrainingConstants.EXAM_NOT_STARTED;
import static me.lj.train.training.constant.TrainingConstants.EXAM_PASSED;
import static me.lj.train.training.constant.TrainingConstants.PLAN_IN_PROGRESS;
import static me.lj.train.training.constant.TrainingConstants.STUDY_COMPLETED;
import static me.lj.train.training.constant.TrainingConstants.STUDY_NOT_STARTED;
import static me.lj.train.training.constant.TrainingPermissions.STATISTICS_VIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 企业统计口径和租户权限测试。 */
@ExtendWith(MockitoExtension.class)
class TrainingStatisticsServiceImplTest {

    @Mock private PlatformTransactionManager transactionManager;
    @Mock private PlanMapper planMapper;
    @Mock private PlanCourseMapper planCourseMapper;
    @Mock private PlanUserMapper planUserMapper;
    @Mock private ExamRecordMapper examRecordMapper;
    @Mock private PlanLifecycleService lifecycleService;

    private TrainingStatisticsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TrainingStatisticsServiceImpl(
                transactionManager, planMapper, planCourseMapper, planUserMapper,
                examRecordMapper, lifecycleService);
        UserContext.set(administrator(Collections.singletonList(STATISTICS_VIEW)));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldSummarizeOperationalTasksAndRequiredDuration() {
        PlanEntity plan = plan(100L, "安全培训");
        when(planMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(plan));
        when(planCourseMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Arrays.asList(course(100L, 1800), course(100L, 1200)));
        when(planUserMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Arrays.asList(
                task(1L, 100L, STUDY_NOT_STARTED, EXAM_NOT_STARTED, COMPLETION_NOT_COMPLETED),
                task(2L, 100L, STUDY_NOT_STARTED, EXAM_FAILED, COMPLETION_NOT_COMPLETED),
                task(3L, 100L, STUDY_COMPLETED, EXAM_PASSED, COMPLETION_COMPLETED)));

        Result<StatisticsOverviewView> result = service.overview(null);

        assertThat(result.isSuccess()).isTrue();
        StatisticsOverviewView overview = result.getData();
        assertThat(overview.planCount()).isEqualTo(1);
        assertThat(overview.participantCount()).isEqualTo(3);
        assertThat(overview.startedCount()).isEqualTo(2);
        assertThat(overview.studyCompletedCount()).isEqualTo(1);
        assertThat(overview.examPassedCount()).isEqualTo(1);
        assertThat(overview.completedCount()).isEqualTo(1);
        assertThat(overview.completionRate()).isEqualByComparingTo(new BigDecimal("33.33"));
        assertThat(overview.requiredDurationMillis()).isEqualTo(9_000_000L);
    }

    @Test
    void shouldReturnExamAndPlanDurationInParticipantDetail() {
        PlanEntity plan = plan(100L, "安全培训");
        PlanUserEntity task = task(
                1L, 100L, STUDY_COMPLETED, EXAM_PASSED, COMPLETION_COMPLETED);
        ExamRecordEntity exam = new ExamRecordEntity();
        exam.setTaskId(1L);
        exam.setScore(92);
        exam.setPassed(true);
        when(planMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(plan));
        when(planCourseMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(course(100L, 3600)));
        when(planUserMapper.paginate(anyInt(), anyInt(),
                any(QueryWrapper.class))).thenReturn(
                        new Page<>(Collections.singletonList(task), 1, 10, 1));
        when(examRecordMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(exam));

        Result<PageResult<ParticipantStatisticsView>> result = service.pageParticipants(
                new ParticipantStatisticsQuery(1, 10, 100L, null, null));

        assertThat(result.isSuccess()).isTrue();
        ParticipantStatisticsView detail = result.getData().getRecords().get(0);
        assertThat(detail.planName()).isEqualTo("安全培训");
        assertThat(detail.examScore()).isEqualTo(92);
        assertThat(detail.examPassed()).isTrue();
        assertThat(detail.requiredDurationMillis()).isEqualTo(3_600_000L);
    }

    @Test
    void shouldRejectAccountWithoutStatisticsPermission() {
        UserContext.set(administrator(Collections.emptyList()));

        Result<?> result = service.overview(null);

        assertThat(result.getCode()).isEqualTo(AppErrorCode.FORBIDDEN.getCode());
        verifyNoInteractions(planMapper, planCourseMapper, planUserMapper, examRecordMapper);
    }

    /** 构造统计权限企业管理员。 */
    private LoginUser administrator(java.util.List<String> permissions) {
        LoginUser user = new LoginUser();
        user.setUserId(10L);
        user.setEnterpriseId(20L);
        user.setPermissions(permissions);
        return user;
    }

    /** 构造培训计划。 */
    private PlanEntity plan(Long id, String name) {
        PlanEntity plan = new PlanEntity();
        plan.setId(id);
        plan.setEnterpriseId(20L);
        plan.setPlanName(name);
        plan.setStatus(PLAN_IN_PROGRESS);
        return plan;
    }

    /** 构造计划课程规则快照。 */
    private PlanCourseEntity course(Long planId, int requiredDurationSeconds) {
        PlanCourseEntity course = new PlanCourseEntity();
        course.setPlanId(planId);
        course.setEnterpriseId(20L);
        course.setRequiredDurationSeconds(requiredDurationSeconds);
        return course;
    }

    /** 构造参训任务状态。 */
    private PlanUserEntity task(
            Long id,
            Long planId,
            String studyStatus,
            String examStatus,
            String completionStatus) {
        PlanUserEntity task = new PlanUserEntity();
        task.setId(id);
        task.setEnterpriseId(20L);
        task.setPlanId(planId);
        task.setUserId(1000L + id);
        task.setUsername("student" + id);
        task.setDisplayName("学员" + id);
        task.setAssignmentStatus(ASSIGNMENT_ASSIGNED);
        task.setStudyStatus(studyStatus);
        task.setExamStatus(examStatus);
        task.setCompletionStatus(completionStatus);
        return task;
    }
}
