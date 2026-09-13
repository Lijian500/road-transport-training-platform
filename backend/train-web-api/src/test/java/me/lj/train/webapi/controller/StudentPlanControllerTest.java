package me.lj.train.webapi.controller;

import me.lj.train.api.learning.LearningRecordService;
import me.lj.train.api.learning.LearningStatisticsModels.TaskDurationView;
import me.lj.train.api.training.PlanModels.StudentPlanDurationView;
import me.lj.train.api.training.StudentPlanService;
import me.lj.train.common.core.result.Result;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** 列表批量汇总仅查询本人任务，未生成学习记录时保留要求学时。 */
class StudentPlanControllerTest {
    @Test
    void shouldMergeExistingProgressAndKeepUnstartedRequirements() {
        StudentPlanService training = mock(StudentPlanService.class);
        LearningRecordService learning = mock(LearningRecordService.class);
        StudentPlanController controller = new StudentPlanController();
        ReflectionTestUtils.setField(controller, "studentPlanService", training);
        ReflectionTestUtils.setField(controller, "learningRecordService", learning);
        when(training.getMyPlanDurations(List.of(100L, 101L, 999L))).thenReturn(Result.ok(List.of(
                new StudentPlanDurationView(500L, 100L, 180000L),
                new StudentPlanDurationView(501L, 101L, 60000L))));
        when(learning.myDurations(List.of(500L, 501L))).thenReturn(Result.ok(List.of(
                new TaskDurationView(500L, 60000L, 30000L))));

        var result = controller.progress(List.of(100L, 101L, 999L));

        assertThat(result.getData()).containsExactly(
                new StudentPlanController.StudentPlanProgressView(100L, 180000L, 30000L),
                new StudentPlanController.StudentPlanProgressView(101L, 60000L, 0L));
        verify(training).getMyPlanDurations(List.of(100L, 101L, 999L));
        verify(learning).myDurations(List.of(500L, 501L));
        verifyNoMoreInteractions(training, learning);
    }
}
