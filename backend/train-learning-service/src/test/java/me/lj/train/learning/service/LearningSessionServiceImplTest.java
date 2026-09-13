package me.lj.train.learning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.api.learning.LearningModels.BindSessionCommand;
import me.lj.train.api.learning.LearningModels.LearningEventResultView;
import me.lj.train.api.learning.LearningModels.OpenSessionCommand;
import me.lj.train.api.learning.LearningModels.SubmitEventCommand;
import me.lj.train.api.training.LearningAccessModels.LearningCourseRuleView;
import me.lj.train.api.training.LearningAccessModels.LearningTaskContextView;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;
import me.lj.train.learning.mapper.StudyCoursewareProgressMapper;
import me.lj.train.learning.mapper.StudyEventLogMapper;
import me.lj.train.learning.mapper.StudyProgressMapper;
import me.lj.train.learning.mapper.StudySessionMapper;
import me.lj.train.learning.model.entity.StudyEventLogEntity;
import me.lj.train.learning.model.entity.StudyProgressEntity;
import me.lj.train.learning.model.entity.StudySessionEntity;
import me.lj.train.learning.model.entity.StudyCoursewareProgressEntity;
import me.lj.train.learning.support.TrainingAccessClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 学习会话冲突和幂等入口测试。 */
@ExtendWith(MockitoExtension.class)
class LearningSessionServiceImplTest {

    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;
    @Mock private StudySessionMapper sessionMapper;
    @Mock private StudyProgressMapper progressMapper;
    @Mock private StudyCoursewareProgressMapper coursewareProgressMapper;
    @Mock private StudyEventLogMapper eventLogMapper;
    @Mock private LearningProgressManager progressManager;
    @Mock private LearningOutboxService outboxService;
    @Mock private TrainingAccessClient trainingAccessClient;
    @Mock private FaceCheckServiceImpl faceCheckService;

    private ObjectMapper objectMapper;
    private LearningSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new LearningSessionServiceImpl(
                transactionManager, sessionMapper, progressMapper, coursewareProgressMapper,
                eventLogMapper, progressManager, outboxService, trainingAccessClient,
                objectMapper, Clock.fixed(Instant.parse("2026-08-19T08:00:00Z"),
                ZoneId.of("Asia/Shanghai")), new LearningTimeCalculator(), faceCheckService);
        UserContext.set(student());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldRestoreFailedFaceCheckFromTerminatedSession() {
        StudySessionEntity ended = session(101L, "browser-one", "TERMINATED");
        var faceCheck = new me.lj.train.api.learning.FaceCheckModels.FaceCheckView(
                400L, ended.getId(), "FAILED", LocalDateTime.now().minusMinutes(2),
                LocalDateTime.now().minusMinutes(1), 3, 3, 0, "NOT_MATCH",
                "NOT_MATCH", null, LocalDateTime.now().minusMinutes(1));
        when(sessionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(ended);
        when(progressManager.requireProgress(20L, 10L, 100L, 101L)).thenReturn(progress());
        when(faceCheckService.currentTerminal(ended.getId())).thenReturn(faceCheck);

        var result = service.getSession(ended.getId());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().currentFaceCheck().status()).isEqualTo("FAILED");
        verify(faceCheckService, never()).currentPending(any());
    }

