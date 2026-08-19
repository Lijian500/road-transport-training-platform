package me.lj.train.learning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
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

    private ObjectMapper objectMapper;
    private LearningSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new LearningSessionServiceImpl(
                transactionManager, sessionMapper, progressMapper, coursewareProgressMapper,
                eventLogMapper, progressManager, outboxService, trainingAccessClient,
                objectMapper, Clock.fixed(Instant.parse("2026-08-19T08:00:00Z"),
                ZoneId.of("Asia/Shanghai")), new LearningTimeCalculator());
        UserContext.set(student());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
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
