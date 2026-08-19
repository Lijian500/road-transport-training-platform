package me.lj.train.training.service;

import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.api.training.LearningAccessModels.LearningCourseRuleView;
import me.lj.train.api.training.LearningAccessModels.LearningCoursewareRuleView;
import me.lj.train.api.training.LearningAccessModels.LearningPlaybackCommand;
import me.lj.train.api.training.LearningAccessModels.LearningTaskContextView;
import me.lj.train.api.training.LearningAccessModels.LearningTaskQuery;
import me.lj.train.api.training.LearningAccessService;
import me.lj.train.api.training.StorageModels.SignedRequestView;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.training.config.OssStorageProperties;
import me.lj.train.training.mapper.PlanCourseMapper;
import me.lj.train.training.mapper.PlanCoursewareSnapshotMapper;
import me.lj.train.training.mapper.PlanMapper;
import me.lj.train.training.mapper.PlanUserMapper;
import me.lj.train.training.mapper.StorageObjectMapper;
import me.lj.train.training.model.entity.PlanCourseEntity;
import me.lj.train.training.model.entity.PlanCoursewareSnapshotEntity;
import me.lj.train.training.model.entity.PlanEntity;
import me.lj.train.training.model.entity.PlanUserEntity;
import me.lj.train.training.model.entity.StorageObjectEntity;
import me.lj.train.training.storage.ObjectStorageService;
import me.lj.train.training.storage.ObjectStorageService.SignedRequest;
import me.lj.train.training.support.TrainingGuard;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static me.lj.train.training.constant.TrainingConstants.ASSIGNMENT_ASSIGNED;
import static me.lj.train.training.constant.TrainingConstants.OBJECT_ACTIVE;
import static me.lj.train.training.constant.TrainingConstants.OBJECT_RETAINED;
import static me.lj.train.training.constant.TrainingConstants.PLAN_IN_PROGRESS;
import static me.lj.train.training.constant.TrainingPermissions.STUDENT_LEARNING_STUDY;
import static me.lj.train.training.model.table.PlanCourseTableDef.PLAN_COURSE;
import static me.lj.train.training.model.table.PlanCoursewareSnapshotTableDef.PLAN_COURSEWARE_SNAPSHOT;
import static me.lj.train.training.model.table.PlanTableDef.PLAN;
import static me.lj.train.training.model.table.PlanUserTableDef.PLAN_USER;
import static me.lj.train.training.model.table.StorageObjectTableDef.STORAGE_OBJECT;

/**
 * 学员任务资格和私有OSS视频签名实现。
 */
@DubboService(timeout = 8000, retries = 0)
public class LearningAccessServiceImpl extends TrainingServiceSupport implements LearningAccessService {

    private final PlanMapper planMapper;
    private final PlanCourseMapper planCourseMapper;
    private final PlanCoursewareSnapshotMapper snapshotMapper;
    private final PlanUserMapper planUserMapper;
    private final StorageObjectMapper storageObjectMapper;
    private final PlanLifecycleService lifecycleService;
    private final ObjectStorageService objectStorageService;
    private final OssStorageProperties properties;

    public LearningAccessServiceImpl(
            PlatformTransactionManager transactionManager,
            PlanMapper planMapper,
            PlanCourseMapper planCourseMapper,
            PlanCoursewareSnapshotMapper snapshotMapper,
            PlanUserMapper planUserMapper,
            StorageObjectMapper storageObjectMapper,
            PlanLifecycleService lifecycleService,
            ObjectStorageService objectStorageService,
            OssStorageProperties properties) {
        super(transactionManager);
        this.planMapper = planMapper;
        this.planCourseMapper = planCourseMapper;
        this.snapshotMapper = snapshotMapper;
        this.planUserMapper = planUserMapper;
        this.storageObjectMapper = storageObjectMapper;
        this.lifecycleService = lifecycleService;
        this.objectStorageService = objectStorageService;
        this.properties = properties;
    }

    @Override
    public Result<LearningTaskContextView> getTaskContext(LearningTaskQuery query) {
        return execute(() -> {
            Context context = requireContext(query == null ? null : query.planId());
            List<PlanCourseEntity> courses = planCourseMapper.selectListByQuery(QueryWrapper.create()
                    .where(PLAN_COURSE.ENTERPRISE_ID.eq(context.enterpriseId))
                    .and(PLAN_COURSE.PLAN_ID.eq(context.plan.getId()))
                    .orderBy(PLAN_COURSE.SORT_ORDER.asc(), PLAN_COURSE.ID.asc()));
            List<Long> courseIds = courses.stream().map(PlanCourseEntity::getId).toList();
            Map<Long, List<PlanCoursewareSnapshotEntity>> snapshots = courseIds.isEmpty()
                    ? Collections.emptyMap()
                    : snapshotMapper.selectListByQuery(QueryWrapper.create()
                            .where(PLAN_COURSEWARE_SNAPSHOT.ENTERPRISE_ID.eq(context.enterpriseId))
                            .and(PLAN_COURSEWARE_SNAPSHOT.PLAN_COURSE_ID.in(courseIds))
                            .orderBy(PLAN_COURSEWARE_SNAPSHOT.SORT_ORDER.asc(),
                                    PLAN_COURSEWARE_SNAPSHOT.ID.asc()))
                            .stream().collect(Collectors.groupingBy(
                                    PlanCoursewareSnapshotEntity::getPlanCourseId));
            List<LearningCourseRuleView> ruleViews = courses.stream()
                    .map(course -> toCourseView(course, snapshots.getOrDefault(
                            course.getId(), Collections.emptyList())))
                    .toList();
            return new LearningTaskContextView(
                    context.task.getId(), context.plan.getId(), context.plan.getPlanName(),
                    context.plan.getStartAt(), context.plan.getEndAt(), context.plan.getStatus(),
                    context.task.getAssignmentStatus(), context.task.getStudyStatus(),
                    context.task.getCompletionStatus(), ruleViews);
        });
    }

