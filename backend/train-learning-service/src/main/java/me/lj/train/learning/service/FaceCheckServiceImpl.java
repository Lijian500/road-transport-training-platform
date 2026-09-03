package me.lj.train.learning.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.api.learning.FaceCheckEvents;
import me.lj.train.api.learning.FaceCheckModels.FaceCheckRealtimeEvent;
import me.lj.train.api.learning.FaceCheckModels.FaceCheckView;
import me.lj.train.api.learning.FaceCheckModels.FaceReferenceValidationView;
import me.lj.train.api.learning.FaceCheckModels.SubmitFaceCheckCommand;
import me.lj.train.api.learning.FaceCheckService;
import me.lj.train.api.training.LearningAccessModels.LearningTaskContextView;
import me.lj.train.api.training.PrivateImageModels.PrivateImageContentView;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.core.util.IdGenerator;
import me.lj.train.common.security.model.LoginUser;
import me.lj.train.face.adapter.FaceVerifier;
import me.lj.train.face.adapter.model.FaceComparisonResult;
import me.lj.train.face.adapter.model.FaceDetectionResult;
import me.lj.train.learning.mapper.FaceCheckLogMapper;
import me.lj.train.learning.mapper.FaceCheckTaskMapper;
import me.lj.train.learning.mapper.StudyProgressMapper;
import me.lj.train.learning.mapper.StudySessionMapper;
import me.lj.train.learning.model.entity.FaceCheckLogEntity;
import me.lj.train.learning.model.entity.FaceCheckTaskEntity;
import me.lj.train.learning.model.entity.StudyProgressEntity;
import me.lj.train.learning.model.entity.StudySessionEntity;
import me.lj.train.learning.support.FaceReferenceImageClient;
import me.lj.train.learning.support.LearningGuard;
import me.lj.train.learning.support.LearningServiceSupport;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static me.lj.train.learning.model.table.FaceCheckLogTableDef.FACE_CHECK_LOG;
import static me.lj.train.learning.model.table.FaceCheckTaskTableDef.FACE_CHECK_TASK;
import static me.lj.train.learning.model.table.StudyProgressTableDef.STUDY_PROGRESS;
import static me.lj.train.learning.model.table.StudySessionTableDef.STUDY_SESSION;

/**
 * 人脸抽验阈值触发、核验提交和终态流转实现。
 */
@DubboService(timeout = 30000, retries = 0)
public class FaceCheckServiceImpl extends LearningServiceSupport implements FaceCheckService {

    static final String PENDING = "PENDING";
    static final String PASSED = "PASSED";
    static final String FAILED = "FAILED";
    static final String TIMED_OUT = "TIMED_OUT";
    static final String CANCELLED = "CANCELLED";

    private static final int DEFAULT_MIN_INTERVAL_SECONDS = 300;
    private static final int DEFAULT_MAX_INTERVAL_SECONDS = 600;
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;

