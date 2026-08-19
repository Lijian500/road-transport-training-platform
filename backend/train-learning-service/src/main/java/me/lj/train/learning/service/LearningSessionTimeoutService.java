package me.lj.train.learning.service;

import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.util.IdGenerator;
import me.lj.train.learning.config.LearningProperties;
import me.lj.train.learning.mapper.StudyEventLogMapper;
import me.lj.train.learning.mapper.StudyProgressMapper;
import me.lj.train.learning.mapper.StudySessionMapper;
import me.lj.train.learning.model.entity.StudyEventLogEntity;
import me.lj.train.learning.model.entity.StudyProgressEntity;
import me.lj.train.learning.model.entity.StudySessionEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static me.lj.train.learning.model.table.StudyProgressTableDef.STUDY_PROGRESS;
import static me.lj.train.learning.model.table.StudySessionTableDef.STUDY_SESSION;

/**
 * 自动暂停超时上报并终止过期或长期闲置会话。
 */
@Component
public class LearningSessionTimeoutService {

    private static final int SCAN_BATCH_SIZE = 500;
    private static final Set<String> ACTIVE = Set.of(
            LearningSessionServiceImpl.CREATED,
            LearningSessionServiceImpl.SIGNED_IN,
            LearningSessionServiceImpl.STUDYING,
            LearningSessionServiceImpl.PAUSED);

    private final StudySessionMapper sessionMapper;
    private final StudyProgressMapper progressMapper;
    private final StudyEventLogMapper eventLogMapper;
    private final LearningProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public LearningSessionTimeoutService(
            StudySessionMapper sessionMapper,
            StudyProgressMapper progressMapper,
            StudyEventLogMapper eventLogMapper,
            LearningProperties properties,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.sessionMapper = sessionMapper;
        this.progressMapper = progressMapper;
        this.eventLogMapper = eventLogMapper;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${learning.timeout-scan-interval-ms:5000}")
    public void maintainSessions() {
        Long lastScannedId = null;
        while (true) {
            QueryWrapper query = QueryWrapper.create()
                    .where(STUDY_SESSION.STATUS.in(ACTIVE));
            if (lastScannedId != null) {
                query.and(STUDY_SESSION.ID.gt(lastScannedId));
            }
            List<StudySessionEntity> sessions = sessionMapper.selectListByQuery(query
                    .orderBy(STUDY_SESSION.ID.asc())
                    .limit(SCAN_BATCH_SIZE));
            if (sessions.isEmpty()) {
                return;
            }
            for (StudySessionEntity candidate : sessions) {
                transactionTemplate.executeWithoutResult(status -> maintainOne(candidate.getId()));
            }
            if (sessions.size() < SCAN_BATCH_SIZE) {
                return;
            }
            lastScannedId = sessions.get(sessions.size() - 1).getId();
        }
    }

    private void maintainOne(Long sessionId) {
        StudySessionEntity session = sessionMapper.selectOneByQuery(QueryWrapper.create()
                .where(STUDY_SESSION.ID.eq(sessionId)).forUpdate());
        if (session == null || !ACTIVE.contains(session.getStatus())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (session.getPlanEndAt() != null && !session.getPlanEndAt().isAfter(now)) {
            transition(session, LearningSessionServiceImpl.TERMINATED,
                    "PLAN_ENDED", "PLAN_ENDED", now);
            return;
        }
        LocalDateTime activityTime = session.getLastEventAt() == null
                ? session.getCreatedAt() : session.getLastEventAt();
        if (activityTime != null && !activityTime.plusHours(
                properties.getSessionIdleHours()).isAfter(now)) {
            transition(session, LearningSessionServiceImpl.TERMINATED,
                    "SESSION_IDLE", "SESSION_IDLE_TIMEOUT", now);
            return;
        }
        if (LearningSessionServiceImpl.STUDYING.equals(session.getStatus())) {
            StudyProgressEntity progress = progressMapper.selectOneByQuery(QueryWrapper.create()
                    .where(STUDY_PROGRESS.ID.isNotNull())
                    .and(STUDY_PROGRESS.ENTERPRISE_ID.eq(session.getEnterpriseId()))
                    .and(STUDY_PROGRESS.USER_ID.eq(session.getUserId()))
                    .and(STUDY_PROGRESS.PLAN_COURSE_ID.eq(session.getPlanCourseId())));
            if (progress == null || session.getLastEventAt() == null) {
                return;
            }
            long timeoutSeconds = progress.getProgressReportIntervalSeconds()
                    + progress.getStudyToleranceSeconds();
            if (!session.getLastEventAt().plusSeconds(timeoutSeconds).isAfter(now)) {
                transition(session, LearningSessionServiceImpl.PAUSED,
                        null, "REPORT_TIMEOUT", now);
            }
        }
    }

    private void transition(
            StudySessionEntity session,
            String targetStatus,
            String terminationReason,
            String eventType,
            LocalDateTime now) {
        String fromStatus = session.getStatus();
        session.setStatus(targetStatus);
        session.setLastEventAt(now);
        session.setLastSequence(session.getLastSequence() + 1L);
        session.setVersion(session.getVersion() + 1);
        if (LearningSessionServiceImpl.PAUSED.equals(targetStatus)) {
            session.setPausedAt(now);
        }
        if (LearningSessionServiceImpl.TERMINATED.equals(targetStatus)) {
            session.setTerminatedAt(now);
            session.setTerminationReason(terminationReason);
        }
        sessionMapper.updateByCondition(session, STUDY_SESSION.ID.eq(session.getId()));

        StudyEventLogEntity log = new StudyEventLogEntity();
        log.setId(IdGenerator.nextId());
        log.setEnterpriseId(session.getEnterpriseId());
        log.setUserId(session.getUserId());
        log.setSessionId(session.getId());
        log.setRequestId("SYSTEM-" + UUID.randomUUID());
        log.setSequenceNo(session.getLastSequence());
        log.setEventType(eventType);
        log.setFromStatus(fromStatus);
        log.setToStatus(targetStatus);
        log.setCoursewareSnapshotId(session.getCurrentCoursewareSnapshotId());
        log.setConfirmedPositionMs(session.getLastConfirmedPositionMs());
        log.setResultCode(AppErrorCode.SUCCESS.getCode());
        log.setResponsePayload("{}");
        log.setServerTime(now);
        eventLogMapper.insertSelective(log);
    }
}
