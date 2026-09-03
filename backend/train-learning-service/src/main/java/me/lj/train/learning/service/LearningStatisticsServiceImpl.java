package me.lj.train.learning.service;

import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.api.learning.LearningStatisticsModels.LearningDurationQuery;
import me.lj.train.api.learning.LearningStatisticsModels.LearningDurationSummaryView;
import me.lj.train.api.learning.LearningStatisticsModels.TaskDurationView;
import me.lj.train.api.learning.LearningStatisticsService;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.learning.mapper.StudyProgressMapper;
import me.lj.train.learning.model.entity.StudyProgressEntity;
import me.lj.train.learning.support.LearningGuard;
import me.lj.train.learning.support.LearningServiceSupport;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static me.lj.train.learning.model.table.StudyProgressTableDef.STUDY_PROGRESS;

/**
 * 按企业、计划或任务汇总服务端确认的有效学时。
 */
@DubboService(timeout = 10000, retries = 0)
public class LearningStatisticsServiceImpl extends LearningServiceSupport
        implements LearningStatisticsService {

    private static final int MAX_TASKS = 500;

    private final StudyProgressMapper progressMapper;

    public LearningStatisticsServiceImpl(
            PlatformTransactionManager transactionManager,
            StudyProgressMapper progressMapper) {
        super(transactionManager);
        this.progressMapper = progressMapper;
    }

    @Override
    public Result<LearningDurationSummaryView> summarize(LearningDurationQuery query) {
        return execute(() -> {
            if (query == null) {
                throw new BusinessException(AppErrorCode.PARAM_INVALID);
            }
            Long enterpriseId = LearningGuard.requireStatisticsAdministrator();
            Long planId = normalizePlanId(query.planId());
            List<Long> taskIds = normalizeTaskIds(query.taskIds());
            if (query.taskIds() != null && taskIds.isEmpty()) {
                return emptySummary();
            }
            List<StudyProgressEntity> progressList = progressMapper.selectListByQuery(
                    QueryWrapper.create()
                            .where(STUDY_PROGRESS.ENTERPRISE_ID.eq(enterpriseId))
                            .and(STUDY_PROGRESS.PLAN_ID.eq(planId).when(planId != null))
                            .and(STUDY_PROGRESS.TASK_ID.in(taskIds).when(query.taskIds() != null))
                            .orderBy(STUDY_PROGRESS.TASK_ID.asc(),
                                    STUDY_PROGRESS.SORT_ORDER.asc()));
            Map<Long, DurationAccumulator> taskMap = new LinkedHashMap<>();
            long requiredDurationMillis = 0L;
            long effectiveDurationMillis = 0L;
            for (StudyProgressEntity progress : progressList) {
                requiredDurationMillis += progress.getRequiredDurationMs();
                effectiveDurationMillis += progress.getEffectiveDurationMs();
                taskMap.computeIfAbsent(progress.getTaskId(), key -> new DurationAccumulator())
                        .add(progress.getRequiredDurationMs(), progress.getEffectiveDurationMs());
            }
            List<TaskDurationView> tasks = taskMap.entrySet().stream()
                    .map(entry -> new TaskDurationView(
                            entry.getKey(), entry.getValue().requiredDurationMillis,
                            entry.getValue().effectiveDurationMillis))
                    .collect(Collectors.toList());
            return new LearningDurationSummaryView(
                    requiredDurationMillis, effectiveDurationMillis, tasks);
        });
    }

    /** 计划ID为空表示汇总企业全部已产生进度的数据。 */
    private Long normalizePlanId(Long planId) {
        if (planId != null && planId <= 0) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "培训计划不正确");
        }
        return planId;
    }

    /** 去重并限制一次RPC可查询的任务数量。 */
    private List<Long> normalizeTaskIds(List<Long> taskIds) {
        if (taskIds == null) {
            return Collections.emptyList();
        }
        List<Long> normalized = new ArrayList<>(new LinkedHashSet<>(taskIds));
        if (normalized.stream().anyMatch(id -> id == null || id <= 0)
                || normalized.size() > MAX_TASKS) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "培训任务范围不正确");
        }
        return normalized;
    }

    /** 返回无学习进度时的稳定空结构。 */
    private LearningDurationSummaryView emptySummary() {
        return new LearningDurationSummaryView(0L, 0L, Collections.emptyList());
    }

    /** 单任务多课程学时累加器。 */
    private static final class DurationAccumulator {
        private long requiredDurationMillis;
        private long effectiveDurationMillis;

        private void add(long requiredDuration, long effectiveDuration) {
            requiredDurationMillis += requiredDuration;
            effectiveDurationMillis += effectiveDuration;
        }
    }
}
