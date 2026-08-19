package me.lj.train.training.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateWrapper;
import me.lj.train.api.admin.TrainingParticipantModels.ParticipantView;
import me.lj.train.api.training.PlanModels.CreatePlanCommand;
import me.lj.train.api.training.PlanModels.PlanCourseOptionView;
import me.lj.train.api.training.PlanModels.PlanParticipantOptionView;
import me.lj.train.api.training.PlanModels.PlanQuery;
import me.lj.train.api.training.PlanModels.PlanView;
import me.lj.train.api.training.PlanModels.UpdatePlanCommand;
import me.lj.train.api.training.PlanService;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.page.PageRequest;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.core.util.IdGenerator;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.training.mapper.CourseMapper;
import me.lj.train.training.mapper.CoursewareMapper;
import me.lj.train.training.mapper.PlanCourseMapper;
import me.lj.train.training.mapper.PlanCoursewareSnapshotMapper;
import me.lj.train.training.mapper.PlanMapper;
import me.lj.train.training.mapper.PlanUserMapper;
import me.lj.train.training.model.entity.CourseEntity;
import me.lj.train.training.model.entity.CoursewareEntity;
import me.lj.train.training.model.entity.PlanCourseEntity;
import me.lj.train.training.model.entity.PlanCoursewareSnapshotEntity;
import me.lj.train.training.model.entity.PlanEntity;
import me.lj.train.training.model.entity.PlanUserEntity;
import me.lj.train.training.support.ParticipantDirectoryClient;
import me.lj.train.training.support.TrainingGuard;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static me.lj.train.training.constant.TrainingConstants.ASSIGNMENT_ASSIGNED;
import static me.lj.train.training.constant.TrainingConstants.ASSIGNMENT_CANCELLED;
import static me.lj.train.training.constant.TrainingConstants.COMPLETION_NOT_COMPLETED;
import static me.lj.train.training.constant.TrainingConstants.COURSE_ENABLED;
import static me.lj.train.training.constant.TrainingConstants.EXAM_NOT_REQUIRED;
import static me.lj.train.training.constant.TrainingConstants.PLAN_CANCELLED;
import static me.lj.train.training.constant.TrainingConstants.PLAN_DRAFT;
import static me.lj.train.training.constant.TrainingConstants.PLAN_FINISHED;
import static me.lj.train.training.constant.TrainingConstants.PLAN_IN_PROGRESS;
import static me.lj.train.training.constant.TrainingConstants.PLAN_PUBLISHED;
import static me.lj.train.training.constant.TrainingConstants.STUDY_NOT_STARTED;
import static me.lj.train.training.constant.TrainingPermissions.PLAN_CANCEL;
import static me.lj.train.training.constant.TrainingPermissions.PLAN_CREATE;
import static me.lj.train.training.constant.TrainingPermissions.PLAN_PUBLISH;
import static me.lj.train.training.constant.TrainingPermissions.PLAN_UPDATE;
import static me.lj.train.training.constant.TrainingPermissions.PLAN_VIEW;
import static me.lj.train.training.model.table.CourseTableDef.COURSE;
import static me.lj.train.training.model.table.CoursewareTableDef.COURSEWARE;
import static me.lj.train.training.model.table.PlanCourseTableDef.PLAN_COURSE;
import static me.lj.train.training.model.table.PlanCoursewareSnapshotTableDef.PLAN_COURSEWARE_SNAPSHOT;
import static me.lj.train.training.model.table.PlanTableDef.PLAN;
import static me.lj.train.training.model.table.PlanUserTableDef.PLAN_USER;

/**
 * 企业培训计划、规则快照及学员分配RPC实现。
 */
@DubboService(timeout = 10000, retries = 0)
public class PlanServiceImpl extends TrainingServiceSupport implements PlanService {

    private static final int MAX_COURSES = 100;
    private static final int MAX_PARTICIPANTS = 500;

    private final PlanMapper planMapper;
    private final PlanCourseMapper planCourseMapper;
    private final PlanCoursewareSnapshotMapper snapshotMapper;
    private final PlanUserMapper planUserMapper;
    private final CourseMapper courseMapper;
    private final CoursewareMapper coursewareMapper;
    private final ParticipantDirectoryClient participantClient;
    private final PlanLifecycleService lifecycleService;
    private final PlanViewAssembler viewAssembler;

