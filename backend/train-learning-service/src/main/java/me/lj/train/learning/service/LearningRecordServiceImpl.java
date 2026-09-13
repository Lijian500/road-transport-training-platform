package me.lj.train.learning.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.api.learning.LearningRecordModels.*;
import me.lj.train.api.learning.LearningRecordService;
import me.lj.train.api.learning.LearningStatisticsModels.TaskDurationView;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.page.PageRequest;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;
import me.lj.train.learning.mapper.*;
import me.lj.train.learning.model.entity.*;
import me.lj.train.learning.support.FaceReferenceImageClient;
import me.lj.train.learning.support.LearningGuard;
import me.lj.train.learning.support.LearningServiceSupport;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.*;
import java.util.stream.Collectors;

import static me.lj.train.learning.model.table.StudyProgressTableDef.STUDY_PROGRESS;
import static me.lj.train.learning.model.table.StudySessionTableDef.STUDY_SESSION;
import static me.lj.train.learning.model.table.StudyEventLogTableDef.STUDY_EVENT_LOG;
import static me.lj.train.learning.model.table.FaceCheckTaskTableDef.FACE_CHECK_TASK;
import static me.lj.train.learning.model.table.FaceCheckLogTableDef.FACE_CHECK_LOG;

/** 学习历史仅查询已有数据，不调用学习准入或进度初始化逻辑。 */
@DubboService(timeout = 10000, retries = 0)
public class LearningRecordServiceImpl extends LearningServiceSupport implements LearningRecordService {
    private final StudyProgressMapper progressMapper;
    private final StudySessionMapper sessionMapper;
    private final StudyEventLogMapper eventMapper;
    private final FaceCheckTaskMapper faceTaskMapper;
    private final FaceCheckLogMapper faceLogMapper;
    private final FaceReferenceImageClient imageClient;

    public LearningRecordServiceImpl(PlatformTransactionManager transactions, StudyProgressMapper progressMapper,
            StudySessionMapper sessionMapper, StudyEventLogMapper eventMapper,
            FaceCheckTaskMapper faceTaskMapper, FaceCheckLogMapper faceLogMapper, FaceReferenceImageClient imageClient) {
        super(transactions);
        this.progressMapper = progressMapper;
        this.sessionMapper = sessionMapper;
        this.eventMapper = eventMapper;
        this.faceTaskMapper = faceTaskMapper;
        this.faceLogMapper = faceLogMapper;
        this.imageClient = imageClient;
    }

    /** 学员批量读取学时，分页调用最多允许100个任务。 */
    @Override
    public Result<List<TaskDurationView>> myDurations(List<Long> taskIds) {
        return execute(() -> {
            LoginUser user = authorize(false);
            if (taskIds == null || taskIds.size() > 100 || taskIds.stream().anyMatch(id -> id == null || id <= 0)) {
                throw new BusinessException(AppErrorCode.PARAM_INVALID, "培训任务范围不正确");
            }
            if (taskIds.isEmpty()) return List.of();
            return progressMapper.selectListByQuery(QueryWrapper.create()
                    .where(STUDY_PROGRESS.ENTERPRISE_ID.eq(user.getEnterpriseId()))
                    .and(STUDY_PROGRESS.USER_ID.eq(user.getUserId())).and(STUDY_PROGRESS.TASK_ID.in(taskIds)))
                    .stream().collect(Collectors.groupingBy(StudyProgressEntity::getTaskId)).entrySet().stream()
                    .map(entry -> new TaskDurationView(entry.getKey(),
                            entry.getValue().stream().mapToLong(StudyProgressEntity::getRequiredDurationMs).sum(),
                            entry.getValue().stream().mapToLong(StudyProgressEntity::getEffectiveDurationMs).sum())).toList();
        });
    }

    /** 查询本人历史课程。 */
    @Override public Result<List<CourseRecordView>> myCourses(Long taskId) {
        return execute(() -> courses(taskId, false));
    }
    /** 查询企业内学员历史课程。 */
    @Override public Result<List<CourseRecordView>> adminCourses(Long taskId) {
        return execute(() -> courses(taskId, true));
    }
    /** 查询本人历史会话。 */
    @Override public Result<PageResult<SessionRecordView>> mySessions(SessionQuery query) {
        return execute(() -> sessions(query, false));
    }
    /** 查询企业内学员历史会话。 */
    @Override public Result<PageResult<SessionRecordView>> adminSessions(SessionQuery query) {
        return execute(() -> sessions(query, true));
    }
    /** 查询本人会话事件。 */
    @Override public Result<PageResult<EventRecordView>> myEvents(Long id, int number, int size) {
        return execute(() -> events(id, number, size, false));
    }
    /** 查询企业内会话事件。 */
    @Override public Result<PageResult<EventRecordView>> adminEvents(Long id, int number, int size) {
        return execute(() -> events(id, number, size, true));
    }
    /** 查询本人抽验任务，包括无提交的超时任务。 */
    @Override public Result<PageResult<FaceRecordView>> myFaceChecks(Long id, int number, int size) {
        return execute(() -> faceChecks(id, number, size, false));
    }
    /** 查询企业内抽验任务，包括无提交的超时任务。 */
    @Override public Result<PageResult<FaceRecordView>> adminFaceChecks(Long id, int number, int size) {
        return execute(() -> faceChecks(id, number, size, true));
    }

