package me.lj.train.learning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.api.learning.FaceCheckEvents;
import me.lj.train.api.learning.FaceCheckModels.FaceCheckView;
import me.lj.train.api.learning.FaceCheckModels.SubmitFaceCheckCommand;
import me.lj.train.api.training.PrivateImageModels.PrivateImageContentView;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.security.context.UserContext;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 人脸抽验通过、失败、超时和幂等处理测试。 */
@ExtendWith(MockitoExtension.class)
class FaceCheckServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 19, 16, 0);

    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;
    @Mock private FaceCheckTaskMapper taskMapper;
    @Mock private FaceCheckLogMapper logMapper;
    @Mock private StudySessionMapper sessionMapper;
    @Mock private StudyProgressMapper progressMapper;
    @Mock private LearningOutboxService outboxService;
    @Mock private FaceReferenceImageClient referenceImageClient;
    @Mock private ObjectProvider<FaceVerifier> verifierProvider;
    @Mock private FaceVerifier verifier;

    private ObjectMapper objectMapper;
    private FaceCheckServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        service = new FaceCheckServiceImpl(
                transactionManager, taskMapper, logMapper, sessionMapper, progressMapper,
                outboxService, referenceImageClient, verifierProvider, objectMapper,
                Clock.fixed(Instant.parse("2026-08-19T08:00:00Z"),
                        ZoneId.of("Asia/Shanghai")));
        UserContext.set(student());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /** 核验通过后任务进入终态，会话回到暂停态并安排下一次阈值。 */
    @Test
    void shouldResumePausedStateAfterFaceCheckPasses() {
        FaceCheckTaskEntity task = pendingTask(3, NOW.plusMinutes(1));
        StudySessionEntity session = pendingSession();
        StudyProgressEntity progress = progress();
        prepareSubmission(task, session);
        when(verifierProvider.getIfAvailable()).thenReturn(verifier);
        when(referenceImageClient.read(10L)).thenReturn(referenceImage());
        when(referenceImageClient.saveLearningPhoto(any(byte[].class))).thenReturn(900L);
        when(verifier.compare(any(byte[].class), any(byte[].class)))
                .thenReturn(Result.ok(comparison(0.80D)));
        when(progressMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(progress);

        Result<FaceCheckView> result = service.submitFaceCheck(
                new SubmitFaceCheckCommand(400L, "request-pass", new byte[]{9, 8, 7}));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().status()).isEqualTo("PASSED");
        assertThat(task.getAttemptCount()).isEqualTo(1);
        assertThat(session.getStatus()).isEqualTo(LearningSessionServiceImpl.PAUSED);
        assertThat(session.getNextFaceCheckEffectiveDurationMs()).isGreaterThan(100_000L);
        var photoLog = org.mockito.ArgumentCaptor.forClass(FaceCheckLogEntity.class);
        verify(logMapper).insertSelective(photoLog.capture());
        assertThat(photoLog.getValue().getPhotoObjectId()).isEqualTo(900L);
        verify(outboxService).appendFaceCheckEvent(
                eq(400L), eq("RESULT:PASSED:1"),
                eq(FaceCheckEvents.RESULT_ROUTING_KEY), any(), eq(NOW));
    }

    /** 最后一次核验不匹配时终止学习会话。 */
    @Test
    void shouldTerminateSessionWhenFailureAttemptsAreExhausted() {
        FaceCheckTaskEntity task = pendingTask(1, NOW.plusMinutes(1));
        StudySessionEntity session = pendingSession();
        prepareSubmission(task, session);
        when(verifierProvider.getIfAvailable()).thenReturn(verifier);
        when(referenceImageClient.read(10L)).thenReturn(referenceImage());
        when(referenceImageClient.saveLearningPhoto(any(byte[].class))).thenReturn(900L);
        when(verifier.compare(any(byte[].class), any(byte[].class)))
                .thenReturn(Result.ok(comparison(0.10D)));

        Result<FaceCheckView> result = service.submitFaceCheck(
                new SubmitFaceCheckCommand(400L, "request-failed", new byte[]{1, 2, 3}));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().status()).isEqualTo("FAILED");
        assertThat(session.getStatus()).isEqualTo(LearningSessionServiceImpl.TERMINATED);
        assertThat(session.getTerminationReason()).isEqualTo("FACE_CHECK_FAILED");
        var photoLog = org.mockito.ArgumentCaptor.forClass(FaceCheckLogEntity.class);
        verify(logMapper).insertSelective(photoLog.capture());
        assertThat(photoLog.getValue().getPhotoObjectId()).isEqualTo(900L);
        assertThat(service.currentTerminal(session.getId()).status()).isEqualTo("FAILED");
        verify(sessionMapper).updateByCondition(eq(session), any());
    }

    /** 截止时间已到的待处理任务应终止对应学习会话。 */
    @Test
    void shouldTerminateSessionWhenFaceCheckTimesOut() {
        FaceCheckTaskEntity task = pendingTask(3, NOW.minusSeconds(1));
        StudySessionEntity session = pendingSession();
        when(taskMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(task);
        when(sessionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(session);

        service.timeoutTask(400L);

        assertThat(task.getStatus()).isEqualTo("TIMED_OUT");
        assertThat(session.getStatus()).isEqualTo(LearningSessionServiceImpl.TERMINATED);
        assertThat(session.getTerminationReason()).isEqualTo("FACE_CHECK_TIMEOUT");
        assertThat(service.currentTerminal(session.getId()).status()).isEqualTo("TIMED_OUT");
        verify(taskMapper).updateByCondition(eq(task), any());
        verify(sessionMapper).updateByCondition(eq(session), any());
    }

    /** 相同请求ID直接返回已保存响应，不重复读取照片或执行模型。 */
    @Test
    void shouldReturnStoredResponseForDuplicateRequest() throws Exception {
        FaceCheckTaskEntity task = pendingTask(3, NOW.plusMinutes(1));
        FaceCheckView stored = new FaceCheckView(
                400L, 900L, "PENDING", NOW.minusSeconds(10), NOW.plusMinutes(1),
                1, 3, 2, "NOT_MATCH", "NOT_MATCH", 0.10D, null);
        FaceCheckLogEntity duplicate = new FaceCheckLogEntity();
        duplicate.setResponsePayload(objectMapper.writeValueAsString(stored));
        when(taskMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(task);
        when(logMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(duplicate);

        Result<FaceCheckView> result = service.submitFaceCheck(
                new SubmitFaceCheckCommand(400L, "request-duplicate", new byte[]{1}));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(stored);
        verifyNoInteractions(referenceImageClient, verifierProvider, verifier, sessionMapper);
        verify(taskMapper, never()).updateByCondition(any(), any());
    }

    private void prepareSubmission(
            FaceCheckTaskEntity task, StudySessionEntity session) {
        when(taskMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(task);
        when(logMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
        when(sessionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(session);
    }

    private FaceCheckTaskEntity pendingTask(int maxAttempts, LocalDateTime deadline) {
        FaceCheckTaskEntity task = new FaceCheckTaskEntity();
        task.setId(400L);
        task.setEnterpriseId(20L);
        task.setUserId(10L);
        task.setSessionId(900L);
        task.setTrainingTaskId(500L);
        task.setPlanId(100L);
        task.setPlanCourseId(101L);
        task.setStatus("PENDING");
        task.setTriggeredAt(NOW.minusSeconds(10));
        task.setDeadlineAt(deadline);
        task.setMaxAttempts(maxAttempts);
        task.setCreatedAt(NOW.minusSeconds(10));
        return task;
    }

    private StudySessionEntity pendingSession() {
        StudySessionEntity session = new StudySessionEntity();
        session.setId(900L);
        session.setEnterpriseId(20L);
        session.setUserId(10L);
        session.setTaskId(500L);
        session.setPlanId(100L);
        session.setPlanCourseId(101L);
        session.setStatus(LearningSessionServiceImpl.FACE_PENDING);
        session.setFaceCheckEnabled(true);
        session.setFaceCheckMinIntervalSeconds(30);
        session.setFaceCheckMaxIntervalSeconds(60);
        session.setFaceCheckTimeoutSeconds(60);
        session.setFaceCheckMaxAttempts(3);
        return session;
    }

    private StudyProgressEntity progress() {
        StudyProgressEntity progress = new StudyProgressEntity();
        progress.setEffectiveDurationMs(100_000L);
        progress.setRequiredDurationMs(600_000L);
        return progress;
    }

    private PrivateImageContentView referenceImage() {
        return new PrivateImageContentView(
                700L, "image/jpeg", new byte[]{4, 5, 6}, "reference-sha256");
    }

    private FaceComparisonResult comparison(double similarity) {
        FaceDetectionResult detection = FaceDetectionResult.of(Collections.emptyList(), 1L);
        return FaceComparisonResult.compared(
                similarity, 0.363D, detection, detection, 12L);
    }

    private LoginUser student() {
        LoginUser user = new LoginUser();
        user.setUserId(10L);
        user.setEnterpriseId(20L);
        user.setPermissions(Collections.singletonList("student:learning:study"));
        return user;
    }
}