    @Test
    void shouldRejectOpeningAnotherCourseWhileSessionIsActive() {
        prepareTransaction();
        when(trainingAccessClient.taskContext(100L)).thenReturn(context());
        StudySessionEntity active = session(101L, "browser-one", "PAUSED");
        when(sessionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(active);

        Result<?> result = service.openSession(new OpenSessionCommand(
                100L, 102L, "browser-one"));

        assertThat(result.getCode()).isEqualTo(AppErrorCode.LEARNING_SESSION_CONFLICT.getCode());
        verify(sessionMapper, never()).insertSelective(any(StudySessionEntity.class));
    }

    @Test
    void shouldResumeSameCourseAndBrowserSession() {
        prepareTransaction();
        when(trainingAccessClient.taskContext(100L)).thenReturn(context());
        StudySessionEntity active = session(101L, "browser-one", "PAUSED");
        StudyProgressEntity progress = progress();
        when(sessionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(active);
        when(progressManager.requireProgress(20L, 10L, 100L, 101L)).thenReturn(progress);

        Result<?> result = service.openSession(new OpenSessionCommand(
                100L, 101L, "browser-one"));

        assertThat(result.isSuccess()).isTrue();
        verify(sessionMapper, never()).insertSelective(any(StudySessionEntity.class));
    }

    @Test
    void shouldCreateSessionWithCourseSnapshotOrder() {
        prepareTransaction();
        when(trainingAccessClient.taskContext(100L)).thenReturn(context());
        when(sessionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
        when(progressManager.requireProgress(20L, 10L, 100L, 101L)).thenReturn(progress());

        Result<?> result = service.openSession(new OpenSessionCommand(
                100L, 101L, "browser-one"));

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<StudySessionEntity> captor = ArgumentCaptor.forClass(
                StudySessionEntity.class);
        verify(sessionMapper).insertSelective(captor.capture());
        assertThat(captor.getValue().getSortOrder()).isEqualTo(1);
        assertThat(captor.getValue().getCreatedAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 19, 16, 0));
    }

    @Test
    void shouldBindCompletedSessionForFinalSignOut() {
        StudySessionEntity completed = session(101L, "browser-one", "COMPLETED");
        when(sessionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(completed);
        when(progressManager.requireProgress(20L, 10L, 100L, 101L)).thenReturn(progress());

        Result<?> result = service.bindSession(new BindSessionCommand(900L, "browser-one"));

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldRejectBindingAnotherBrowserInstance() {
        when(sessionMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(session(101L, "browser-one", "PAUSED"));

        Result<?> result = service.bindSession(new BindSessionCommand(900L, "browser-two"));

        assertThat(result.getCode()).isEqualTo(AppErrorCode.LEARNING_SESSION_STALE.getCode());
        verifyNoInteractions(progressManager);
    }

    @Test
    void shouldRejectBindingFinalSession() {
        when(sessionMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(session(101L, "browser-one", "SIGNED_OUT"));

        Result<?> result = service.bindSession(new BindSessionCommand(900L, "browser-one"));

        assertThat(result.getCode()).isEqualTo(AppErrorCode.LEARNING_SESSION_STALE.getCode());
        verifyNoInteractions(progressManager);
    }

    @Test
    void shouldRejectBindingSessionOwnedByAnotherUser() {
        when(sessionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);

        Result<?> result = service.bindSession(new BindSessionCommand(900L, "browser-one"));

        assertThat(result.getCode()).isEqualTo(AppErrorCode.LEARNING_SESSION_NOT_FOUND.getCode());
        verifyNoInteractions(progressManager);
    }

    @Test
    void shouldReturnStoredResponseForDuplicateRequestWithoutUpdatingProgress() throws Exception {
        prepareTransaction();
        StudySessionEntity active = session(101L, "browser-one", "STUDYING");
        active.setLastSequence(5L);
        LearningEventResultView response = new LearningEventResultView(
                active.getId(), "request-one", 5L, "STUDYING", 301L,
                10_000L, 5_000L, 10_000L, 60_000L, false, false,
                LocalDateTime.of(2026, 8, 19, 16, 0));
        StudyEventLogEntity duplicate = new StudyEventLogEntity();
        duplicate.setResponsePayload(objectMapper.writeValueAsString(response));
        when(sessionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(active);
        when(eventLogMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(duplicate);

        Result<LearningEventResultView> result = service.submitEvent(new SubmitEventCommand(
                active.getId(), "browser-one", "request-one", 5L,
                "PROGRESS", 301L, 10_000L));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().creditedDurationMillis()).isEqualTo(5_000L);
        verify(sessionMapper, never()).updateByCondition(any(), any());
        verifyNoInteractions(progressMapper, coursewareProgressMapper, outboxService);
    }

    @Test
    void shouldRejectSkippedSequenceBeforeCreditingTime() {
        prepareTransaction();
        StudySessionEntity active = session(101L, "browser-one", "STUDYING");
        active.setLastSequence(5L);
        when(sessionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(active);
        when(eventLogMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);

        Result<LearningEventResultView> result = service.submitEvent(new SubmitEventCommand(
                active.getId(), "browser-one", "request-two", 7L,
                "PROGRESS", 301L, 10_000L));

        assertThat(result.getCode())
                .isEqualTo(AppErrorCode.LEARNING_EVENT_SEQUENCE_INVALID.getCode());
        verifyNoInteractions(progressMapper, coursewareProgressMapper, outboxService);
    }

    @Test
    void shouldSignOutExpiredStudyingSessionWithoutCreditingTime() {
        prepareTransaction();
        StudySessionEntity active = session(101L, "browser-one", "STUDYING");
        active.setLastSequence(5L);
        active.setCurrentCoursewareSnapshotId(301L);
        active.setPlanEndAt(LocalDateTime.of(2026, 8, 19, 15, 59, 59));
        when(sessionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(active);
        when(progressManager.requireProgress(20L, 10L, 100L, 101L)).thenReturn(progress());
        when(progressManager.coursewares(20L, 10L, 101L))
                .thenReturn(Collections.emptyList());

        Result<LearningEventResultView> result = service.submitEvent(new SubmitEventCommand(
                active.getId(), "browser-one", "sign-out-expired", 6L,
                "SIGN_OUT", 301L, 30_000L));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().status()).isEqualTo("SIGNED_OUT");
        assertThat(result.getData().creditedDurationMillis()).isZero();
        verifyNoInteractions(trainingAccessClient, progressMapper, coursewareProgressMapper,
                outboxService);
    }

    @Test
    void shouldSignOutInvalidTaskWithoutCreditingTime() {
        prepareTransaction();
        StudySessionEntity active = session(101L, "browser-one", "STUDYING");
        active.setLastSequence(5L);
        active.setCurrentCoursewareSnapshotId(301L);
        active.setPlanEndAt(LocalDateTime.of(2026, 8, 19, 17, 0));
        when(sessionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(active);
        when(trainingAccessClient.taskContext(100L)).thenThrow(
                new BusinessException(AppErrorCode.LEARNING_ACCESS_DENIED));
        when(progressManager.requireProgress(20L, 10L, 100L, 101L)).thenReturn(progress());
        when(progressManager.coursewares(20L, 10L, 101L))
                .thenReturn(Collections.emptyList());

        Result<LearningEventResultView> result = service.submitEvent(new SubmitEventCommand(
                active.getId(), "browser-one", "sign-out-invalid", 6L,
                "SIGN_OUT", 301L, 30_000L));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().status()).isEqualTo("SIGNED_OUT");
        assertThat(result.getData().creditedDurationMillis()).isZero();
        verifyNoInteractions(progressMapper, coursewareProgressMapper, outboxService);
    }

    /** 复现播完仍缺5秒时的补学：重置位置不赠送学时，随后按实际经过时间补足。 */
    @Test
    void shouldSupplementCompletedCoursewareWithoutLosingHistoryOrCreditingPlay() {
        StudySessionEntity active = prepareReplay("COMPLETED");
        StudyProgressEntity progress = progressManager.requireProgress(20L, 10L, 100L, 101L);
        StudyCoursewareProgressEntity target = progressManager.coursewares(20L, 10L, 101L).get(0);

        Result<LearningEventResultView> play = service.submitEvent(new SubmitEventCommand(
                900L, "browser-one", "replay", 1L, "PLAY", 301L, 0L));

        assertThat(play.isSuccess()).isTrue();
        assertThat(play.getData().confirmedPositionMillis()).isZero();
        assertThat(play.getData().creditedDurationMillis()).isZero();
        assertThat(progress.getEffectiveDurationMs()).isEqualTo(55_000L);
        assertThat(target.getStatus()).isEqualTo("COMPLETED");
        assertThat(target.getMaxConfirmedPositionMs()).isEqualTo(60_000L);

        active.setLastEventAt(LocalDateTime.of(2026, 8, 19, 15, 59, 55));
        when(progressManager.allTaskCoursesCompleted(20L, 10L, 500L)).thenReturn(true);
        Result<LearningEventResultView> pause = service.submitEvent(new SubmitEventCommand(
                900L, "browser-one", "replay-pause", 2L, "PAUSE", 301L, 5_000L));

        assertThat(pause.isSuccess()).isTrue();
        assertThat(pause.getData().creditedDurationMillis()).isEqualTo(5_000L);
        assertThat(pause.getData().courseCompleted()).isTrue();
        assertThat(progress.getEffectiveDurationMs()).isEqualTo(60_000L);
        assertThat(target.getMaxConfirmedPositionMs()).isEqualTo(60_000L);
    }

    /** 未完成课件不能借补学命令重置已确认位置，仍需按原位置续学。 */
    @Test
    void shouldKeepUnfinishedCoursewarePositionWhenPlayReportsZero() {
        prepareReplay("IN_PROGRESS");
        Result<LearningEventResultView> play = service.submitEvent(new SubmitEventCommand(
                900L, "browser-one", "resume-zero", 1L, "PLAY", 301L, 0L));
        assertThat(play.isSuccess()).isTrue();
        assertThat(play.getData().confirmedPositionMillis()).isEqualTo(60_000L);
        verifyNoInteractions(coursewareProgressMapper);
    }

    /** 构建可继续学习的真实领域对象，仅替换持久化和跨服务边界。 */
    private StudySessionEntity prepareReplay(String coursewareStatus) {
        prepareTransaction();
        StudySessionEntity active = session(101L, "browser-one", "PAUSED");
        active.setLastSequence(0L);
        active.setVersion(0);
        StudyProgressEntity progress = progress();
        progress.setId(200L);
        progress.setStatus("IN_PROGRESS");
        progress.setEffectiveDurationMs(55_000L);
        progress.setStudyToleranceSeconds(2);
        progress.setProgressReportIntervalSeconds(10);
        progress.setVersion(0);
        StudyCoursewareProgressEntity target = new StudyCoursewareProgressEntity();
        target.setId(300L);
        target.setCoursewareSnapshotId(301L);
        target.setStatus(coursewareStatus);
        target.setSortOrder(1);
        target.setDurationMs(60_000L);
        target.setConfirmedPositionMs(60_000L);
        target.setMaxConfirmedPositionMs(60_000L);
        target.setVersion(0);
        when(sessionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(active);
        when(trainingAccessClient.taskContext(100L)).thenReturn(context());
        when(progressManager.requireProgress(20L, 10L, 100L, 101L)).thenReturn(progress);
        when(progressManager.coursewares(20L, 10L, 101L)).thenReturn(Collections.singletonList(target));
        when(progressManager.requireCourseware(any(), eq(301L))).thenReturn(target);
        return active;
    }

    private void prepareTransaction() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
    }

    private LearningTaskContextView context() {
        return new LearningTaskContextView(
                500L, 100L, "安全培训",
                LocalDateTime.of(2026, 8, 19, 0, 0),
                LocalDateTime.of(2026, 8, 20, 0, 0),
                "IN_PROGRESS", "ASSIGNED", "NOT_STARTED", "NOT_COMPLETED",
                false, 300, 600, 60, 3,
                Arrays.asList(
                        new LearningCourseRuleView(
                                101L, 201L, "安全驾驶", 60, false, 20, 5, 1,
                                Collections.emptyList()),
                        new LearningCourseRuleView(
                                102L, 202L, "应急处置", 60, false, 20, 5, 2,
                                Collections.emptyList())));
    }

    private StudySessionEntity session(Long planCourseId, String clientId, String status) {
        StudySessionEntity value = new StudySessionEntity();
        value.setId(900L);
        value.setEnterpriseId(20L);
        value.setUserId(10L);
        value.setTaskId(500L);
        value.setPlanId(100L);
        value.setPlanCourseId(planCourseId);
        value.setClientInstanceId(clientId);
        value.setCourseName("安全驾驶");
        value.setStatus(status);
        return value;
    }

    private StudyProgressEntity progress() {
        StudyProgressEntity value = new StudyProgressEntity();
        value.setPlanCourseId(101L);
        value.setCourseName("安全驾驶");
        value.setRequiredDurationMs(60_000L);
        return value;
    }

    private LoginUser student() {
        LoginUser user = new LoginUser();
        user.setUserId(10L);
        user.setEnterpriseId(20L);
        user.setPermissions(Collections.singletonList("student:learning:study"));
        return user;
    }
}