    /** 使用档案阅读权限，不要求当前任务仍可学习。 */
    private LoginUser authorize(boolean administrator) {
        LoginUser user = UserContext.require();
        if (administrator) {
            LearningGuard.requireStatisticsAdministrator();
        } else {
            if (user.isMustChangePassword()) throw new BusinessException(AppErrorCode.PASSWORD_CHANGE_REQUIRED);
            if (user.isPlatformAdmin() || user.getEnterpriseId() == null || !user.hasPermission("student:plan:view")) {
                throw new BusinessException(AppErrorCode.FORBIDDEN);
            }
        }
        return user;
    }

    /** 课程按发布顺序展示，无学习数据返回空集合。 */
    private List<CourseRecordView> courses(Long taskId, boolean administrator) {
        LoginUser user = authorize(administrator);
        requireId(taskId);
        return progressMapper.selectListByQuery(QueryWrapper.create()
                .where(STUDY_PROGRESS.ENTERPRISE_ID.eq(user.getEnterpriseId()))
                .and(STUDY_PROGRESS.USER_ID.eq(user.getUserId()).when(!administrator))
                .and(STUDY_PROGRESS.TASK_ID.eq(taskId)).orderBy(STUDY_PROGRESS.SORT_ORDER.asc(), STUDY_PROGRESS.ID.asc()))
                .stream().map(row -> new CourseRecordView(row.getPlanCourseId(), row.getCourseName(),
                        row.getRequiredDurationMs(), row.getEffectiveDurationMs(), row.getStatus(), row.getCompletedAt())).toList();
    }

