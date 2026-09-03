package me.lj.train.training.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.api.training.TrainingStatisticsModels.ParticipantStatisticsQuery;
import me.lj.train.api.training.TrainingStatisticsModels.ParticipantStatisticsView;
import me.lj.train.api.training.TrainingStatisticsModels.PlanStatisticsQuery;
import me.lj.train.api.training.TrainingStatisticsModels.PlanStatisticsView;
import me.lj.train.api.training.TrainingStatisticsModels.StatisticsOverviewView;
import me.lj.train.api.training.TrainingStatisticsService;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.page.PageRequest;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.training.mapper.ExamRecordMapper;
import me.lj.train.training.mapper.PlanMapper;
import me.lj.train.training.mapper.PlanCourseMapper;
import me.lj.train.training.mapper.PlanUserMapper;
import me.lj.train.training.model.entity.ExamRecordEntity;
import me.lj.train.training.model.entity.PlanEntity;
import me.lj.train.training.model.entity.PlanCourseEntity;
import me.lj.train.training.model.entity.PlanUserEntity;
import me.lj.train.training.support.TrainingGuard;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static me.lj.train.training.constant.TrainingConstants.COMPLETION_COMPLETED;
import static me.lj.train.training.constant.TrainingConstants.COMPLETION_NOT_COMPLETED;
import static me.lj.train.training.constant.TrainingConstants.EXAM_NOT_REQUIRED;
import static me.lj.train.training.constant.TrainingConstants.EXAM_NOT_STARTED;
import static me.lj.train.training.constant.TrainingConstants.EXAM_PASSED;
import static me.lj.train.training.constant.TrainingConstants.PLAN_CANCELLED;
import static me.lj.train.training.constant.TrainingConstants.PLAN_DRAFT;
import static me.lj.train.training.constant.TrainingConstants.PLAN_FINISHED;
import static me.lj.train.training.constant.TrainingConstants.PLAN_IN_PROGRESS;
import static me.lj.train.training.constant.TrainingConstants.PLAN_PUBLISHED;
import static me.lj.train.training.constant.TrainingConstants.STUDY_COMPLETED;
import static me.lj.train.training.constant.TrainingConstants.STUDY_NOT_STARTED;
import static me.lj.train.training.constant.TrainingPermissions.STATISTICS_VIEW;
import static me.lj.train.training.model.table.ExamRecordTableDef.EXAM_RECORD;
import static me.lj.train.training.model.table.PlanTableDef.PLAN;
import static me.lj.train.training.model.table.PlanCourseTableDef.PLAN_COURSE;
import static me.lj.train.training.model.table.PlanUserTableDef.PLAN_USER;

/**
 * 企业培训计划、参训状态和考试结果统计实现。
 */
