package me.lj.train.learning.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.api.learning.LearningModels.CourseProgressView;
import me.lj.train.api.learning.LearningModels.LearningEventResultView;
import me.lj.train.api.learning.LearningModels.LearningSessionView;
import me.lj.train.api.learning.LearningModels.OpenSessionCommand;
import me.lj.train.api.learning.LearningModels.PlanProgressView;
import me.lj.train.api.learning.LearningModels.PlaybackUrlCommand;
import me.lj.train.api.learning.LearningModels.SubmitEventCommand;
import me.lj.train.api.learning.LearningModels.TerminateSessionCommand;
import me.lj.train.api.learning.LearningSessionService;
import me.lj.train.api.training.LearningAccessModels.LearningCourseRuleView;
import me.lj.train.api.training.LearningAccessModels.LearningPlaybackCommand;
import me.lj.train.api.training.LearningAccessModels.LearningTaskContextView;
import me.lj.train.api.training.LearningTaskEvents;
import me.lj.train.api.training.StorageModels.SignedRequestView;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.core.util.IdGenerator;
import me.lj.train.common.security.model.LoginUser;
import me.lj.train.learning.mapper.StudyCoursewareProgressMapper;
import me.lj.train.learning.mapper.StudyEventLogMapper;
import me.lj.train.learning.mapper.StudyProgressMapper;
import me.lj.train.learning.mapper.StudySessionMapper;
import me.lj.train.learning.model.entity.StudyCoursewareProgressEntity;
import me.lj.train.learning.model.entity.StudyEventLogEntity;
import me.lj.train.learning.model.entity.StudyProgressEntity;
import me.lj.train.learning.model.entity.StudySessionEntity;
import me.lj.train.learning.support.LearningGuard;
import me.lj.train.learning.support.LearningServiceSupport;
import me.lj.train.learning.support.TrainingAccessClient;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static me.lj.train.learning.model.table.StudyEventLogTableDef.STUDY_EVENT_LOG;
import static me.lj.train.learning.model.table.StudyCoursewareProgressTableDef.STUDY_COURSEWARE_PROGRESS;
import static me.lj.train.learning.model.table.StudyProgressTableDef.STUDY_PROGRESS;
import static me.lj.train.learning.model.table.StudySessionTableDef.STUDY_SESSION;
import static me.lj.train.learning.service.LearningProgressManager.COMPLETED;
import static me.lj.train.learning.service.LearningProgressManager.IN_PROGRESS;
import static me.lj.train.learning.service.LearningProgressManager.NOT_STARTED;

/**
 * REST版本学习状态机、有效学时和播放授权实现。
 */