    /** 会话按创建时间分页，避免加载完整心跳记录。 */
    private PageResult<SessionRecordView> sessions(SessionQuery query, boolean administrator) {
        LoginUser user = authorize(administrator);
        if (query == null) throw new BusinessException(AppErrorCode.PARAM_INVALID);
        requireId(query.taskId());
        String status = query.status() == null || query.status().isBlank() ? null : query.status().trim();
        if (status != null && !Set.of("CREATED", "SIGNED_IN", "STUDYING", "PAUSED", "DISCONNECTED",
                "FACE_PENDING", "COMPLETED", "SIGNED_OUT", "TERMINATED").contains(status)
                || query.fromTime() != null && query.toTime() != null && query.fromTime().isAfter(query.toTime())) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "会话筛选条件不正确");
        }
        PageRequest request = new PageRequest(query.pageNumber(), query.pageSize());
        QueryWrapper filter = QueryWrapper.create().where(STUDY_SESSION.ENTERPRISE_ID.eq(user.getEnterpriseId()))
                .and(STUDY_SESSION.USER_ID.eq(user.getUserId()).when(!administrator))
                .and(STUDY_SESSION.TASK_ID.eq(query.taskId())).and(STUDY_SESSION.STATUS.eq(status).when(status != null));
        if (query.fromTime() != null) filter.and(STUDY_SESSION.CREATED_AT.ge(query.fromTime()));
        if (query.toTime() != null) filter.and(STUDY_SESSION.CREATED_AT.le(query.toTime()));
        Page<StudySessionEntity> page = sessionMapper.paginate(request.getPageNumber(), request.getPageSize(),
                filter.orderBy(STUDY_SESSION.CREATED_AT.desc(), STUDY_SESSION.ID.desc()));
        return PageResult.of(page.getRecords().stream().map(row -> new SessionRecordView(row.getId(),
                row.getTaskId(), row.getPlanId(), row.getPlanCourseId(), row.getCourseName(), row.getStatus(),
                row.getCreatedAt(), row.getSignedInAt(), row.getStartedAt(), row.getSignedOutAt(),
                row.getTerminatedAt(), row.getTerminationReason(), imageClient.learningPhotoUrl(row.getSignInPhotoObjectId()),
                imageClient.learningPhotoUrl(row.getSignOutPhotoObjectId()))).toList(), page.getTotalRow(), request);
    }

    /** 每次读取子记录都重新验证会话所有权，防止更换会话ID越权。 */
    private StudySessionEntity requireSession(Long id, boolean administrator) {
        LoginUser user = authorize(administrator);
        requireId(id);
        StudySessionEntity session = sessionMapper.selectOneByQuery(QueryWrapper.create()
                .where(STUDY_SESSION.ID.eq(id)).and(STUDY_SESSION.ENTERPRISE_ID.eq(user.getEnterpriseId()))
                .and(STUDY_SESSION.USER_ID.eq(user.getUserId()).when(!administrator)));
        if (session == null || !user.getEnterpriseId().equals(session.getEnterpriseId())
                || !administrator && !user.getUserId().equals(session.getUserId())) {
            throw new BusinessException(AppErrorCode.RESOURCE_NOT_FOUND, "学习会话不存在");
        }
        return session;
    }

    /** 只返回事件解释所需字段，不返回缓存的完整响应。 */
    private PageResult<EventRecordView> events(Long id, int number, int size, boolean administrator) {
        StudySessionEntity session = requireSession(id, administrator);
        PageRequest request = new PageRequest(number, size);
        Page<StudyEventLogEntity> page = eventMapper.paginate(request.getPageNumber(), request.getPageSize(),
                QueryWrapper.create().where(STUDY_EVENT_LOG.ENTERPRISE_ID.eq(session.getEnterpriseId()))
                        .and(STUDY_EVENT_LOG.SESSION_ID.eq(id)).orderBy(STUDY_EVENT_LOG.SEQUENCE_NO.desc()));
        return PageResult.of(page.getRecords().stream().map(row -> new EventRecordView(row.getId(), row.getRequestId(),
                row.getSequenceNo(), row.getEventType(), row.getFromStatus(), row.getToStatus(), row.getReportedPositionMs(),
                row.getConfirmedPositionMs(), row.getCreditedDurationMs(), row.getResultCode(), row.getServerTime())).toList(),
                page.getTotalRow(), request);
    }

    /** 先分页抽验任务，再批量加载本页提交记录，保留零次提交的任务。 */
    private PageResult<FaceRecordView> faceChecks(Long id, int number, int size, boolean administrator) {
        StudySessionEntity session = requireSession(id, administrator);
        PageRequest request = new PageRequest(number, size);
        Page<FaceCheckTaskEntity> page = faceTaskMapper.paginate(request.getPageNumber(), request.getPageSize(),
                QueryWrapper.create().where(FACE_CHECK_TASK.ENTERPRISE_ID.eq(session.getEnterpriseId()))
                        .and(FACE_CHECK_TASK.SESSION_ID.eq(id))
                        .orderBy(FACE_CHECK_TASK.TRIGGERED_AT.desc(), FACE_CHECK_TASK.ID.desc()));
        if (page.getRecords().isEmpty()) return PageResult.of(List.of(), page.getTotalRow(), request);
        Map<Long, List<FaceCheckLogEntity>> logs = faceLogMapper.selectListByQuery(QueryWrapper.create()
                .where(FACE_CHECK_LOG.ENTERPRISE_ID.eq(session.getEnterpriseId())).and(FACE_CHECK_LOG.SESSION_ID.eq(id))
                .and(FACE_CHECK_LOG.TASK_ID.in(page.getRecords().stream().map(FaceCheckTaskEntity::getId).toList()))
                .orderBy(FACE_CHECK_LOG.ATTEMPT_NO.asc())).stream().collect(Collectors.groupingBy(FaceCheckLogEntity::getTaskId));
        return PageResult.of(page.getRecords().stream().map(row -> new FaceRecordView(row.getId(), row.getStatus(),
                row.getTriggeredAt(), row.getDeadlineAt(), row.getCompletedAt(), row.getFailureReason(), row.getAttemptCount(),
                logs.getOrDefault(row.getId(), List.of()).stream().map(log -> new FaceAttemptView(log.getAttemptNo(),
                        log.getResult(), log.getFailureReason(), log.getSimilarity(), log.getElapsedMs(), log.getCreatedAt(),
                        imageClient.learningPhotoUrl(log.getPhotoObjectId()))).toList()))
                .toList(), page.getTotalRow(), request);
    }

    /** 拒绝空或无效ID，避免误退化为全量查询。 */
    private void requireId(Long id) {
        if (id == null || id <= 0) throw new BusinessException(AppErrorCode.PARAM_INVALID);
    }
}