    private final FaceCheckTaskMapper taskMapper;
    private final FaceCheckLogMapper logMapper;
    private final StudySessionMapper sessionMapper;
    private final StudyProgressMapper progressMapper;
    private final LearningOutboxService outboxService;
    private final FaceReferenceImageClient referenceImageClient;
    private final ObjectProvider<FaceVerifier> verifierProvider;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public FaceCheckServiceImpl(
            PlatformTransactionManager transactionManager,
            FaceCheckTaskMapper taskMapper,
            FaceCheckLogMapper logMapper,
            StudySessionMapper sessionMapper,
            StudyProgressMapper progressMapper,
            LearningOutboxService outboxService,
            FaceReferenceImageClient referenceImageClient,
            ObjectProvider<FaceVerifier> verifierProvider,
            ObjectMapper objectMapper,
            Clock clock) {
        super(transactionManager);
        this.taskMapper = taskMapper;
        this.logMapper = logMapper;
        this.sessionMapper = sessionMapper;
        this.progressMapper = progressMapper;
        this.outboxService = outboxService;
        this.referenceImageClient = referenceImageClient;
        this.verifierProvider = verifierProvider;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public Result<FaceReferenceValidationView> validateReference(byte[] imageBytes) {
        if (!validImage(imageBytes)) {
            return Result.failed(AppErrorCode.PARAM_INVALID, "登记照内容不正确");
        }
        FaceVerifier verifier = verifierProvider.getIfAvailable();
        if (verifier == null) {
            return Result.failed(AppErrorCode.FACE_CHECK_UNAVAILABLE);
        }
        Result<FaceDetectionResult> detection = verifier.detect(imageBytes);
        if (detection == null) {
            return Result.failed(AppErrorCode.FACE_CHECK_UNAVAILABLE);
        }
        if (!detection.isSuccess()) {
            return Result.failed(detection.getCode(), detection.getMessage());
        }
        FaceDetectionResult value = detection.getData();
        boolean valid = value != null && value.faceCount() == 1;
        String message = valid ? "登记照校验通过"
                : value == null || value.faceCount() == 0
                ? "登记照中未检测到人脸" : "登记照中只能包含一张人脸";
        return Result.ok(new FaceReferenceValidationView(
                valid, message, value == null ? 0 : value.faceCount()));
    }

    @Override
    public Result<FaceCheckView> getCurrentFaceCheck(Long sessionId) {
        return execute(() -> {
            LoginUser user = LearningGuard.requireStudent();
            StudySessionEntity session = requireOwnedSession(
                    sessionId, user.getEnterpriseId(), user.getUserId(), false);
            return currentPending(session.getId());
        });
    }

    @Override
    public Result<FaceCheckView> submitFaceCheck(SubmitFaceCheckCommand command) {
        return executeTransactional(() -> submit(command));
    }

    /** 打开学习会话时冻结抽验规则并生成首个累计有效学时阈值。 */
    void configureSession(
            StudySessionEntity session,
            LearningTaskContextView context,
            long effectiveDurationMs,
            long requiredDurationMs) {
        boolean enabled = context.faceCheckEnabled();
        int minSeconds = positiveOrDefault(
                context.faceCheckMinIntervalSeconds(), DEFAULT_MIN_INTERVAL_SECONDS);
        int maxSeconds = Math.max(minSeconds, positiveOrDefault(
                context.faceCheckMaxIntervalSeconds(), DEFAULT_MAX_INTERVAL_SECONDS));
        int timeoutSeconds = positiveOrDefault(
                context.faceCheckTimeoutSeconds(), DEFAULT_TIMEOUT_SECONDS);
        int maxAttempts = positiveOrDefault(
                context.faceCheckMaxAttempts(), DEFAULT_MAX_ATTEMPTS);
        session.setFaceCheckEnabled(enabled);
        session.setFaceCheckMinIntervalSeconds(minSeconds);
        session.setFaceCheckMaxIntervalSeconds(maxSeconds);
        session.setFaceCheckTimeoutSeconds(timeoutSeconds);
        session.setFaceCheckMaxAttempts(maxAttempts);
        session.setNextFaceCheckEffectiveDurationMs(enabled
                ? firstThreshold(effectiveDurationMs, requiredDurationMs, minSeconds, maxSeconds)
                : null);
    }

    /** 有效学时达到阈值后创建任务，并立即冻结会话学时累计。 */
    boolean triggerIfDue(
            StudySessionEntity session,
            StudyProgressEntity progress,
            LocalDateTime now) {
        Long threshold = session.getNextFaceCheckEffectiveDurationMs();
        if (!session.isFaceCheckEnabled() || threshold == null
                || progress.getEffectiveDurationMs() < threshold
                || progress.getEffectiveDurationMs() >= progress.getRequiredDurationMs()) {
            return false;
        }
        FaceCheckTaskEntity existing = currentPendingEntity(session.getId(), false);
        if (existing != null) {
            session.setStatus(LearningSessionServiceImpl.FACE_PENDING);
            session.setNextFaceCheckEffectiveDurationMs(null);
            return true;
        }
        FaceCheckTaskEntity task = new FaceCheckTaskEntity();
        task.setId(IdGenerator.nextId());
        task.setEnterpriseId(session.getEnterpriseId());
        task.setUserId(session.getUserId());
        task.setSessionId(session.getId());
        task.setTrainingTaskId(session.getTaskId());
        task.setPlanId(session.getPlanId());
        task.setPlanCourseId(session.getPlanCourseId());
        task.setStatus(PENDING);
        task.setTriggeredAt(now);
        task.setDeadlineAt(now.plusSeconds(session.getFaceCheckTimeoutSeconds()));
        task.setMaxAttempts(session.getFaceCheckMaxAttempts());
        task.setCreatedAt(now);
        try {
            taskMapper.insertSelective(task);
        } catch (DuplicateKeyException exception) {
            task = currentPendingEntity(session.getId(), false);
            if (task == null) {
                throw exception;
            }
        }
        session.setStatus(LearningSessionServiceImpl.FACE_PENDING);
        session.setPausedAt(now);
        session.setNextFaceCheckEffectiveDurationMs(null);
        appendRealtime(task, FaceCheckEvents.REQUIRED_EVENT,
                FaceCheckEvents.REQUIRED_ROUTING_KEY, now);
        return true;
    }

    /** 返回会话当前待处理抽验，供STATE_SYNC恢复弹窗。 */
    FaceCheckView currentPending(Long sessionId) {
        FaceCheckTaskEntity task = currentPendingEntity(sessionId, false);
        return task == null ? null : toView(task);
    }

    /** 用户主动终止会话时同步取消仍待处理的抽验任务。 */
    void cancelPending(StudySessionEntity session, LocalDateTime now) {
        FaceCheckTaskEntity task = currentPendingEntity(session.getId(), true);
        if (task == null) {
            return;
        }
        task.setStatus(CANCELLED);
        task.setResult(CANCELLED);
        task.setFailureReason("SESSION_TERMINATED");
        task.setCompletedAt(now);
        task.setVersion(task.getVersion() + 1);
        taskMapper.updateByCondition(task, FACE_CHECK_TASK.ID.eq(task.getId()));
        appendRealtime(task, FaceCheckEvents.RESULT_EVENT,
                FaceCheckEvents.RESULT_ROUTING_KEY, now);
    }

    /** 定时扫描命中的单个任务超时处理，调用方无需持有事务。 */
    void timeoutTask(Long taskId) {
        runInTransaction(() -> {
            FaceCheckTaskEntity task = taskMapper.selectOneByQuery(QueryWrapper.create()
                    .where(FACE_CHECK_TASK.ID.eq(taskId)).forUpdate());
            if (task == null || !PENDING.equals(task.getStatus())) {
                return;
            }
            LocalDateTime now = now();
            if (task.getDeadlineAt().isAfter(now)) {
                return;
            }
            StudySessionEntity session = sessionMapper.selectOneByQuery(QueryWrapper.create()
                    .where(STUDY_SESSION.ID.eq(task.getSessionId())).forUpdate());
            expire(task, session, now);
        });
    }

    private FaceCheckView submit(SubmitFaceCheckCommand command) {
        LoginUser user = LearningGuard.requireStudent();
        validateSubmission(command);
        FaceCheckTaskEntity task = requireOwnedTask(
                command.taskId(), user.getEnterpriseId(), user.getUserId(), true);
        FaceCheckLogEntity duplicate = logMapper.selectOneByQuery(QueryWrapper.create()
                .where(FACE_CHECK_LOG.TASK_ID.eq(task.getId()))
                .and(FACE_CHECK_LOG.REQUEST_ID.eq(command.requestId())));
        if (duplicate != null) {
            return fromJson(duplicate.getResponsePayload());
        }
        if (!PENDING.equals(task.getStatus())) {
            return toView(task);
        }
        StudySessionEntity session = requireOwnedSession(
                task.getSessionId(), user.getEnterpriseId(), user.getUserId(), true);
        LocalDateTime now = now();
        if (!task.getDeadlineAt().isAfter(now)) {
            expire(task, session, now);
            return toView(task);
        }
        if (!LearningSessionServiceImpl.FACE_PENDING.equals(session.getStatus())) {
            throw new BusinessException(AppErrorCode.FACE_CHECK_STATE_INVALID);
        }
        FaceVerifier verifier = requireVerifier();
        PrivateImageContentView reference = referenceImageClient.read(user.getUserId());
        Result<FaceComparisonResult> compared = verifier.compare(
                reference.content(), command.imageBytes());
        if (compared == null) {
            throw new BusinessException(AppErrorCode.FACE_CHECK_UNAVAILABLE);
        }
        if (!compared.isSuccess()) {
            if ("F4001".equals(compared.getCode()) || "F4002".equals(compared.getCode())) {
                return recordInvalidImage(command, task, session, compared.getMessage(), now);
            }
            throw new BusinessException(AppErrorCode.FACE_CHECK_UNAVAILABLE,
                    compared.getMessage());
        }
        FaceComparisonResult comparison = compared.getData();
        if (comparison == null) {
            throw new BusinessException(AppErrorCode.FACE_CHECK_UNAVAILABLE);
        }
        return recordComparison(command, task, session, comparison, now);
    }

    private FaceCheckView recordComparison(
            SubmitFaceCheckCommand command,
            FaceCheckTaskEntity task,
            StudySessionEntity session,
            FaceComparisonResult comparison,
            LocalDateTime now) {
        task.setAttemptCount(task.getAttemptCount() + 1);
        task.setResult(comparison.status().name());
        task.setSimilarity(comparison.similarity());
        task.setFailureReason(comparison.samePerson() ? null : comparison.status().name());
        if (comparison.samePerson()) {
            task.setStatus(PASSED);
            task.setCompletedAt(now);
            session.setStatus(LearningSessionServiceImpl.PAUSED);
            session.setPausedAt(now);
            scheduleNextThreshold(session);
        } else if (task.getAttemptCount() >= task.getMaxAttempts()) {
            failTask(task, session, now, comparison.status().name());
        }
        task.setVersion(task.getVersion() + 1);
        persistResult(task, session);
        FaceCheckView view = toView(task);
        appendLog(command, task, comparison.elapsedMillis(), view);
        appendRealtime(task, FaceCheckEvents.RESULT_EVENT,
                FaceCheckEvents.RESULT_ROUTING_KEY, now);
        return view;
    }

    private FaceCheckView recordInvalidImage(
            SubmitFaceCheckCommand command,
            FaceCheckTaskEntity task,
            StudySessionEntity session,
            String message,
            LocalDateTime now) {
        task.setAttemptCount(task.getAttemptCount() + 1);
        task.setResult("INVALID_IMAGE");
        task.setFailureReason("INVALID_IMAGE");
        if (task.getAttemptCount() >= task.getMaxAttempts()) {
            failTask(task, session, now, "INVALID_IMAGE");
        }
        task.setVersion(task.getVersion() + 1);
        persistResult(task, session);
        FaceCheckView view = toView(task);
        appendLog(command, task, 0L, view);
        appendRealtime(task, FaceCheckEvents.RESULT_EVENT,
                FaceCheckEvents.RESULT_ROUTING_KEY, now);
        return view;
    }

    private void failTask(
            FaceCheckTaskEntity task,
            StudySessionEntity session,
            LocalDateTime now,
            String reason) {
        task.setStatus(FAILED);
        task.setFailureReason(reason);
        task.setCompletedAt(now);
        session.setStatus(LearningSessionServiceImpl.TERMINATED);
        session.setTerminatedAt(now);
        session.setTerminationReason("FACE_CHECK_FAILED");
        session.setLastEventAt(now);
        session.setVersion(session.getVersion() + 1);
    }

    private void expire(
            FaceCheckTaskEntity task,
            StudySessionEntity session,
            LocalDateTime now) {
        task.setStatus(TIMED_OUT);
        task.setResult(TIMED_OUT);
        task.setFailureReason("DEADLINE_EXCEEDED");
        task.setCompletedAt(now);
        task.setVersion(task.getVersion() + 1);
        taskMapper.updateByCondition(task, FACE_CHECK_TASK.ID.eq(task.getId()));
        if (session != null && LearningSessionServiceImpl.FACE_PENDING.equals(session.getStatus())) {
            session.setStatus(LearningSessionServiceImpl.TERMINATED);
            session.setTerminatedAt(now);
            session.setTerminationReason("FACE_CHECK_TIMEOUT");
            session.setLastEventAt(now);
            session.setVersion(session.getVersion() + 1);
            sessionMapper.updateByCondition(session, STUDY_SESSION.ID.eq(session.getId()));
        }
        appendRealtime(task, FaceCheckEvents.RESULT_EVENT,
                FaceCheckEvents.RESULT_ROUTING_KEY, now);
    }

    private void scheduleNextThreshold(StudySessionEntity session) {
        StudyProgressEntity progress = progressMapper.selectOneByQuery(QueryWrapper.create()
                .where(STUDY_PROGRESS.ENTERPRISE_ID.eq(session.getEnterpriseId()))
                .and(STUDY_PROGRESS.USER_ID.eq(session.getUserId()))
                .and(STUDY_PROGRESS.PLAN_ID.eq(session.getPlanId()))
                .and(STUDY_PROGRESS.PLAN_COURSE_ID.eq(session.getPlanCourseId())));
        if (progress == null) {
            session.setNextFaceCheckEffectiveDurationMs(null);
            return;
        }
        long remaining = progress.getRequiredDurationMs() - progress.getEffectiveDurationMs();
        long minInterval = session.getFaceCheckMinIntervalSeconds() * 1000L;
        if (remaining <= minInterval) {
            session.setNextFaceCheckEffectiveDurationMs(null);
            return;
        }
        long maxInterval = Math.min(
                session.getFaceCheckMaxIntervalSeconds() * 1000L, remaining - 1L);
        long interval = randomBetween(minInterval, Math.max(minInterval, maxInterval));
        session.setNextFaceCheckEffectiveDurationMs(
                progress.getEffectiveDurationMs() + interval);
    }

    private void persistResult(FaceCheckTaskEntity task, StudySessionEntity session) {
        taskMapper.updateByCondition(task, FACE_CHECK_TASK.ID.eq(task.getId()));
        sessionMapper.updateByCondition(session, STUDY_SESSION.ID.eq(session.getId()));
    }

    private void appendLog(
            SubmitFaceCheckCommand command,
            FaceCheckTaskEntity task,
            long elapsedMillis,
            FaceCheckView view) {
        FaceCheckLogEntity log = new FaceCheckLogEntity();
        log.setId(IdGenerator.nextId());
        log.setEnterpriseId(task.getEnterpriseId());
        log.setUserId(task.getUserId());
        log.setTaskId(task.getId());
        log.setSessionId(task.getSessionId());
        log.setRequestId(command.requestId());
        log.setAttemptNo(task.getAttemptCount());
        log.setResult(task.getResult());
        log.setFailureReason(task.getFailureReason());
        log.setSimilarity(task.getSimilarity());
        log.setElapsedMs(elapsedMillis);
        log.setImageSha256(sha256(command.imageBytes()));
        log.setResponsePayload(toJson(view));
        log.setCreatedAt(now());
        logMapper.insertSelective(log);
    }

    private void appendRealtime(
            FaceCheckTaskEntity task,
            String eventType,
            String routingKey,
            LocalDateTime occurredAt) {
        String eventId = UUID.randomUUID().toString();
        FaceCheckRealtimeEvent event = new FaceCheckRealtimeEvent(
                eventId, eventType, occurredAt, task.getEnterpriseId(), task.getUserId(),
                task.getSessionId(), toView(task));
        String stage = FaceCheckEvents.REQUIRED_EVENT.equals(eventType)
                ? "REQUIRED" : "RESULT:" + task.getStatus() + ":" + task.getAttemptCount();
        outboxService.appendFaceCheckEvent(
                task.getId(), stage, routingKey, event, occurredAt);
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

    private FaceCheckTaskEntity requireOwnedTask(
            Long taskId, Long enterpriseId, Long userId, boolean lock) {
        if (taskId == null) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "抽验任务不能为空");
        }
        QueryWrapper query = QueryWrapper.create()
                .where(FACE_CHECK_TASK.ID.eq(taskId))
                .and(FACE_CHECK_TASK.ENTERPRISE_ID.eq(enterpriseId))
                .and(FACE_CHECK_TASK.USER_ID.eq(userId));
        if (lock) {
            query.forUpdate();
        }
        FaceCheckTaskEntity task = taskMapper.selectOneByQuery(query);
        if (task == null) {
            throw new BusinessException(AppErrorCode.FACE_CHECK_NOT_FOUND);
        }
        return task;
    }