    public PlanServiceImpl(
            PlatformTransactionManager transactionManager,
            PlanMapper planMapper,
            PlanCourseMapper planCourseMapper,
            PlanCoursewareSnapshotMapper snapshotMapper,
            PlanUserMapper planUserMapper,
            CourseMapper courseMapper,
            CoursewareMapper coursewareMapper,
            ParticipantDirectoryClient participantClient,
            PlanLifecycleService lifecycleService,
            PlanViewAssembler viewAssembler) {
        super(transactionManager);
        this.planMapper = planMapper;
        this.planCourseMapper = planCourseMapper;
        this.snapshotMapper = snapshotMapper;
        this.planUserMapper = planUserMapper;
        this.courseMapper = courseMapper;
        this.coursewareMapper = coursewareMapper;
        this.participantClient = participantClient;
        this.lifecycleService = lifecycleService;
        this.viewAssembler = viewAssembler;
    }

    @Override
    public Result<PageResult<PlanView>> page(PlanQuery query) {
        return execute(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(PLAN_VIEW);
            lifecycleService.refreshStatuses();
            PageRequest request = query.toPageRequest();
            String keyword = trim(query.keyword());
            String status = normalizeStatus(query.status());
            QueryWrapper wrapper = QueryWrapper.create()
                    .where(PLAN.ENTERPRISE_ID.eq(enterpriseId))
                    .and(PLAN.DELETED_AT.isNull())
                    .and(PLAN.PLAN_NAME.like(keyword).when(hasText(keyword)))
                    .and(PLAN.STATUS.eq(status).when(hasText(status)))
                    .orderBy(PLAN.CREATED_AT.desc());
            Page<PlanEntity> page = planMapper.paginate(
                    request.getPageNumber(), request.getPageSize(), wrapper);
            List<PlanView> records = page.getRecords().stream()
                    .map(plan -> viewAssembler.toPlanView(plan, true))
                    .collect(Collectors.toList());
            return PageResult.of(records, page.getTotalRow(), request);
        });
    }

    @Override
    public Result<PlanView> create(CreatePlanCommand command) {
        return executeTransactional(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(PLAN_CREATE);
            validatePlanValues(command.name(), command.description(), command.startAt(),
                    command.endAt(), command.examRequired());
            Long operatorId = UserContext.require().getUserId();
            PlanEntity plan = new PlanEntity();
            plan.setId(IdGenerator.nextId());
            plan.setEnterpriseId(enterpriseId);
            applyPlanValues(plan, command.name(), command.description(),
                    command.startAt(), command.endAt(), command.examRequired());
            plan.setStatus(PLAN_DRAFT);
            plan.setCreatedBy(operatorId);
            plan.setUpdatedBy(operatorId);
            planMapper.insertSelective(plan);
            return viewAssembler.toPlanView(requirePlan(plan.getId(), enterpriseId, false), true);
        });
    }

    @Override
    public Result<PlanView> get(Long id) {
        return execute(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(PLAN_VIEW);
            lifecycleService.refreshStatuses();
            return viewAssembler.toPlanView(requirePlan(id, enterpriseId, false), true);
        });
    }

    @Override
    public Result<PlanView> update(UpdatePlanCommand command) {
        return executeTransactional(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(PLAN_UPDATE);
            PlanEntity plan = requirePlan(command.id(), enterpriseId, true);
            requireDraft(plan);
            validatePlanValues(command.name(), command.description(), command.startAt(),
                    command.endAt(), command.examRequired());
            List<Long> courseIds = normalizeIds(command.courseIds(), MAX_COURSES, "课程");
            List<Long> userIds = normalizeIds(command.userIds(), MAX_PARTICIPANTS, "学员");
            List<CourseEntity> courses = loadEnabledCourses(courseIds, enterpriseId);
            List<ParticipantView> participants = validateParticipants(userIds, enterpriseId);
            Long operatorId = UserContext.require().getUserId();
            UpdateWrapper<PlanEntity> update = UpdateWrapper.of(PlanEntity.class)
                    .set(PLAN.PLAN_NAME, TrainingGuard.requireText(command.name(), "计划名称", 128))
                    .set(PLAN.DESCRIPTION,
                            TrainingGuard.optionalText(command.description(), "计划说明", 1000))
                    .set(PLAN.START_AT, command.startAt())
                    .set(PLAN.END_AT, command.endAt())
                    .set(PLAN.EXAM_REQUIRED, false)
                    .set(PLAN.UPDATED_BY, operatorId);
            planMapper.updateByCondition(update.toEntity(), activePlanCondition(plan.getId(), enterpriseId)
                    .and(PLAN.STATUS.eq(PLAN_DRAFT)));
            replaceSnapshotsAndTasks(plan.getId(), enterpriseId, courses, participants, operatorId);
            return viewAssembler.toPlanView(requirePlan(plan.getId(), enterpriseId, false), true);
        });
    }

    @Override
    public Result<?> delete(Long id) {
        return executeVoidTransactional(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(PLAN_UPDATE);
            PlanEntity plan = requirePlan(id, enterpriseId, true);
            requireDraft(plan);
            deleteRelations(plan.getId(), enterpriseId);
            Long operatorId = UserContext.require().getUserId();
            LocalDateTime now = LocalDateTime.now();
            PlanEntity deleted = new PlanEntity();
            deleted.setDeletedBy(operatorId);
            deleted.setDeletedAt(now);
            deleted.setUpdatedBy(operatorId);
            planMapper.updateByCondition(deleted, activePlanCondition(plan.getId(), enterpriseId)
                    .and(PLAN.STATUS.eq(PLAN_DRAFT)));
        });
    }

    @Override
    public Result<PlanView> publish(Long id) {
        return executeTransactional(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(PLAN_PUBLISH);
            PlanEntity plan = requirePlan(id, enterpriseId, true);
            requireDraft(plan);
            validatePlanValues(plan.getPlanName(), plan.getDescription(), plan.getStartAt(),
                    plan.getEndAt(), plan.isExamRequired());
            LocalDateTime now = LocalDateTime.now();
            if (!plan.getEndAt().isAfter(now)) {
                throw new BusinessException(AppErrorCode.PLAN_PUBLISH_INVALID,
                        "计划结束时间必须晚于当前时间");
            }
            List<Long> courseIds = listPlanCourses(plan.getId(), enterpriseId).stream()
                    .map(PlanCourseEntity::getCourseId)
                    .collect(Collectors.toList());
            List<Long> userIds = listPlanUsers(plan.getId(), enterpriseId).stream()
                    .map(PlanUserEntity::getUserId)
                    .collect(Collectors.toList());
            if (courseIds.isEmpty() || userIds.isEmpty()) {
                throw new BusinessException(AppErrorCode.PLAN_PUBLISH_INVALID,
                        "发布前必须至少选择一门课程和一名学员");
            }
            List<CourseEntity> courses = loadEnabledCourses(courseIds, enterpriseId);
            List<ParticipantView> participants = validateParticipants(userIds, enterpriseId);
            Long operatorId = UserContext.require().getUserId();
            replaceSnapshotsAndTasks(plan.getId(), enterpriseId, courses, participants, operatorId);
            String status = plan.getStartAt().isAfter(now) ? PLAN_PUBLISHED : PLAN_IN_PROGRESS;
            UpdateWrapper<PlanEntity> publish = UpdateWrapper.of(PlanEntity.class)
                    .set(PLAN.STATUS, status)
                    .set(PLAN.PUBLISHED_BY, operatorId)
                    .set(PLAN.PUBLISHED_AT, now)
                    .set(PLAN.UPDATED_BY, operatorId);
            planMapper.updateByCondition(publish.toEntity(), activePlanCondition(plan.getId(), enterpriseId)
                    .and(PLAN.STATUS.eq(PLAN_DRAFT)));
            return viewAssembler.toPlanView(requirePlan(plan.getId(), enterpriseId, false), true);
        });
    }

    @Override
    public Result<PlanView> cancel(Long id) {
        return executeTransactional(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(PLAN_CANCEL);
            PlanEntity plan = requirePlan(id, enterpriseId, true);
            LocalDateTime now = LocalDateTime.now();
            if (!PLAN_PUBLISHED.equals(plan.getStatus()) || !plan.getStartAt().isAfter(now)) {
                throw new BusinessException(AppErrorCode.PLAN_STATE_INVALID,
                        "只有尚未开始的已发布计划可以取消");
            }
            Long operatorId = UserContext.require().getUserId();
            UpdateWrapper<PlanEntity> cancel = UpdateWrapper.of(PlanEntity.class)
                    .set(PLAN.STATUS, PLAN_CANCELLED)
                    .set(PLAN.CANCELLED_BY, operatorId)
                    .set(PLAN.CANCELLED_AT, now)
                    .set(PLAN.UPDATED_BY, operatorId);
            planMapper.updateByCondition(cancel.toEntity(), activePlanCondition(plan.getId(), enterpriseId)
                    .and(PLAN.STATUS.eq(PLAN_PUBLISHED)));
            UpdateWrapper<PlanUserEntity> cancelTasks = UpdateWrapper.of(PlanUserEntity.class)
                    .set(PLAN_USER.ASSIGNMENT_STATUS, ASSIGNMENT_CANCELLED)
                    .set(PLAN_USER.UPDATED_BY, operatorId);
            planUserMapper.updateByCondition(cancelTasks.toEntity(),
                    PLAN_USER.ENTERPRISE_ID.eq(enterpriseId).and(PLAN_USER.PLAN_ID.eq(plan.getId())));
            return viewAssembler.toPlanView(requirePlan(plan.getId(), enterpriseId, false), true);
        });
    }

    @Override
    public Result<List<PlanCourseOptionView>> listCourseCandidates(String keyword) {
        return execute(() -> {
            Long enterpriseId = TrainingGuard.requireEnterpriseAnyPermission(PLAN_CREATE, PLAN_UPDATE);
            String normalizedKeyword = trim(keyword);
            Page<CourseEntity> page = courseMapper.paginate(1, 500, QueryWrapper.create()
                    .where(COURSE.ENTERPRISE_ID.eq(enterpriseId))
                    .and(COURSE.DELETED_AT.isNull())
                    .and(COURSE.STATUS.eq(COURSE_ENABLED))
                    .and(COURSE.COURSE_NAME.like(normalizedKeyword).when(hasText(normalizedKeyword)))
                    .orderBy(COURSE.COURSE_NAME.asc(), COURSE.ID.asc()));
            return page.getRecords().stream().map(course -> {
                List<CoursewareEntity> coursewares = listCoursewares(course.getId(), enterpriseId);
                long totalDuration = coursewares.stream()
                        .mapToLong(CoursewareEntity::getDurationSeconds)
                        .sum();
                return new PlanCourseOptionView(
                        course.getId(), course.getCourseName(), course.getRequiredDurationSeconds(),
                        coursewares.size(), totalDuration);
            }).collect(Collectors.toList());
        });
    }

    @Override
    public Result<List<PlanParticipantOptionView>> listParticipantCandidates(
            String keyword, Long orgId) {
        return execute(() -> {
            TrainingGuard.requireEnterpriseAnyPermission(PLAN_CREATE, PLAN_UPDATE);
            return participantClient.listCandidates(keyword, orgId).stream()
                    .map(participant -> new PlanParticipantOptionView(
                            participant.userId(), participant.orgId(), participant.orgName(),
                            participant.username(), participant.displayName()))
                    .collect(Collectors.toList());
        });
    }

    private void replaceSnapshotsAndTasks(
            Long planId,
            Long enterpriseId,
            List<CourseEntity> courses,
            List<ParticipantView> participants,
            Long operatorId) {
        deleteRelations(planId, enterpriseId);
        for (int index = 0; index < courses.size(); index++) {
            CourseEntity course = courses.get(index);
            PlanCourseEntity planCourse = new PlanCourseEntity();
            planCourse.setId(IdGenerator.nextId());
            planCourse.setEnterpriseId(enterpriseId);
            planCourse.setPlanId(planId);
            planCourse.setCourseId(course.getId());
            planCourse.setCourseName(course.getCourseName());
            planCourse.setRequiredDurationSeconds(course.getRequiredDurationSeconds());
            planCourse.setAllowSeek(course.isAllowSeek());
            planCourse.setProgressReportIntervalSeconds(course.getProgressReportIntervalSeconds());
            planCourse.setStudyToleranceSeconds(course.getStudyToleranceSeconds());
            planCourse.setSortOrder(index + 1);
            planCourse.setCreatedBy(operatorId);
            planCourse.setUpdatedBy(operatorId);
            planCourseMapper.insertSelective(planCourse);
            List<CoursewareEntity> coursewares = listCoursewares(course.getId(), enterpriseId);
            if (coursewares.isEmpty()) {
                throw new BusinessException(AppErrorCode.PLAN_PUBLISH_INVALID,
                        "计划课程必须包含有效视频课件");
            }
            for (CoursewareEntity courseware : coursewares) {
                PlanCoursewareSnapshotEntity snapshot = new PlanCoursewareSnapshotEntity();
                snapshot.setId(IdGenerator.nextId());
                snapshot.setEnterpriseId(enterpriseId);
                snapshot.setPlanId(planId);
                snapshot.setPlanCourseId(planCourse.getId());
                snapshot.setCourseId(course.getId());
                snapshot.setSourceCoursewareId(courseware.getId());
                snapshot.setStorageObjectId(courseware.getStorageObjectId());
                snapshot.setCoursewareTitle(courseware.getCoursewareTitle());
                snapshot.setDurationSeconds(courseware.getDurationSeconds());
                snapshot.setSortOrder(courseware.getSortOrder());
                snapshot.setCreatedBy(operatorId);
                snapshot.setUpdatedBy(operatorId);
                snapshotMapper.insertSelective(snapshot);
            }
        }
        for (ParticipantView participant : participants) {
            PlanUserEntity task = new PlanUserEntity();
            task.setId(IdGenerator.nextId());
            task.setEnterpriseId(enterpriseId);
            task.setPlanId(planId);
            task.setUserId(participant.userId());
            task.setOrgId(participant.orgId());
            task.setOrgName(participant.orgName());
            task.setUsername(participant.username());
            task.setDisplayName(participant.displayName());
            task.setAssignmentStatus(ASSIGNMENT_ASSIGNED);
            task.setStudyStatus(STUDY_NOT_STARTED);
            task.setExamStatus(EXAM_NOT_REQUIRED);
            task.setCompletionStatus(COMPLETION_NOT_COMPLETED);
            task.setCreatedBy(operatorId);
            task.setUpdatedBy(operatorId);
            planUserMapper.insertSelective(task);
        }
    }

    private void deleteRelations(Long planId, Long enterpriseId) {
        snapshotMapper.deleteByQuery(QueryWrapper.create()
                .where(PLAN_COURSEWARE_SNAPSHOT.ENTERPRISE_ID.eq(enterpriseId))
                .and(PLAN_COURSEWARE_SNAPSHOT.PLAN_ID.eq(planId)));
        planCourseMapper.deleteByQuery(QueryWrapper.create()
                .where(PLAN_COURSE.ENTERPRISE_ID.eq(enterpriseId))
                .and(PLAN_COURSE.PLAN_ID.eq(planId)));
        planUserMapper.deleteByQuery(QueryWrapper.create()
                .where(PLAN_USER.ENTERPRISE_ID.eq(enterpriseId))
                .and(PLAN_USER.PLAN_ID.eq(planId)));
    }

    private List<CourseEntity> loadEnabledCourses(List<Long> courseIds, Long enterpriseId) {
        if (courseIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, CourseEntity> courseMap = courseMapper.selectListByQuery(QueryWrapper.create()
                        .where(COURSE.ENTERPRISE_ID.eq(enterpriseId))
                        .and(COURSE.ID.in(courseIds))
                        .and(COURSE.STATUS.eq(COURSE_ENABLED))
                        .and(COURSE.DELETED_AT.isNull()))
                .stream()
                .collect(Collectors.toMap(CourseEntity::getId, Function.identity()));
        if (courseMap.size() != courseIds.size()) {
            throw new BusinessException(AppErrorCode.PLAN_PUBLISH_INVALID,
                    "计划只能选择当前组织已启用的课程");
        }
        return courseIds.stream().map(courseMap::get).collect(Collectors.toList());
    }

    private List<ParticipantView> validateParticipants(List<Long> userIds, Long enterpriseId) {
        if (userIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<ParticipantView> participants = participantClient.validate(userIds);
        if (participants == null || participants.size() != userIds.size()
                || participants.stream().anyMatch(item -> !enterpriseId.equals(item.enterpriseId()))) {
            throw new BusinessException(AppErrorCode.PLAN_PARTICIPANT_INVALID);
        }
        Map<Long, ParticipantView> participantMap = participants.stream()
                .collect(Collectors.toMap(ParticipantView::userId, Function.identity()));
        return userIds.stream().map(participantMap::get).collect(Collectors.toList());
    }

    private List<PlanCourseEntity> listPlanCourses(Long planId, Long enterpriseId) {
        return planCourseMapper.selectListByQuery(QueryWrapper.create()
                .where(PLAN_COURSE.ENTERPRISE_ID.eq(enterpriseId))
                .and(PLAN_COURSE.PLAN_ID.eq(planId))
                .orderBy(PLAN_COURSE.SORT_ORDER.asc()));
    }

    private List<PlanUserEntity> listPlanUsers(Long planId, Long enterpriseId) {
        return planUserMapper.selectListByQuery(QueryWrapper.create()
                .where(PLAN_USER.ENTERPRISE_ID.eq(enterpriseId))
                .and(PLAN_USER.PLAN_ID.eq(planId))
                .orderBy(PLAN_USER.ID.asc()));
    }

    private List<CoursewareEntity> listCoursewares(Long courseId, Long enterpriseId) {
        return coursewareMapper.selectListByQuery(QueryWrapper.create()
                .where(COURSEWARE.ENTERPRISE_ID.eq(enterpriseId))
                .and(COURSEWARE.COURSE_ID.eq(courseId))
                .and(COURSEWARE.DELETED_AT.isNull())
                .orderBy(COURSEWARE.SORT_ORDER.asc(), COURSEWARE.CREATED_AT.asc()));
    }

    private PlanEntity requirePlan(Long id, Long enterpriseId, boolean lock) {
        QueryWrapper wrapper = QueryWrapper.create()
                .where(PLAN.ID.eq(id))
                .and(PLAN.ENTERPRISE_ID.eq(enterpriseId))
                .and(PLAN.DELETED_AT.isNull());
        if (lock) {
            wrapper.forUpdate();
        }
        PlanEntity plan = id == null ? null : planMapper.selectOneByQuery(wrapper);
        if (plan == null) {
            throw new BusinessException(AppErrorCode.PLAN_NOT_FOUND);
        }
        TrainingGuard.checkEnterprise(plan.getEnterpriseId(), enterpriseId);
        return plan;
    }

    private void requireDraft(PlanEntity plan) {
        if (!PLAN_DRAFT.equals(plan.getStatus())) {
            throw new BusinessException(AppErrorCode.PLAN_STATE_INVALID,
                    "只有草稿计划可以编辑或删除");
        }
    }

    private void validatePlanValues(
            String name,
            String description,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean examRequired) {
        TrainingGuard.requireText(name, "计划名称", 128);
        TrainingGuard.optionalText(description, "计划说明", 1000);
        if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "计划开始时间必须早于结束时间");
        }
        if (examRequired) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "考试模块尚未启用，本期计划不能要求考试");
        }
    }

    private void applyPlanValues(
            PlanEntity plan,
            String name,
            String description,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean examRequired) {
        plan.setPlanName(TrainingGuard.requireText(name, "计划名称", 128));
        plan.setDescription(TrainingGuard.optionalText(description, "计划说明", 1000));
        plan.setStartAt(startAt);
        plan.setEndAt(endAt);
        plan.setExamRequired(examRequired);
    }

    private List<Long> normalizeIds(List<Long> values, int maxSize, String fieldName) {
        List<Long> ids = values == null
                ? Collections.emptyList()
                : new LinkedHashSet<Long>(values).stream()
                        .filter(id -> id != null)
                        .collect(Collectors.toList());
        if (ids.size() > maxSize) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID,
                    fieldName + "数量不能超过" + maxSize + "个");
        }
        return ids;
    }

    private com.mybatisflex.core.query.QueryCondition activePlanCondition(
            Long planId, Long enterpriseId) {
        return PLAN.ID.eq(planId)
                .and(PLAN.ENTERPRISE_ID.eq(enterpriseId))
                .and(PLAN.DELETED_AT.isNull());
    }

    private String normalizeStatus(String value) {
        String status = trim(value);
        if (!hasText(status)) {
            return null;
        }
        if (!PLAN_DRAFT.equals(status) && !PLAN_PUBLISHED.equals(status)
                && !PLAN_IN_PROGRESS.equals(status) && !PLAN_FINISHED.equals(status)
                && !PLAN_CANCELLED.equals(status)) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "计划状态不正确");
        }
        return status;
    }

    private boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
