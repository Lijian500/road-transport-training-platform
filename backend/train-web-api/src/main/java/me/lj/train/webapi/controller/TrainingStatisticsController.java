package me.lj.train.webapi.controller;

import me.lj.train.api.learning.LearningStatisticsModels.LearningDurationQuery;
import me.lj.train.api.learning.LearningStatisticsModels.LearningDurationSummaryView;
import me.lj.train.api.learning.LearningStatisticsModels.TaskDurationView;
import me.lj.train.api.learning.LearningStatisticsService;
import me.lj.train.api.training.TrainingStatisticsModels.ParticipantStatisticsQuery;
import me.lj.train.api.training.TrainingStatisticsModels.ParticipantStatisticsView;
import me.lj.train.api.training.TrainingStatisticsModels.PlanStatisticsQuery;
import me.lj.train.api.training.TrainingStatisticsModels.PlanStatisticsView;
import me.lj.train.api.training.TrainingStatisticsModels.StatisticsOverviewView;
import me.lj.train.api.training.TrainingStatisticsService;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.Result;
import me.lj.train.webapi.security.RequirePermission;
import me.lj.train.webapi.support.RpcResultSupport;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端培训状态、考试结果和有效学时聚合接口。
 */
@RestController
@RequestMapping("/api/training/statistics")
public class TrainingStatisticsController {

    private static final String STATISTICS_PERMISSION = "admin:statistics:view";

    @DubboReference(check = false, timeout = 10000, retries = 0)
    private TrainingStatisticsService trainingStatisticsService;

    @DubboReference(check = false, timeout = 10000, retries = 0)
    private LearningStatisticsService learningStatisticsService;

    /** 查询企业全部计划或指定计划的培训统计总览。 */
    @GetMapping("/overview")
    @RequirePermission(STATISTICS_PERMISSION)
    public Result<StatisticsOverviewResponse> overview(
            @RequestParam(required = false) Long planId) {
        StatisticsOverviewView training = RpcResultSupport.unwrap(
                trainingStatisticsService.overview(planId));
        LearningDurationSummaryView learning = RpcResultSupport.unwrap(
                learningStatisticsService.summarize(
                        new LearningDurationQuery(planId, null)));
        long averageEffectiveDurationMillis = training.participantCount() == 0
                ? 0L : learning.effectiveDurationMillis() / training.participantCount();
        return Result.ok(new StatisticsOverviewResponse(
                training.planCount(), training.participantCount(), training.startedCount(),
                training.studyCompletedCount(), training.examPassedCount(),
                training.completedCount(), training.completionRate(),
                training.requiredDurationMillis(), learning.effectiveDurationMillis(),
                averageEffectiveDurationMillis));
    }

    /** 分页查询各培训计划的完成情况。 */
    @GetMapping("/plans")
    @RequirePermission(STATISTICS_PERMISSION)
    public Result<PageResult<PlanStatisticsView>> pagePlans(
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.ok(RpcResultSupport.unwrap(trainingStatisticsService.pagePlans(
                new PlanStatisticsQuery(pageNumber, pageSize, keyword, status))));
    }

    /** 分页查询学员培训、考试与服务端有效学时明细。 */
    @GetMapping("/participants")
    @RequirePermission(STATISTICS_PERMISSION)
    public Result<PageResult<ParticipantStatisticsResponse>> pageParticipants(
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long planId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String completionStatus) {
        PageResult<ParticipantStatisticsView> trainingPage = RpcResultSupport.unwrap(
                trainingStatisticsService.pageParticipants(new ParticipantStatisticsQuery(
                        pageNumber, pageSize, planId, keyword, completionStatus)));
        Map<Long, TaskDurationView> durationMap = loadTaskDurations(trainingPage, planId);
        return Result.ok(new PageResult<>(trainingPage.getRecords().stream()
                .map(item -> toParticipantResponse(item, durationMap.get(item.taskId())))
                .collect(Collectors.toList()), trainingPage.getTotal(),
                trainingPage.getPageNumber(), trainingPage.getPageSize()));
    }

    /** 只查询当前页任务学时，避免跨服务传递无界数据集合。 */
    private Map<Long, TaskDurationView> loadTaskDurations(
            PageResult<ParticipantStatisticsView> page, Long planId) {
        if (page.getRecords().isEmpty()) {
            return Collections.emptyMap();
        }
        LearningDurationSummaryView learning = RpcResultSupport.unwrap(
                learningStatisticsService.summarize(new LearningDurationQuery(
                        planId, page.getRecords().stream()
                                .map(ParticipantStatisticsView::taskId)
                                .collect(Collectors.toList()))));
        return learning.tasks().stream().collect(Collectors.toMap(
                TaskDurationView::taskId, Function.identity()));
    }

    /** 合并培训库任务快照和学习库有效学时。 */
    private ParticipantStatisticsResponse toParticipantResponse(
            ParticipantStatisticsView item, TaskDurationView duration) {
        return new ParticipantStatisticsResponse(
                item.taskId(), item.planId(), item.planName(), item.userId(), item.orgId(),
                item.orgName(), item.username(), item.displayName(), item.studyStatus(),
                item.examStatus(), item.completionStatus(), item.examScore(), item.examPassed(),
                item.requiredDurationMillis(),
                duration == null ? 0L : duration.effectiveDurationMillis(), item.completedAt());
    }

    /** 统计总览REST返回。 */
    public record StatisticsOverviewResponse(
            long planCount,
            long participantCount,
            long startedCount,
            long studyCompletedCount,
            long examPassedCount,
            long completedCount,
            BigDecimal completionRate,
            long requiredDurationMillis,
            long effectiveDurationMillis,
            long averageEffectiveDurationMillis) {
    }

    /** 学员培训明细REST返回。 */
    public record ParticipantStatisticsResponse(
            Long taskId,
            Long planId,
            String planName,
            Long userId,
            Long orgId,
            String orgName,
            String username,
            String displayName,
            String studyStatus,
            String examStatus,
            String completionStatus,
            Integer examScore,
            Boolean examPassed,
            long requiredDurationMillis,
            long effectiveDurationMillis,
            LocalDateTime completedAt) {
    }
}
