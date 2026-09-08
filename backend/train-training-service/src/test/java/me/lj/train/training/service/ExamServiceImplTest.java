package me.lj.train.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateWrapper;
import me.lj.train.api.training.ExamModels.ExamRecordView;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;
import me.lj.train.training.mapper.ExamAnswerMapper;
import me.lj.train.training.mapper.ExamPaperMapper;
import me.lj.train.training.mapper.ExamPaperQuestionMapper;
import me.lj.train.training.mapper.ExamRecordMapper;
import me.lj.train.training.mapper.PlanMapper;
import me.lj.train.training.mapper.PlanUserMapper;
import me.lj.train.training.model.entity.ExamAnswerEntity;
import me.lj.train.training.model.entity.ExamPaperEntity;
import me.lj.train.training.model.entity.ExamPaperQuestionEntity;
import me.lj.train.training.model.entity.ExamRecordEntity;
import me.lj.train.training.model.entity.PlanEntity;
import me.lj.train.training.model.entity.PlanUserEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDateTime;
import java.util.Collections;

import static me.lj.train.training.constant.TrainingConstants.EXAM_RECORD_IN_PROGRESS;
import static me.lj.train.training.constant.TrainingConstants.EXAM_RECORD_SUBMITTED;
import static me.lj.train.training.constant.TrainingConstants.EXAM_RECORD_TIMEOUT;
import static me.lj.train.training.constant.TrainingConstants.PAPER_ENABLED;
import static me.lj.train.training.constant.TrainingPermissions.STUDENT_EXAM_TAKE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

/** 学员考试唯一记录和自动判分核心测试。 */
@ExtendWith(MockitoExtension.class)
class ExamServiceImplTest {

    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;
    @Mock private PlanMapper planMapper;
    @Mock private PlanUserMapper planUserMapper;
    @Mock private ExamPaperMapper paperMapper;
    @Mock private ExamPaperQuestionMapper paperQuestionMapper;
    @Mock private ExamRecordMapper recordMapper;
    @Mock private ExamAnswerMapper answerMapper;
    @Mock private TrainingCompletionService completionService;

