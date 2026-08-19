package me.lj.train.training.service;

import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;
import me.lj.train.training.mapper.PlanMapper;
import me.lj.train.training.mapper.PlanUserMapper;
import me.lj.train.training.model.entity.PlanUserEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Collections;

import static me.lj.train.training.constant.TrainingPermissions.STUDENT_PLAN_VIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 当前学员任务隔离规则测试。 */
@ExtendWith(MockitoExtension.class)
class StudentPlanServiceImplTest {

    @Mock private PlatformTransactionManager transactionManager;
    @Mock private PlanMapper planMapper;
    @Mock private PlanUserMapper planUserMapper;
    @Mock private PlanLifecycleService lifecycleService;
    @Mock private PlanViewAssembler viewAssembler;

    private StudentPlanServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StudentPlanServiceImpl(
                transactionManager, planMapper, planUserMapper, lifecycleService, viewAssembler);
        LoginUser learner = new LoginUser();
        learner.setUserId(10L);
        learner.setEnterpriseId(20L);
        learner.setPermissions(Collections.singletonList(STUDENT_PLAN_VIEW));
        UserContext.set(learner);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldHidePlanWhenCurrentLearnerIsNotAssigned() {
        when(planUserMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);

        Result<?> result = service.getMyPlan(100L);

        assertThat(result.getCode()).isEqualTo(AppErrorCode.STUDENT_TASK_NOT_FOUND.getCode());
        verifyNoInteractions(planMapper, viewAssembler);
    }

    @Test
    void shouldRejectTaskReturnedForAnotherLearner() {
        PlanUserEntity foreignTask = new PlanUserEntity();
        foreignTask.setEnterpriseId(20L);
        foreignTask.setPlanId(100L);
        foreignTask.setUserId(11L);
        when(planUserMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(foreignTask);

        Result<?> result = service.getMyPlan(100L);

        assertThat(result.getCode()).isEqualTo(AppErrorCode.STUDENT_TASK_NOT_FOUND.getCode());
        verifyNoInteractions(planMapper, viewAssembler);
    }
}