    private FaceCheckTaskEntity currentPendingEntity(Long sessionId, boolean lock) {
        QueryWrapper query = QueryWrapper.create()
                .where(FACE_CHECK_TASK.SESSION_ID.eq(sessionId))
                .and(FACE_CHECK_TASK.STATUS.eq(PENDING));
        if (lock) {
            query.forUpdate();
        }
        return taskMapper.selectOneByQuery(query);
    }

    private FaceCheckView toView(FaceCheckTaskEntity task) {
        return new FaceCheckView(
                task.getId(), task.getSessionId(), task.getStatus(), task.getTriggeredAt(),
                task.getDeadlineAt(), task.getAttemptCount(), task.getMaxAttempts(),
                Math.max(0, task.getMaxAttempts() - task.getAttemptCount()), task.getResult(),
                task.getFailureReason(), task.getSimilarity(), task.getCompletedAt());
    }

    private long firstThreshold(
            long effectiveDurationMs,
            long requiredDurationMs,
            int minSeconds,
            int maxSeconds) {
        long remaining = Math.max(0L, requiredDurationMs - effectiveDurationMs);
        if (remaining == 0L) {
            return -1L;
        }
        long maxInterval = maxSeconds * 1000L;
        if (remaining <= maxInterval) {
            return effectiveDurationMs + Math.max(1L, remaining / 2L);
        }
        return effectiveDurationMs + randomBetween(minSeconds * 1000L, maxInterval);
    }