@DubboService(timeout = 10000, retries = 0)
public class LearningSessionServiceImpl extends LearningServiceSupport
        implements LearningSessionService {

    static final String CREATED = "CREATED";
    static final String SIGNED_IN = "SIGNED_IN";
    static final String STUDYING = "STUDYING";
    static final String PAUSED = "PAUSED";
    static final String SESSION_COMPLETED = "COMPLETED";
    static final String SIGNED_OUT = "SIGNED_OUT";
    static final String TERMINATED = "TERMINATED";

    private static final Set<String> ACTIVE_STATUSES = Set.of(
            CREATED, SIGNED_IN, STUDYING, PAUSED);
    private static final Set<String> EVENT_TYPES = Set.of(
            "SIGN_IN", "PLAY", "PROGRESS", "PAUSE", "SIGN_OUT");

    private final StudySessionMapper sessionMapper;
    private final StudyProgressMapper progressMapper;
    private final StudyCoursewareProgressMapper coursewareProgressMapper;
    private final StudyEventLogMapper eventLogMapper;
    private final LearningProgressManager progressManager;
    private final LearningOutboxService outboxService;
    private final TrainingAccessClient trainingAccessClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final LearningTimeCalculator timeCalculator;

    public LearningSessionServiceImpl(
            PlatformTransactionManager transactionManager,
            StudySessionMapper sessionMapper,
            StudyProgressMapper progressMapper,
            StudyCoursewareProgressMapper coursewareProgressMapper,
            StudyEventLogMapper eventLogMapper,
            LearningProgressManager progressManager,
            LearningOutboxService outboxService,
            TrainingAccessClient trainingAccessClient,
            ObjectMapper objectMapper,
            Clock clock,
            LearningTimeCalculator timeCalculator) {
        super(transactionManager);
        this.sessionMapper = sessionMapper;
        this.progressMapper = progressMapper;
        this.coursewareProgressMapper = coursewareProgressMapper;
        this.eventLogMapper = eventLogMapper;
        this.progressManager = progressManager;
        this.outboxService = outboxService;
        this.trainingAccessClient = trainingAccessClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.timeCalculator = timeCalculator;
    }

    @Override
    public Result<PlanProgressView> getPlanProgress(Long planId) {
        return executeTransactional(() -> {
            LoginUser user = LearningGuard.requireStudent();
            LearningTaskContextView context = trainingAccessClient.taskContext(planId);
            progressManager.ensureProgress(user.getEnterpriseId(), user.getUserId(), context);
            return progressManager.toPlanView(user.getEnterpriseId(), user.getUserId(), context);
        });
    }

    @Override
    public Result<CourseProgressView> getCourse(Long planId, Long planCourseId) {
        return executeTransactional(() -> {
            LoginUser user = LearningGuard.requireStudent();
            LearningTaskContextView context = trainingAccessClient.taskContext(planId);
            requireRule(context, planCourseId);
            progressManager.ensureProgress(user.getEnterpriseId(), user.getUserId(), context);
            StudyProgressEntity progress = progressManager.requireProgress(
                    user.getEnterpriseId(), user.getUserId(), planId, planCourseId);
            return progressManager.toCourseView(progress, progressManager.coursewares(
                    user.getEnterpriseId(), user.getUserId(), planCourseId));
        });
    }

    @Override
    public Result<LearningSessionView> openSession(OpenSessionCommand command) {
        return executeTransactional(() -> {
            LoginUser user = LearningGuard.requireStudent();
            if (command == null || command.planId() == null || command.planCourseId() == null) {
                throw new BusinessException(AppErrorCode.PARAM_INVALID);
            }
            String clientInstanceId = requireIdentifier(
                    command.clientInstanceId(), "客户端实例ID");
            LearningTaskContextView context = trainingAccessClient.taskContext(command.planId());
            LearningCourseRuleView rule = requireRule(context, command.planCourseId());
            progressManager.ensureProgress(user.getEnterpriseId(), user.getUserId(), context);
            StudySessionEntity active = activeSession(
                    user.getEnterpriseId(), user.getUserId(), true);
            if (active != null) {
                if (active.getPlanCourseId().equals(command.planCourseId())
                        && active.getClientInstanceId().equals(clientInstanceId)) {
                    return toSessionView(active);
                }
                throw new BusinessException(AppErrorCode.LEARNING_SESSION_CONFLICT,
                        "已有活动学习会话，请先签退或终止原会话");
            }
            StudySessionEntity session = new StudySessionEntity();
            session.setId(IdGenerator.nextId());
            session.setEnterpriseId(user.getEnterpriseId());
            session.setUserId(user.getUserId());
            session.setTaskId(context.taskId());
            session.setPlanId(context.planId());
            session.setPlanCourseId(rule.id());
            session.setClientInstanceId(clientInstanceId);
            session.setCourseName(rule.courseName());
            session.setSortOrder(rule.sortOrder());
            session.setPlanEndAt(context.endAt());
            session.setStatus(CREATED);
            session.setCreatedAt(now());
            try {
                sessionMapper.insertSelective(session);
            } catch (DuplicateKeyException exception) {
                StudySessionEntity concurrent = activeSession(
                        user.getEnterpriseId(), user.getUserId(), true);
                if (concurrent != null
                        && concurrent.getPlanCourseId().equals(command.planCourseId())
                        && concurrent.getClientInstanceId().equals(clientInstanceId)) {
                    return toSessionView(concurrent);
                }
                throw new BusinessException(AppErrorCode.LEARNING_SESSION_CONFLICT,
                        "已有活动学习会话，请先签退或终止原会话");
            }
            return toSessionView(session);
        });
    }

    @Override
    public Result<LearningSessionView> getActiveSession() {
        return execute(() -> {
            LoginUser user = LearningGuard.requireStudent();
            StudySessionEntity session = activeSession(
                    user.getEnterpriseId(), user.getUserId(), false);
            return session == null ? null : toSessionView(session);
        });
    }

    @Override
    public Result<LearningSessionView> getSession(Long sessionId) {
        return execute(() -> {
            LoginUser user = LearningGuard.requireStudent();
            return toSessionView(requireOwnedSession(
                    sessionId, user.getEnterpriseId(), user.getUserId(), false));
        });
    }

    @Override
    public Result<LearningEventResultView> submitEvent(SubmitEventCommand command) {
        return executeTransactional(() -> processEvent(command));
    }

    @Override
    public Result<?> terminateSession(TerminateSessionCommand command) {
        return executeVoidTransactional(() -> {
            LoginUser user = LearningGuard.requireStudent();
            Long sessionId = command == null ? null : command.sessionId();
            StudySessionEntity session = requireOwnedSession(
                    sessionId, user.getEnterpriseId(), user.getUserId(), true);
            if (!ACTIVE_STATUSES.contains(session.getStatus())) {
                return;
            }
            LocalDateTime now = now();
            String fromStatus = session.getStatus();
            session.setStatus(TERMINATED);
            session.setTerminatedAt(now);
            session.setTerminationReason("USER_TERMINATED");
            session.setLastEventAt(now);
            session.setLastSequence(session.getLastSequence() + 1L);
            session.setVersion(session.getVersion() + 1);
            sessionMapper.updateByCondition(session, STUDY_SESSION.ID.eq(session.getId()));
            appendSystemEvent(session, fromStatus, "USER_TERMINATE", now);
        });
    }

    @Override
    public Result<SignedRequestView> createPlaybackUrl(PlaybackUrlCommand command) {
        return execute(() -> {
            LoginUser user = LearningGuard.requireStudent();
            if (command == null || command.coursewareSnapshotId() == null) {
                throw new BusinessException(AppErrorCode.PARAM_INVALID);
            }
            StudySessionEntity session = requireOwnedSession(
                    command.sessionId(), user.getEnterpriseId(), user.getUserId(), false);
            if (!session.getClientInstanceId().equals(command.clientInstanceId())) {
                throw new BusinessException(AppErrorCode.LEARNING_SESSION_STALE);
            }
            if (!Arrays.asList(SIGNED_IN, STUDYING, PAUSED).contains(session.getStatus())) {
                throw new BusinessException(AppErrorCode.LEARNING_SESSION_STATE_INVALID,
                        "请先完成学习签到");
            }
            List<StudyCoursewareProgressEntity> coursewares = progressManager.coursewares(
                    user.getEnterpriseId(), user.getUserId(), session.getPlanCourseId());
            StudyCoursewareProgressEntity target = progressManager.requireCourseware(
                    coursewares, command.coursewareSnapshotId());
            ensureUnlocked(coursewares, target);
            return trainingAccessClient.playbackUrl(new LearningPlaybackCommand(
                    session.getTaskId(), session.getPlanId(), session.getPlanCourseId(),
                    target.getCoursewareSnapshotId()));
        });
    }

    private LearningEventResultView processEvent(SubmitEventCommand command) {
        LoginUser user = LearningGuard.requireStudent();
        validateEvent(command);
        StudySessionEntity session = requireOwnedSession(
                command.sessionId(), user.getEnterpriseId(), user.getUserId(), true);
        if (!session.getClientInstanceId().equals(command.clientInstanceId())) {
            throw new BusinessException(AppErrorCode.LEARNING_SESSION_STALE);
        }
        StudyEventLogEntity duplicate = eventLogMapper.selectOneByQuery(QueryWrapper.create()
                .where(STUDY_EVENT_LOG.SESSION_ID.eq(session.getId()))
                .and(STUDY_EVENT_LOG.REQUEST_ID.eq(command.requestId())));
        if (duplicate != null) {
            return fromJson(duplicate.getResponsePayload());
        }
        if (SIGNED_OUT.equals(session.getStatus()) || TERMINATED.equals(session.getStatus())) {
            throw new BusinessException(AppErrorCode.LEARNING_SESSION_STALE);
        }
        if (command.sequence() != session.getLastSequence() + 1L) {
            throw new BusinessException(AppErrorCode.LEARNING_EVENT_SEQUENCE_INVALID,
                    "期望事件序号为" + (session.getLastSequence() + 1L));
        }
        LocalDateTime serverTime = now();
        boolean settleSignOut = false;
        if ("SIGN_OUT".equals(command.eventType())) {
            settleSignOut = canSettleSignOut(session, serverTime);
        } else {
            requireActiveTask(session);
        }
        StudyProgressEntity progress = progressManager.requireProgress(
                user.getEnterpriseId(), user.getUserId(), session.getPlanId(),
                session.getPlanCourseId());
        List<StudyCoursewareProgressEntity> coursewares = progressManager.coursewares(
                user.getEnterpriseId(), user.getUserId(), session.getPlanCourseId());
        String fromStatus = session.getStatus();
        SettleResult settled = applyEvent(
                command, session, progress, coursewares, serverTime, user, settleSignOut);
        session.setLastSequence(command.sequence());
        session.setLastEventAt(serverTime);
        session.setVersion(session.getVersion() + 1);
        sessionMapper.updateByCondition(session, STUDY_SESSION.ID.eq(session.getId()));

        LearningEventResultView response = new LearningEventResultView(
                session.getId(), command.requestId(), command.sequence(), session.getStatus(),
                session.getCurrentCoursewareSnapshotId(), session.getLastConfirmedPositionMs(),
                settled.creditedDurationMillis, progress.getEffectiveDurationMs(),
                progress.getRequiredDurationMs(), settled.coursewareCompleted,
                COMPLETED.equals(progress.getStatus()), serverTime);
        appendEventLog(command, session, fromStatus, response, settled, serverTime, user);
        return response;
    }

    private SettleResult applyEvent(
            SubmitEventCommand command,
            StudySessionEntity session,
            StudyProgressEntity progress,
            List<StudyCoursewareProgressEntity> coursewares,
            LocalDateTime serverTime,
            LoginUser user,
            boolean settleSignOut) {
        switch (command.eventType()) {
            case "SIGN_IN":
                requireState(session, CREATED);
                session.setStatus(SIGNED_IN);
                session.setSignedInAt(serverTime);
                return SettleResult.EMPTY;
            case "PLAY":
                requireState(session, SIGNED_IN, PAUSED);
                StudyCoursewareProgressEntity target = requireEventCourseware(command, coursewares);
                ensureUnlocked(coursewares, target);
                session.setCurrentCoursewareSnapshotId(target.getCoursewareSnapshotId());
                session.setLastConfirmedPositionMs(target.getConfirmedPositionMs());
                session.setStatus(STUDYING);
                if (session.getStartedAt() == null) {
                    session.setStartedAt(serverTime);
                }
                if (NOT_STARTED.equals(target.getStatus())) {
                    target.setStatus(IN_PROGRESS);
                    coursewareProgressMapper.updateByCondition(
                            target, STUDY_COURSEWARE_PROGRESS.ID.eq(target.getId()));
                }
                if (NOT_STARTED.equals(progress.getStatus())) {
                    progress.setStatus(IN_PROGRESS);
                    progressMapper.updateByCondition(progress, STUDY_PROGRESS.ID.eq(progress.getId()));
                }
                outboxService.appendTaskEvent(
                        LearningTaskEvents.STARTED_ROUTING_KEY, "STUDY_STARTED",
                        user.getEnterpriseId(), user.getUserId(), session.getTaskId(),
                        session.getPlanId(), serverTime);
                return SettleResult.EMPTY;
            case "PROGRESS":
                requireState(session, STUDYING);
                return settleProgress(command, session, progress, coursewares, serverTime, false);
            case "PAUSE":
                requireState(session, STUDYING);
                SettleResult pauseResult = settleProgress(
                        command, session, progress, coursewares, serverTime, true);
                if (!SESSION_COMPLETED.equals(session.getStatus())) {
                    session.setStatus(PAUSED);
                    session.setPausedAt(serverTime);
                }
                return pauseResult;
            case "SIGN_OUT":
                SettleResult signOutResult = SettleResult.EMPTY;
                if (STUDYING.equals(session.getStatus()) && settleSignOut) {
                    signOutResult = settleProgress(
                            command, session, progress, coursewares, serverTime, true);
                } else {
                    requireState(session, STUDYING, SIGNED_IN, PAUSED, SESSION_COMPLETED);
                }
                session.setStatus(SIGNED_OUT);
                session.setSignedOutAt(serverTime);
                return signOutResult;
            default:
                throw new BusinessException(AppErrorCode.PARAM_INVALID, "学习事件类型不正确");
        }
    }

    private SettleResult settleProgress(
            SubmitEventCommand command,
            StudySessionEntity session,
            StudyProgressEntity progress,
            List<StudyCoursewareProgressEntity> coursewares,
            LocalDateTime serverTime,
            boolean finalEvent) {
        StudyCoursewareProgressEntity courseware = requireEventCourseware(command, coursewares);
        if (!courseware.getCoursewareSnapshotId().equals(
                session.getCurrentCoursewareSnapshotId())) {
            throw new BusinessException(AppErrorCode.LEARNING_POSITION_INVALID,
                    "上报课件与当前播放课件不一致");
        }
        long toleranceMs = progress.getStudyToleranceSeconds() * 1000L;
        long reportedPosition = command.videoPositionMillis();
        if (reportedPosition > courseware.getDurationMs() + toleranceMs) {
            throw new BusinessException(AppErrorCode.LEARNING_POSITION_INVALID);
        }
        reportedPosition = Math.min(reportedPosition, courseware.getDurationMs());
        long elapsed = session.getLastEventAt() == null ? 0L
                : Math.max(0L, Duration.between(session.getLastEventAt(), serverTime).toMillis());
        long maximumGap = (progress.getProgressReportIntervalSeconds()
                + progress.getStudyToleranceSeconds()) * 1000L;
        long remaining = Math.max(0L,
                progress.getRequiredDurationMs() - progress.getEffectiveDurationMs());
        LearningTimeCalculator.Calculation calculation = timeCalculator.calculate(
                elapsed, session.getLastConfirmedPositionMs(), reportedPosition,
                maximumGap, remaining, toleranceMs, progress.isAllowSeek());
        if (calculation.timedOut()) {
            session.setStatus(PAUSED);
            session.setPausedAt(serverTime);
            return SettleResult.EMPTY;
        }
        long credited = calculation.creditedDurationMillis();
        progress.setEffectiveDurationMs(progress.getEffectiveDurationMs() + credited);
        if (NOT_STARTED.equals(progress.getStatus())) {
            progress.setStatus(IN_PROGRESS);
        }
        courseware.setConfirmedPositionMs(reportedPosition);
        courseware.setMaxConfirmedPositionMs(Math.max(
                courseware.getMaxConfirmedPositionMs(), reportedPosition));
        if (NOT_STARTED.equals(courseware.getStatus())) {
            courseware.setStatus(IN_PROGRESS);
        }
        long completionPosition = timeCalculator.completionPosition(
                courseware.getDurationMs(), toleranceMs);
        boolean coursewareCompleted = courseware.getMaxConfirmedPositionMs() >= completionPosition;
        if (coursewareCompleted && !COMPLETED.equals(courseware.getStatus())) {
            courseware.setStatus(COMPLETED);
            courseware.setCompletedAt(serverTime);
        }
        session.setLastConfirmedPositionMs(reportedPosition);
        boolean allCoursewaresCompleted = coursewares.stream().allMatch(value ->
                value.getId().equals(courseware.getId())
                        ? coursewareCompleted : COMPLETED.equals(value.getStatus()));
        boolean courseCompleted = allCoursewaresCompleted
                && progress.getEffectiveDurationMs() >= progress.getRequiredDurationMs();
        if (courseCompleted && !COMPLETED.equals(progress.getStatus())) {
            progress.setStatus(COMPLETED);
            progress.setCompletedAt(serverTime);
            session.setStatus(SESSION_COMPLETED);
            session.setCompletedAt(serverTime);
        }
        courseware.setVersion(courseware.getVersion() + 1);
        progress.setVersion(progress.getVersion() + 1);
        coursewareProgressMapper.updateByCondition(
                courseware, STUDY_COURSEWARE_PROGRESS.ID.eq(courseware.getId()));
        progressMapper.updateByCondition(progress, STUDY_PROGRESS.ID.eq(progress.getId()));
        if (courseCompleted && progressManager.allTaskCoursesCompleted(
                session.getEnterpriseId(), session.getUserId(), session.getTaskId())) {
            outboxService.appendTaskEvent(
                    LearningTaskEvents.COMPLETED_ROUTING_KEY, "STUDY_COMPLETED",
                    session.getEnterpriseId(), session.getUserId(), session.getTaskId(),
                    session.getPlanId(), serverTime);
        }
        return new SettleResult(credited, coursewareCompleted);
    }

    private StudyCoursewareProgressEntity requireEventCourseware(
            SubmitEventCommand command, List<StudyCoursewareProgressEntity> coursewares) {
        if (command.coursewareSnapshotId() == null) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "课件不能为空");
        }
        return progressManager.requireCourseware(coursewares, command.coursewareSnapshotId());
    }

    private void requireActiveTask(StudySessionEntity session) {
        LearningTaskContextView context = trainingAccessClient.taskContext(session.getPlanId());
        requireRule(context, session.getPlanCourseId());
    }

    /** 失效计划或任务允许关闭会话，但不得再结算任何有效学时。 */
    private boolean canSettleSignOut(StudySessionEntity session, LocalDateTime serverTime) {
        if (session.getPlanEndAt() != null && !serverTime.isBefore(session.getPlanEndAt())) {
            return false;
        }
        try {
            requireActiveTask(session);
            return true;
        } catch (BusinessException exception) {
            if (AppErrorCode.LEARNING_ACCESS_DENIED.getCode().equals(
                    exception.getErrorCode().getCode())) {
                return false;
            }
            throw exception;
        }
    }

    private void ensureUnlocked(
            List<StudyCoursewareProgressEntity> coursewares,
            StudyCoursewareProgressEntity target) {
        boolean locked = coursewares.stream()
                .filter(value -> value.getSortOrder() < target.getSortOrder())
                .anyMatch(value -> !COMPLETED.equals(value.getStatus()));
        if (locked) {
            throw new BusinessException(AppErrorCode.LEARNING_COURSEWARE_LOCKED);
        }
    }

    private void appendEventLog(
            SubmitEventCommand command,
            StudySessionEntity session,
            String fromStatus,
            LearningEventResultView response,
            SettleResult settled,
            LocalDateTime serverTime,
            LoginUser user) {
        StudyEventLogEntity log = new StudyEventLogEntity();
        log.setId(IdGenerator.nextId());
        log.setEnterpriseId(user.getEnterpriseId());
        log.setUserId(user.getUserId());
        log.setSessionId(session.getId());
        log.setRequestId(command.requestId());
        log.setSequenceNo(command.sequence());
        log.setEventType(command.eventType());
        log.setFromStatus(fromStatus);
        log.setToStatus(session.getStatus());
        log.setCoursewareSnapshotId(command.coursewareSnapshotId());
        log.setReportedPositionMs(command.videoPositionMillis());
        log.setConfirmedPositionMs(session.getLastConfirmedPositionMs());
        log.setCreditedDurationMs(settled.creditedDurationMillis);
        log.setResultCode(AppErrorCode.SUCCESS.getCode());
        log.setResponsePayload(toJson(response));
        log.setServerTime(serverTime);
        eventLogMapper.insertSelective(log);
    }

    private void appendSystemEvent(
            StudySessionEntity session, String fromStatus, String eventType, LocalDateTime serverTime) {
        StudyEventLogEntity log = new StudyEventLogEntity();
        log.setId(IdGenerator.nextId());
        log.setEnterpriseId(session.getEnterpriseId());
        log.setUserId(session.getUserId());
        log.setSessionId(session.getId());
        log.setRequestId("SYSTEM-" + UUID.randomUUID());
        log.setSequenceNo(session.getLastSequence());
        log.setEventType(eventType);
        log.setFromStatus(fromStatus);
        log.setToStatus(session.getStatus());
        log.setConfirmedPositionMs(session.getLastConfirmedPositionMs());
        log.setResultCode(AppErrorCode.SUCCESS.getCode());
        log.setResponsePayload("{}");
        log.setServerTime(serverTime);
        eventLogMapper.insertSelective(log);
    }

    private StudySessionEntity activeSession(Long enterpriseId, Long userId, boolean lock) {
        QueryWrapper query = QueryWrapper.create()
                .where(STUDY_SESSION.ENTERPRISE_ID.eq(enterpriseId))
                .and(STUDY_SESSION.USER_ID.eq(userId))
                .and(STUDY_SESSION.STATUS.in(ACTIVE_STATUSES));
        if (lock) {
            query.forUpdate();
        }
        return sessionMapper.selectOneByQuery(query);
    }

    private StudySessionEntity requireOwnedSession(
            Long sessionId, Long enterpriseId, Long userId, boolean lock) {
        if (sessionId == null) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "学习会话不能为空");
        }
        QueryWrapper query = QueryWrapper.create()
                .where(STUDY_SESSION.ID.eq(sessionId))
                .and(STUDY_SESSION.ENTERPRISE_ID.eq(enterpriseId))
                .and(STUDY_SESSION.USER_ID.eq(userId));
        if (lock) {
            query.forUpdate();
        }
        StudySessionEntity session = sessionMapper.selectOneByQuery(query);
        if (session == null) {
            throw new BusinessException(AppErrorCode.LEARNING_SESSION_NOT_FOUND);
        }
        return session;
    }

    private LearningCourseRuleView requireRule(
            LearningTaskContextView context, Long planCourseId) {
        if (planCourseId == null) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "计划课程不能为空");
        }
        return context.courses().stream().filter(value -> value.id().equals(planCourseId))
                .findFirst().orElseThrow(() -> new BusinessException(
                        AppErrorCode.LEARNING_ACCESS_DENIED));
    }

    private LearningSessionView toSessionView(StudySessionEntity session) {
        StudyProgressEntity progress = progressManager.requireProgress(
                session.getEnterpriseId(), session.getUserId(), session.getPlanId(),
                session.getPlanCourseId());
        return new LearningSessionView(
                session.getId(), session.getTaskId(), session.getPlanId(),
                session.getPlanCourseId(), session.getCourseName(), session.getStatus(),
                session.getCurrentCoursewareSnapshotId(), session.getLastSequence(),
                session.getLastConfirmedPositionMs(), progress.getEffectiveDurationMs(),
                progress.getRequiredDurationMs(), session.getLastEventAt(), session.getCreatedAt());
    }

    private void validateEvent(SubmitEventCommand command) {
        if (command == null || command.sessionId() == null || command.sequence() <= 0
                || command.videoPositionMillis() < 0) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID);
        }
        requireIdentifier(command.clientInstanceId(), "客户端实例ID");
        requireIdentifier(command.requestId(), "请求ID");
        String eventType = command.eventType() == null
                ? null : command.eventType().trim().toUpperCase(Locale.ROOT);
        if (!EVENT_TYPES.contains(eventType) || !eventType.equals(command.eventType())) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "学习事件类型不正确");
        }
    }

    private String requireIdentifier(String value, String name) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > 64) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, name + "不正确");
        }
        return normalized;
    }

    private void requireState(StudySessionEntity session, String... statuses) {
        if (Arrays.stream(statuses).noneMatch(value -> value.equals(session.getStatus()))) {
            throw new BusinessException(AppErrorCode.LEARNING_SESSION_STATE_INVALID);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String toJson(LearningEventResultView response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("学习事件响应序列化失败", exception);
        }
    }

    private LearningEventResultView fromJson(String payload) {
        try {
            return objectMapper.readValue(payload, LearningEventResultView.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("学习事件幂等响应解析失败", exception);
        }
    }

    private record SettleResult(long creditedDurationMillis, boolean coursewareCompleted) {
        private static final SettleResult EMPTY = new SettleResult(0L, false);
    }
}
