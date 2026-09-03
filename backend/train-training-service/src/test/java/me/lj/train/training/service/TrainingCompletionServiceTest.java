package me.lj.train.training.service;

import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.training.mapper.PlanMapper;
import me.lj.train.training.mapper.PlanUserMapper;
import me.lj.train.training.model.entity.PlanEntity;
import me.lj.train.training.model.entity.PlanUserEntity;
import org.junit.jupiter.api.Test;

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
        TrainingCompletionService service = new TrainingCompletionService(planMapper, taskMapper);
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
    }

    @Test
    void shouldKeepIncompleteWhenExamHasNotPassed() {
        PlanMapper planMapper = mock(PlanMapper.class);
        PlanUserMapper taskMapper = mock(PlanUserMapper.class);
        TrainingCompletionService service = new TrainingCompletionService(planMapper, taskMapper);
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
    }
}
