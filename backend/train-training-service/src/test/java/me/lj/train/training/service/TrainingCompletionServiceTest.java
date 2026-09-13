package me.lj.train.training.service;

import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.training.mapper.PlanMapper;
import me.lj.train.training.mapper.PlanUserMapper;
import me.lj.train.training.model.entity.PlanEntity;
import me.lj.train.training.model.entity.PlanUserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 学习和考试统一完成条件测试。 */
class TrainingCompletionServiceTest {

    @Test
    void shouldCompleteOnlyAfterStudyAndRequiredExamPass() {
        PlanMapper planMapper = mock(PlanMapper.class);
        PlanUserMapper taskMapper = mock(PlanUserMapper.class);
        TrainingCompletionService service = new TrainingCompletionService(planMapper, taskMapper, mock(PlatformTransactionManager.class));
        PlanUserEntity task = new PlanUserEntity();
        task.setId(1L);
        task.setPlanId(2L);
        task.setStudyStatus("COMPLETED");
        task.setExamStatus("PASSED");
        task.setCompletionStatus("NOT_COMPLETED");
        PlanEntity plan = new PlanEntity();
        plan.setExamRequired(true);
        when(taskMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(task);
        when(planMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(plan);

        service.recalculate(1L, 20L, LocalDateTime.now());

        verify(taskMapper).updateByCondition(any(PlanUserEntity.class), any());
        verify(planMapper).finishCompletedPlans(20L, 2L);
    }

    /** 重复完成通知也修复计划状态，不覆盖学员首次结业时间。 */
    @Test
    void shouldRefreshPlanForAlreadyCompletedTask() {
        PlanMapper planMapper = mock(PlanMapper.class);
        PlanUserMapper taskMapper = mock(PlanUserMapper.class);
        PlanUserEntity task = new PlanUserEntity();
        task.setPlanId(2L);
        task.setCompletionStatus("COMPLETED");
        when(taskMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(task);

        new TrainingCompletionService(planMapper, taskMapper, mock(PlatformTransactionManager.class)).recalculate(1L, 20L, LocalDateTime.now());

        verify(planMapper).finishCompletedPlans(20L, 2L);
        verify(taskMapper, never()).updateByCondition(any(PlanUserEntity.class), any());
    }

    @Test
    void shouldKeepIncompleteWhenExamHasNotPassed() {
        PlanMapper planMapper = mock(PlanMapper.class);
        PlanUserMapper taskMapper = mock(PlanUserMapper.class);
        TrainingCompletionService service = new TrainingCompletionService(planMapper, taskMapper, mock(PlatformTransactionManager.class));
        PlanUserEntity task = new PlanUserEntity();
        task.setId(1L);
        task.setPlanId(2L);
        task.setStudyStatus("COMPLETED");
        task.setExamStatus("FAILED");
        task.setCompletionStatus("NOT_COMPLETED");
        PlanEntity plan = new PlanEntity();
        plan.setExamRequired(true);
        when(taskMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(task);
        when(planMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(plan);

        service.recalculate(1L, 20L, LocalDateTime.now());

        verify(taskMapper, never()).updateByCondition(any(PlanUserEntity.class), any());
        verify(planMapper, never()).finishCompletedPlans(any(), any());
    }

    /** 学习未完成时即使考试已通过也不结业；仅学习模式无需考试。 */
    @ParameterizedTest
    @CsvSource({"true, IN_PROGRESS, PASSED, false", "false, COMPLETED, NOT_REQUIRED, true"})
    void shouldApplyCompletionRequirements(boolean examRequired, String studyStatus,
                                           String examStatus, boolean completed) {
        PlanMapper planMapper = mock(PlanMapper.class);
        PlanUserMapper taskMapper = mock(PlanUserMapper.class);
        TrainingCompletionService service = new TrainingCompletionService(planMapper, taskMapper, mock(PlatformTransactionManager.class));
        PlanUserEntity task = new PlanUserEntity();
        task.setId(1L);
        task.setPlanId(2L);
        task.setStudyStatus(studyStatus);
        task.setExamStatus(examStatus);
        task.setCompletionStatus("NOT_COMPLETED");
        PlanEntity plan = new PlanEntity();
        plan.setExamRequired(examRequired);
        when(taskMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(task);
        when(planMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(plan);

        service.recalculate(1L, 20L, LocalDateTime.now());

        verify(taskMapper, org.mockito.Mockito.times(completed ? 1 : 0))
                .updateByCondition(any(PlanUserEntity.class), any());
    }

    /** 提交前不更新计划，回滚不触发推进；提交后使用新事务且失败不影响已保存的成绩。 */
    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void shouldRefreshPlanOnlyAfterCommit(boolean commit) {
        PlanMapper planMapper = mock(PlanMapper.class);
        PlanUserMapper taskMapper = mock(PlanUserMapper.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        PlanUserEntity task = new PlanUserEntity();
        task.setPlanId(2L);
        task.setCompletionStatus("COMPLETED");
        when(taskMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(task);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            new TrainingCompletionService(planMapper, taskMapper, manager)
                    .recalculate(1L, 20L, LocalDateTime.now());
            verify(planMapper, never()).finishCompletedPlans(any(), any());
            verify(manager, never()).getTransaction(any());
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                if (commit) {
                    when(planMapper.finishCompletedPlans(20L, 2L)).thenThrow(new IllegalStateException("数据库暂不可用"));
                    org.assertj.core.api.Assertions.assertThatCode(synchronization::afterCommit).doesNotThrowAnyException();
                }
                synchronization.afterCompletion(commit ? TransactionSynchronization.STATUS_COMMITTED
                        : TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            verify(planMapper, org.mockito.Mockito.times(commit ? 1 : 0)).finishCompletedPlans(20L, 2L);
            if (commit) {
                verify(manager).getTransaction(org.mockito.ArgumentMatchers.argThat(definition ->
                        definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

}