    @Override
    public Result<SignedRequestView> createCoursewarePlaybackUrl(LearningPlaybackCommand command) {
        return execute(() -> {
            if (command == null || command.taskId() == null || command.planId() == null
                    || command.planCourseId() == null || command.coursewareSnapshotId() == null) {
                throw new BusinessException(AppErrorCode.PARAM_INVALID);
            }
            Context context = requireContext(command.planId());
            if (!command.taskId().equals(context.task.getId())) {
                throw new BusinessException(AppErrorCode.LEARNING_ACCESS_DENIED);
            }
            PlanCourseEntity course = planCourseMapper.selectOneByQuery(QueryWrapper.create()
                    .where(PLAN_COURSE.ID.eq(command.planCourseId()))
                    .and(PLAN_COURSE.ENTERPRISE_ID.eq(context.enterpriseId))
                    .and(PLAN_COURSE.PLAN_ID.eq(command.planId())));
            PlanCoursewareSnapshotEntity snapshot = snapshotMapper.selectOneByQuery(
                    QueryWrapper.create()
                            .where(PLAN_COURSEWARE_SNAPSHOT.ID.eq(command.coursewareSnapshotId()))
                            .and(PLAN_COURSEWARE_SNAPSHOT.ENTERPRISE_ID.eq(context.enterpriseId))
                            .and(PLAN_COURSEWARE_SNAPSHOT.PLAN_ID.eq(command.planId()))
                            .and(PLAN_COURSEWARE_SNAPSHOT.PLAN_COURSE_ID.eq(command.planCourseId())));
            if (course == null || snapshot == null) {
                throw new BusinessException(AppErrorCode.LEARNING_ACCESS_DENIED);
            }
            StorageObjectEntity object = storageObjectMapper.selectOneByQuery(QueryWrapper.create()
                    .where(STORAGE_OBJECT.ID.eq(snapshot.getStorageObjectId()))
                    .and(STORAGE_OBJECT.ENTERPRISE_ID.eq(context.enterpriseId))
                    .and(STORAGE_OBJECT.STATUS.in(OBJECT_ACTIVE, OBJECT_RETAINED)));
            if (object == null) {
                throw new BusinessException(AppErrorCode.LEARNING_PLAYBACK_UNAVAILABLE,
                        "视频存储对象不存在或不可用");
            }
            if (!objectStorageService.isEnabled()) {
                throw new BusinessException(AppErrorCode.LEARNING_PLAYBACK_UNAVAILABLE,
                        objectStorageService.disabledMessage());
            }
            SignedRequest request = objectStorageService.presignGet(
                    object.getObjectKey(), Duration.ofSeconds(properties.getLearningUrlTtlSeconds()));
            return new SignedRequestView(null, request.url(), request.method(), request.headers(),
                    LocalDateTime.ofInstant(request.expiresAt(), ZoneId.systemDefault()));
        });
    }

    private Context requireContext(Long planId) {
        Long enterpriseId = TrainingGuard.requireEnterprisePermission(STUDENT_LEARNING_STUDY);
        Long userId = UserContext.require().getUserId();
        if (planId == null) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "培训计划不能为空");
        }
        lifecycleService.refreshStatus(enterpriseId, planId);
        PlanEntity plan = planMapper.selectOneByQuery(QueryWrapper.create()
                .where(PLAN.ID.eq(planId))
                .and(PLAN.ENTERPRISE_ID.eq(enterpriseId))
                .and(PLAN.DELETED_AT.isNull()));
        PlanUserEntity task = planUserMapper.selectOneByQuery(QueryWrapper.create()
                .where(PLAN_USER.ENTERPRISE_ID.eq(enterpriseId))
                .and(PLAN_USER.PLAN_ID.eq(planId))
                .and(PLAN_USER.USER_ID.eq(userId)));
        LocalDateTime now = LocalDateTime.now();
        if (plan == null || task == null || !ASSIGNMENT_ASSIGNED.equals(task.getAssignmentStatus())
                || !PLAN_IN_PROGRESS.equals(plan.getStatus())
                || now.isBefore(plan.getStartAt()) || !now.isBefore(plan.getEndAt())) {
            throw new BusinessException(AppErrorCode.LEARNING_ACCESS_DENIED);
        }
        return new Context(enterpriseId, plan, task);
    }

    private LearningCourseRuleView toCourseView(
            PlanCourseEntity course, List<PlanCoursewareSnapshotEntity> snapshots) {
        List<LearningCoursewareRuleView> coursewares = snapshots.stream()
                .map(value -> new LearningCoursewareRuleView(
                        value.getId(), value.getCoursewareTitle(), value.getDurationSeconds(),
                        value.getSortOrder()))
                .toList();
        return new LearningCourseRuleView(
                course.getId(), course.getCourseId(), course.getCourseName(),
                course.getRequiredDurationSeconds(), course.isAllowSeek(),
                course.getProgressReportIntervalSeconds(), course.getStudyToleranceSeconds(),
                course.getSortOrder(), coursewares);
    }

    private record Context(Long enterpriseId, PlanEntity plan, PlanUserEntity task) {
    }
}