    private ExamServiceImpl service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        service = new ExamServiceImpl(
                transactionManager, planMapper, planUserMapper, paperMapper,
                paperQuestionMapper, recordMapper, answerMapper,
                new ExamJsonSupport(new ObjectMapper()), completionService);
        UserContext.set(student());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldOpenOneExamAndProjectInProgressStatus() {
        PlanUserEntity task = task();
        when(planUserMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(task);
        when(recordMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
        when(planMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(plan());
        when(paperMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(paper());
        when(paperQuestionMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(question()));
        when(answerMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        Result<ExamRecordView> result = service.open(100L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().status()).isEqualTo(EXAM_RECORD_IN_PROGRESS);
        assertThat(result.getData().questions()).hasSize(1);
        ArgumentCaptor<ExamRecordEntity> captor = ArgumentCaptor.forClass(ExamRecordEntity.class);
        verify(recordMapper).insertSelective(captor.capture());
        assertThat(captor.getValue().getDeadlineAt()).isAfter(captor.getValue().getStartedAt());
        verify(planUserMapper).updateByCondition(any(PlanUserEntity.class), any());
    }

    @Test
    void shouldGradeAnswerAndRecalculateCompletion() {
        ExamRecordEntity inProgress = record(EXAM_RECORD_IN_PROGRESS, null, null);
        ExamRecordEntity submitted = record(EXAM_RECORD_SUBMITTED, 10, true);
        when(recordMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(inProgress, submitted);
        when(paperQuestionMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(question()));
        ExamAnswerEntity answer = new ExamAnswerEntity();
        answer.setId(900L);
        answer.setPaperQuestionId(800L);
        answer.setAnswer("A");
        when(answerMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(answer));

        Result<ExamRecordView> result = service.submit(700L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().score()).isEqualTo(10);
        assertThat(result.getData().passed()).isTrue();
        verify(recordMapper).updateByCondition(any(ExamRecordEntity.class), any());
        verify(planUserMapper).updateByCondition(any(PlanUserEntity.class), any());
        verify(completionService).recalculate(any(), any(), any());
    }

    /** 重复交卷只返回既有成绩，不再判分或重算完成时间。 */
    @Test
    void shouldReturnSubmittedResultWithoutGradingAgain() {
        when(recordMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(record(EXAM_RECORD_SUBMITTED, 10, true));
        when(paperQuestionMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(question()));
        when(answerMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        Result<ExamRecordView> result = service.submit(700L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().score()).isEqualTo(10);
        verify(recordMapper, never()).updateByCondition(any(ExamRecordEntity.class), any());
        verify(completionService, never()).recalculate(any(), any(), any());
    }

    /** 截止后交卷走超时结算，未答题不能获得及格成绩。 */
    @Test
    void shouldSettleExpiredSubmissionAsTimeout() {
        ExamRecordEntity expired = record(EXAM_RECORD_IN_PROGRESS, null, null);
        expired.setDeadlineAt(LocalDateTime.now().minusSeconds(1));
        when(recordMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(expired, record(EXAM_RECORD_TIMEOUT, 0, false));
        when(paperQuestionMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(question()));
        when(answerMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        Result<ExamRecordView> result = service.submit(700L);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<ExamRecordEntity> update = ArgumentCaptor.forClass(ExamRecordEntity.class);
        verify(recordMapper).updateByCondition(update.capture(), any());
        assertThat(((UpdateWrapper<?>) update.getValue()).getUpdates())
                .containsEntry("status", EXAM_RECORD_TIMEOUT).containsEntry("score", 0);
        assertThat(result.getData().passed()).isFalse();
    }

    private LoginUser student() {
        LoginUser user = new LoginUser();
        user.setUserId(10L);
        user.setEnterpriseId(20L);
        user.setPermissions(Collections.singletonList(STUDENT_EXAM_TAKE));
        return user;
    }

    private PlanUserEntity task() {
        PlanUserEntity task = new PlanUserEntity();
        task.setId(500L);
        task.setEnterpriseId(20L);
        task.setPlanId(100L);
        task.setUserId(10L);
        task.setAssignmentStatus("ASSIGNED");
        return task;
    }

    private PlanEntity plan() {
        PlanEntity plan = new PlanEntity();
        plan.setId(100L);
        plan.setEnterpriseId(20L);
        plan.setStartAt(LocalDateTime.now().minusMinutes(1));
        plan.setEndAt(LocalDateTime.now().plusHours(2));
        plan.setExamRequired(true);
        plan.setExamPaperId(600L);
        plan.setExamPassScore(6);
        plan.setExamDurationMinutes(60);
        return plan;
    }

    private ExamPaperEntity paper() {
        ExamPaperEntity paper = new ExamPaperEntity();
        paper.setId(600L);
        paper.setEnterpriseId(20L);
        paper.setPaperName("安全考试");
        paper.setDurationMinutes(60);
        paper.setPassScore(6);
        paper.setTotalScore(10);
        paper.setStatus(PAPER_ENABLED);
        return paper;
    }

    private ExamPaperQuestionEntity question() {
        ExamPaperQuestionEntity question = new ExamPaperQuestionEntity();
        question.setId(800L);
        question.setEnterpriseId(20L);
        question.setPaperId(600L);
        question.setQuestionType("SINGLE_CHOICE");
        question.setContent("安全驾驶应选择？");
        question.setOptionsJson("[\"谨慎驾驶\",\"超速驾驶\"]");
        question.setCorrectAnswer("A");
        question.setScore(10);
        question.setSortOrder(1);
        return question;
    }

    private ExamRecordEntity record(String status, Integer score, Boolean passed) {
        ExamRecordEntity record = new ExamRecordEntity();
        record.setId(700L);
        record.setEnterpriseId(20L);
        record.setTaskId(500L);
        record.setPlanId(100L);
        record.setUserId(10L);
        record.setPaperId(600L);
        record.setPaperName("安全考试");
        record.setStatus(status);
        record.setStartedAt(LocalDateTime.now().minusMinutes(5));
        record.setDeadlineAt(LocalDateTime.now().plusMinutes(55));
        record.setPassScore(6);
        record.setTotalScore(10);
        record.setScore(score);
        record.setPassed(passed);
        return record;
    }
}