@DubboService(timeout = 10000, retries = 0)
public class TrainingStatisticsServiceImpl extends TrainingServiceSupport
        implements TrainingStatisticsService {

    private final PlanMapper planMapper;
    private final PlanCourseMapper planCourseMapper;
    private final PlanUserMapper planUserMapper;
    private final ExamRecordMapper examRecordMapper;
    private final PlanLifecycleService lifecycleService;

    public TrainingStatisticsServiceImpl(
            PlatformTransactionManager transactionManager,
            PlanMapper planMapper,
            PlanCourseMapper planCourseMapper,
            PlanUserMapper planUserMapper,
            ExamRecordMapper examRecordMapper,
            PlanLifecycleService lifecycleService) {
        super(transactionManager);
        this.planMapper = planMapper;
        this.planCourseMapper = planCourseMapper;
        this.planUserMapper = planUserMapper;
        this.examRecordMapper = examRecordMapper;
        this.lifecycleService = lifecycleService;
    }

    @Override
    public Result<StatisticsOverviewView> overview(Long planId) {
        return execute(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(STATISTICS_VIEW);
            lifecycleService.refreshStatuses();
            List<PlanEntity> plans = listOperationalPlans(enterpriseId, planId);
            List<PlanUserEntity> tasks = listAssignedTasks(
                    enterpriseId, plans.stream().map(PlanEntity::getId).collect(Collectors.toList()));
            Map<Long, Long> requiredDurationByPlan = requiredDurationByPlan(
                    enterpriseId, plans.stream().map(PlanEntity::getId).collect(Collectors.toList()));
            TaskStatistics statistics = summarize(tasks);
            long requiredDurationMillis = tasks.stream()
                    .mapToLong(task -> requiredDurationByPlan.getOrDefault(task.getPlanId(), 0L))
                    .sum();
            return new StatisticsOverviewView(
                    plans.size(), statistics.participantCount, statistics.startedCount,
                    statistics.studyCompletedCount, statistics.examPassedCount,
                    statistics.completedCount,
                    completionRate(statistics.completedCount, statistics.participantCount),
                    requiredDurationMillis);
        });
    }

    @Override
    public Result<PageResult<PlanStatisticsView>> pagePlans(PlanStatisticsQuery query) {
        return execute(() -> {
            if (query == null) {
                throw new BusinessException(AppErrorCode.PARAM_INVALID);
            }
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(STATISTICS_VIEW);
            lifecycleService.refreshStatuses();
            PageRequest request = query.toPageRequest();
            String keyword = normalizeKeyword(query.keyword());
            String status = normalizePlanStatus(query.status());
            Page<PlanEntity> page = planMapper.paginate(
                    request.getPageNumber(), request.getPageSize(), QueryWrapper.create()
                            .where(PLAN.ENTERPRISE_ID.eq(enterpriseId))
                            .and(PLAN.DELETED_AT.isNull())
                            .and(PLAN.STATUS.ne(PLAN_DRAFT))
                            .and(PLAN.PLAN_NAME.like(keyword).when(keyword != null))
                            .and(PLAN.STATUS.eq(status).when(status != null))
                            .orderBy(PLAN.START_AT.desc(), PLAN.ID.desc()));
            List<Long> planIds = page.getRecords().stream()
                    .map(PlanEntity::getId).collect(Collectors.toList());
            Map<Long, TaskStatistics> statisticsByPlan = listAssignedTasks(enterpriseId, planIds)
                    .stream().collect(Collectors.groupingBy(
                            PlanUserEntity::getPlanId,
                            LinkedHashMap::new,
                            Collectors.collectingAndThen(Collectors.toList(), this::summarize)));
            List<PlanStatisticsView> records = page.getRecords().stream()
                    .map(plan -> toPlanView(plan,
                            statisticsByPlan.getOrDefault(plan.getId(), TaskStatistics.EMPTY)))
                    .collect(Collectors.toList());
            return PageResult.of(records, page.getTotalRow(), request);
        });
    }

    @Override
    public Result<PageResult<ParticipantStatisticsView>> pageParticipants(
            ParticipantStatisticsQuery query) {
        return execute(() -> {
            if (query == null) {
                throw new BusinessException(AppErrorCode.PARAM_INVALID);
            }
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(STATISTICS_VIEW);
            lifecycleService.refreshStatuses();
            PageRequest request = query.toPageRequest();
            String keyword = normalizeKeyword(query.keyword());
            String completionStatus = normalizeCompletionStatus(query.completionStatus());
            List<PlanEntity> plans = listOperationalPlans(enterpriseId, query.planId());
            if (plans.isEmpty()) {
                return PageResult.of(Collections.emptyList(), 0L, request);
            }
            Map<Long, PlanEntity> planMap = plans.stream()
                    .collect(Collectors.toMap(PlanEntity::getId, Function.identity()));
            Map<Long, Long> requiredDurationByPlan = requiredDurationByPlan(
                    enterpriseId, plans.stream().map(PlanEntity::getId).collect(Collectors.toList()));
            Page<PlanUserEntity> page = planUserMapper.paginate(
                    request.getPageNumber(), request.getPageSize(), QueryWrapper.create()
                            .where(PLAN_USER.ENTERPRISE_ID.eq(enterpriseId))
                            .and(PLAN_USER.PLAN_ID.in(planMap.keySet()))
                            .and(PLAN_USER.USERNAME.like(keyword)
                                    .or(PLAN_USER.DISPLAY_NAME.like(keyword))
                                    .or(PLAN_USER.ORG_NAME.like(keyword))
                                    .when(keyword != null))
                            .and(PLAN_USER.COMPLETION_STATUS.eq(completionStatus)
                                    .when(completionStatus != null))
                            .orderBy(PLAN_USER.CREATED_AT.desc(), PLAN_USER.ID.desc()));
            List<Long> taskIds = page.getRecords().stream()
                    .map(PlanUserEntity::getId).collect(Collectors.toList());
            Map<Long, ExamRecordEntity> examMap = listExamRecords(enterpriseId, taskIds).stream()
                    .collect(Collectors.toMap(ExamRecordEntity::getTaskId, Function.identity(),
                            (left, right) -> left));
            List<ParticipantStatisticsView> records = page.getRecords().stream()
                    .map(task -> toParticipantView(
                            task, planMap.get(task.getPlanId()), examMap.get(task.getId()),
                            requiredDurationByPlan.getOrDefault(task.getPlanId(), 0L)))
                    .collect(Collectors.toList());
            return PageResult.of(records, page.getTotalRow(), request);
        });
    }

    /** 查询已进入发布生命周期的本企业计划。 */
    private List<PlanEntity> listOperationalPlans(Long enterpriseId, Long planId) {
        if (planId != null && planId <= 0) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "培训计划不正确");
        }
        List<PlanEntity> plans = planMapper.selectListByQuery(QueryWrapper.create()
                .where(PLAN.ENTERPRISE_ID.eq(enterpriseId))
                .and(PLAN.ID.eq(planId).when(planId != null))
                .and(PLAN.STATUS.ne(PLAN_DRAFT))
                .and(PLAN.DELETED_AT.isNull())
                .orderBy(PLAN.START_AT.desc(), PLAN.ID.desc()));
        if (planId != null && plans.isEmpty()) {
            throw new BusinessException(AppErrorCode.PLAN_NOT_FOUND);
        }
        return plans;
    }

    /** 批量查询统计范围内的参训任务，取消计划仍保留历史口径。 */
    private List<PlanUserEntity> listAssignedTasks(Long enterpriseId, List<Long> planIds) {
        if (planIds.isEmpty()) {
            return Collections.emptyList();
        }
        return planUserMapper.selectListByQuery(QueryWrapper.create()
                .where(PLAN_USER.ENTERPRISE_ID.eq(enterpriseId))
                .and(PLAN_USER.PLAN_ID.in(planIds)));
    }

    /** 批量查询分页任务对应的考试结果。 */
    private List<ExamRecordEntity> listExamRecords(Long enterpriseId, List<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return Collections.emptyList();
        }
        return examRecordMapper.selectListByQuery(QueryWrapper.create()
                .where(EXAM_RECORD.ENTERPRISE_ID.eq(enterpriseId))
                .and(EXAM_RECORD.TASK_ID.in(taskIds)));
    }

    /** 汇总每个计划为单个学员配置的规定学时。 */
    private Map<Long, Long> requiredDurationByPlan(Long enterpriseId, List<Long> planIds) {
        if (planIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return planCourseMapper.selectListByQuery(QueryWrapper.create()
                        .where(PLAN_COURSE.ENTERPRISE_ID.eq(enterpriseId))
                        .and(PLAN_COURSE.PLAN_ID.in(planIds)))
                .stream().collect(Collectors.groupingBy(
                        PlanCourseEntity::getPlanId,
                        Collectors.summingLong(course ->
                                course.getRequiredDurationSeconds() * 1000L)));
    }

    /** 将任务状态汇总成固定统计口径。 */
    private TaskStatistics summarize(List<PlanUserEntity> tasks) {
        long startedCount = tasks.stream()
                .filter(task -> !STUDY_NOT_STARTED.equals(task.getStudyStatus())
                        || isExamStarted(task.getExamStatus())).count();
        long studyCompletedCount = tasks.stream()
                .filter(task -> STUDY_COMPLETED.equals(task.getStudyStatus())).count();
        long examPassedCount = tasks.stream()
                .filter(task -> EXAM_PASSED.equals(task.getExamStatus())).count();
        long completedCount = tasks.stream()
                .filter(task -> COMPLETION_COMPLETED.equals(task.getCompletionStatus())).count();
        return new TaskStatistics(tasks.size(), startedCount, studyCompletedCount,
                examPassedCount, completedCount);
    }

    /** 考试开始、失败或通过均说明学员已进入培训流程。 */
    private boolean isExamStarted(String examStatus) {
        return examStatus != null && !EXAM_NOT_STARTED.equals(examStatus)
                && !EXAM_NOT_REQUIRED.equals(examStatus);
    }

    /** 构造单计划统计视图。 */
    private PlanStatisticsView toPlanView(PlanEntity plan, TaskStatistics statistics) {
        return new PlanStatisticsView(
                plan.getId(), plan.getPlanName(), plan.getStatus(), plan.getStartAt(),
                plan.getEndAt(), plan.isExamRequired(), statistics.participantCount,
                statistics.startedCount, statistics.studyCompletedCount,
                statistics.examPassedCount, statistics.completedCount,
                completionRate(statistics.completedCount, statistics.participantCount));
    }

    /** 构造学员培训和考试明细视图。 */
    private ParticipantStatisticsView toParticipantView(
            PlanUserEntity task, PlanEntity plan, ExamRecordEntity exam,
            long requiredDurationMillis) {
        return new ParticipantStatisticsView(
                task.getId(), task.getPlanId(), plan.getPlanName(), task.getUserId(),
                task.getOrgId(), task.getOrgName(), task.getUsername(), task.getDisplayName(),
                task.getStudyStatus(), task.getExamStatus(), task.getCompletionStatus(),
                exam == null ? null : exam.getScore(), exam == null ? null : exam.getPassed(),
                requiredDurationMillis,
                task.getCompletedAt());
    }

    /** 完成率按百分数保留两位小数。 */
    private BigDecimal completionRate(long completedCount, long participantCount) {
        if (participantCount == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(completedCount * 100L)
                .divide(BigDecimal.valueOf(participantCount), 2, RoundingMode.HALF_UP);
    }

    /** 规范化计划状态筛选。 */
    private String normalizePlanStatus(String value) {
        String status = trim(value);
        if (status == null) {
            return null;
        }
        if (!PLAN_PUBLISHED.equals(status) && !PLAN_IN_PROGRESS.equals(status)
                && !PLAN_FINISHED.equals(status) && !PLAN_CANCELLED.equals(status)) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "计划状态不正确");
        }
        return status;
    }

    /** 规范化完成状态筛选。 */
    private String normalizeCompletionStatus(String value) {
        String status = trim(value);
        if (status == null) {
            return null;
        }
        if (!COMPLETION_COMPLETED.equals(status) && !COMPLETION_NOT_COMPLETED.equals(status)) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "完成状态不正确");
        }
        return status;
    }

    /** 规范化模糊检索关键字。 */
    private String normalizeKeyword(String value) {
        String keyword = trim(value);
        if (keyword != null && keyword.length() > 128) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "查询关键字过长");
        }
        return keyword;
    }

    /** 空白字符串不参与筛选。 */
    private String trim(String value) {
        String normalized = value == null ? null : value.trim();
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }

    /** 任务状态聚合中间值。 */
    private static final class TaskStatistics {
        private static final TaskStatistics EMPTY = new TaskStatistics(0, 0, 0, 0, 0);

        private final long participantCount;
        private final long startedCount;
        private final long studyCompletedCount;
        private final long examPassedCount;
        private final long completedCount;

        private TaskStatistics(
                long participantCount,
                long startedCount,
                long studyCompletedCount,
                long examPassedCount,
                long completedCount) {
            this.participantCount = participantCount;
            this.startedCount = startedCount;
            this.studyCompletedCount = studyCompletedCount;
            this.examPassedCount = examPassedCount;
            this.completedCount = completedCount;
        }
    }
}
