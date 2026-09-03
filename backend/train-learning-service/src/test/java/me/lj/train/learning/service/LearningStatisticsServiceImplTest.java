package me.lj.train.learning.service;

import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.api.learning.LearningStatisticsModels.LearningDurationQuery;
import me.lj.train.api.learning.LearningStatisticsModels.LearningDurationSummaryView;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;
import me.lj.train.learning.mapper.StudyProgressMapper;
import me.lj.train.learning.model.entity.StudyProgressEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Arrays;
import java.util.Collections;

import static me.lj.train.learning.support.LearningGuard.ADMIN_STATISTICS_VIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 管理端服务端有效学时汇总测试。 */
@ExtendWith(MockitoExtension.class)
class LearningStatisticsServiceImplTest {

    @Mock private PlatformTransactionManager transactionManager;
    @Mock private StudyProgressMapper progressMapper;

    private LearningStatisticsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LearningStatisticsServiceImpl(transactionManager, progressMapper);
        UserContext.set(administrator(Collections.singletonList(ADMIN_STATISTICS_VIEW)));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldAggregateMultipleCoursesByTask() {
        when(progressMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Arrays.asList(
                progress(1L, 1_000L, 800L),
                progress(1L, 2_000L, 1_500L),
                progress(2L, 3_000L, 500L)));

        Result<LearningDurationSummaryView> result = service.summarize(
                new LearningDurationQuery(null, Arrays.asList(1L, 2L)));

        assertThat(result.isSuccess()).isTrue();
        LearningDurationSummaryView summary = result.getData();
        assertThat(summary.requiredDurationMillis()).isEqualTo(6_000L);
        assertThat(summary.effectiveDurationMillis()).isEqualTo(2_800L);
        assertThat(summary.tasks()).hasSize(2);
        assertThat(summary.tasks().get(0).requiredDurationMillis()).isEqualTo(3_000L);
        assertThat(summary.tasks().get(0).effectiveDurationMillis()).isEqualTo(2_300L);
    }

    @Test
    void shouldReturnEmptySummaryWithoutQueryingWhenTaskRangeIsEmpty() {
        Result<LearningDurationSummaryView> result = service.summarize(
                new LearningDurationQuery(null, Collections.emptyList()));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().tasks()).isEmpty();
        verifyNoInteractions(progressMapper);
    }

    @Test
    void shouldRejectAccountWithoutStatisticsPermission() {
        UserContext.set(administrator(Collections.emptyList()));

        Result<?> result = service.summarize(new LearningDurationQuery(null, null));

        assertThat(result.getCode()).isEqualTo(AppErrorCode.FORBIDDEN.getCode());
        verifyNoInteractions(progressMapper);
    }

    /** 构造企业管理员登录上下文。 */
    private LoginUser administrator(java.util.List<String> permissions) {
        LoginUser user = new LoginUser();
        user.setUserId(10L);
        user.setEnterpriseId(20L);
        user.setPermissions(permissions);
        return user;
    }

    /** 构造单课程有效学时记录。 */
    private StudyProgressEntity progress(
            Long taskId, long requiredDurationMillis, long effectiveDurationMillis) {
        StudyProgressEntity progress = new StudyProgressEntity();
        progress.setEnterpriseId(20L);
        progress.setTaskId(taskId);
        progress.setRequiredDurationMs(requiredDurationMillis);
        progress.setEffectiveDurationMs(effectiveDurationMillis);
        return progress;
    }
}