    private long randomBetween(long minimum, long maximum) {
        if (maximum <= minimum) {
            return minimum;
        }
        return ThreadLocalRandom.current().nextLong(minimum, maximum + 1L);
    }

    private int positiveOrDefault(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    private FaceVerifier requireVerifier() {
        FaceVerifier verifier = verifierProvider.getIfAvailable();
        if (verifier == null) {
            throw new BusinessException(AppErrorCode.FACE_CHECK_UNAVAILABLE);
        }
        return verifier;
    }

    private void validateSubmission(SubmitFaceCheckCommand command) {
        if (command == null || command.taskId() == null
                || command.requestId() == null || command.requestId().isBlank()
                || command.requestId().length() > 64 || !validImage(command.imageBytes())) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID);
        }
    }

    private boolean validImage(byte[] imageBytes) {
        return imageBytes != null && imageBytes.length > 0
                && imageBytes.length <= MAX_IMAGE_BYTES;
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持SHA-256", exception);
        }
    }

    private String toJson(FaceCheckView view) {
        try {
            return objectMapper.writeValueAsString(view);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("人脸抽验响应序列化失败", exception);
        }
    }

    private FaceCheckView fromJson(String payload) {
        try {
            return objectMapper.readValue(payload, FaceCheckView.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("人脸抽验幂等响应解析失败", exception);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
