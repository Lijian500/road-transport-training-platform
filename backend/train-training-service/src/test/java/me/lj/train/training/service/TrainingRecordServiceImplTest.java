package me.lj.train.training.service;

import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.api.training.TrainingRecordModels.*;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;
import me.lj.train.training.mapper.*;
import me.lj.train.training.model.entity.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 结束计划可读、所有权隔离和无副作用契约测试。 */
@ExtendWith(MockitoExtension.class)
class TrainingRecordServiceImplTest {
    @Mock private PlatformTransactionManager transactions;
    @Mock private PlanMapper plans;
    @Mock private PlanUserMapper tasks;
    @Mock private PlanCourseMapper courses;
    @Mock private ExamRecordMapper exams;
    private TrainingRecordServiceImpl service;

    /** 设置学员上下文，只授予档案所需权限。 */
    @BeforeEach void setUp() {
        service = new TrainingRecordServiceImpl(transactions, plans, tasks, courses, exams);
        LoginUser user = new LoginUser(); user.setEnterpriseId(20L); user.setUserId(10L);
        user.setPermissions(List.of("student:plan:view")); UserContext.set(user);
    }
    /** 清理线程上下文。 */
    @AfterEach void tearDown() { UserContext.clear(); }

    @Test void shouldReadFinishedPlanWithoutStartingLearningOrExam() {
        PlanUserEntity task = task(10L);
        PlanEntity plan = new PlanEntity(); plan.setId(100L); plan.setEnterpriseId(20L);
        plan.setPlanName("历史培训"); plan.setStatus("FINISHED");
        plan.setStartAt(LocalDateTime.now().minusDays(3)); plan.setEndAt(LocalDateTime.now().minusDays(1));
        PlanCourseEntity course = new PlanCourseEntity(); course.setId(200L); course.setPlanId(100L);
        course.setCourseName("安全行车"); course.setRequiredDurationSeconds(60);
        when(tasks.selectOneByQuery(any(QueryWrapper.class))).thenReturn(task);
        when(plans.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(plan));
        when(courses.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(course));
        when(exams.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

        var result = service.getMyRecord(1L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().planStatus()).isEqualTo("FINISHED");
        assertThat(result.getData().requiredDurationMillis()).isEqualTo(60_000);
        assertThat(result.getData().courses()).hasSize(1);
        assertThat(result.getData().examScore()).isNull();
        verifyNoInteractions(transactions);
        verify(tasks, never()).insertSelective(any(PlanUserEntity.class));
        verify(exams, never()).insertSelective(any(ExamRecordEntity.class));
    }

    @Test void shouldRejectAnotherStudentsTaskEvenWhenRepositoryReturnsIt() {
        when(tasks.selectOneByQuery(any(QueryWrapper.class))).thenReturn(task(11L));
        assertThat(service.getMyRecord(1L).getCode()).isEqualTo(AppErrorCode.STUDENT_TASK_NOT_FOUND.getCode());
        verifyNoInteractions(plans, courses, exams);
    }

    @Test void shouldRejectAnotherEnterprise() {
        PlanUserEntity foreign = task(10L); foreign.setEnterpriseId(21L);
        when(tasks.selectOneByQuery(any(QueryWrapper.class))).thenReturn(foreign);
        assertThat(service.getMyRecord(1L).getCode()).isEqualTo(AppErrorCode.DATA_SCOPE_VIOLATION.getCode());
        verifyNoInteractions(plans, courses, exams);
    }

    @Test void shouldRequireStatisticsPermissionForAdministratorEntry() {
        assertThat(service.getAdminRecord(1L).getCode()).isEqualTo(AppErrorCode.FORBIDDEN.getCode());
        verifyNoInteractions(tasks, plans, courses, exams);
    }

    @Test void shouldRejectReversedDateRangeBeforeQuerying() {
        var result = service.pageMyRecords(new RecordQuery(1, 10, null, null,
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 1), null));
        assertThat(result.getCode()).isEqualTo(AppErrorCode.PARAM_INVALID.getCode());
        verifyNoInteractions(tasks, plans, courses, exams);
    }

    /** 构造某学员已分配的培训任务。 */
    private PlanUserEntity task(Long userId) {
        PlanUserEntity task = new PlanUserEntity(); task.setId(1L); task.setEnterpriseId(20L);
        task.setPlanId(100L); task.setUserId(userId); task.setAssignmentStatus("ASSIGNED");
        task.setStudyStatus("NOT_STARTED"); task.setExamStatus("NOT_STARTED"); task.setCompletionStatus("NOT_COMPLETED");
        return task;
    }
}
