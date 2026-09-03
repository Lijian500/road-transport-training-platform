package me.lj.train.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.api.training.ExamModels.PaperView;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;
import me.lj.train.training.mapper.ExamPaperMapper;
import me.lj.train.training.mapper.ExamPaperQuestionMapper;
import me.lj.train.training.mapper.ExamQuestionMapper;
import me.lj.train.training.model.entity.ExamPaperEntity;
import me.lj.train.training.model.entity.ExamPaperQuestionEntity;
import me.lj.train.training.model.entity.ExamQuestionEntity;
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

import java.util.Collections;
import java.util.List;

import static me.lj.train.training.constant.TrainingPermissions.EXAM_MANAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 试卷启用时手工选题、随机补齐和固化边界测试。 */
@ExtendWith(MockitoExtension.class)
class ExamPaperServiceImplTest {

    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;
    @Mock private ExamPaperMapper paperMapper;
    @Mock private ExamQuestionMapper questionMapper;
    @Mock private ExamPaperQuestionMapper paperQuestionMapper;

    private ExamPaperServiceImpl service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        service = new ExamPaperServiceImpl(
                transactionManager, paperMapper, questionMapper, paperQuestionMapper,
                new ExamJsonSupport(new ObjectMapper()));
        UserContext.set(administrator());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldFreezeManualAndRandomQuestionsWhenEnabled() {
        ExamPaperEntity draft = paper(1, 1, "DRAFT");
        ExamPaperEntity enabled = paper(1, 1, "ENABLED");
        ExamQuestionEntity manual = question(100L, "手工题");
        ExamQuestionEntity random = question(101L, "随机题");
        ExamPaperQuestionEntity draftSnapshot = snapshot(100L, "MANUAL", 1);
        List<ExamPaperQuestionEntity> enabledSnapshots = java.util.Arrays.asList(
                snapshot(100L, "MANUAL", 1), snapshot(101L, "RANDOM", 2));
        when(paperMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(draft, enabled);
        when(paperQuestionMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(draftSnapshot), enabledSnapshots);
        when(questionMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(manual), Collections.singletonList(random));

        Result<PaperView> result = service.enable(600L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().questions()).hasSize(2);
        ArgumentCaptor<ExamPaperQuestionEntity> captor =
                ArgumentCaptor.forClass(ExamPaperQuestionEntity.class);
        verify(paperQuestionMapper, times(2)).insertSelective(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ExamPaperQuestionEntity::getSelectionType)
                .containsExactly("MANUAL", "RANDOM");
        verify(paperMapper).updateByCondition(any(ExamPaperEntity.class), any());
    }

    @Test
    void shouldRejectEnableWhenRandomCandidatesAreInsufficient() {
        ExamPaperEntity draft = paper(0, 1, "DRAFT");
        when(paperMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(draft);
        when(paperQuestionMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(questionMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        Result<PaperView> result = service.enable(600L);

        assertThat(result.getCode()).isEqualTo(AppErrorCode.EXAM_PAPER_ENABLE_INVALID.getCode());
    }

    /** 构造具备试卷维护权限的企业管理员。 */
    private LoginUser administrator() {
        LoginUser user = new LoginUser();
        user.setUserId(10L);
        user.setEnterpriseId(20L);
        user.setPermissions(Collections.singletonList(EXAM_MANAGE));
        return user;
    }

    /** 构造试卷草稿或启用后的实体。 */
    private ExamPaperEntity paper(int manualCount, int randomCount, String status) {
        ExamPaperEntity paper = new ExamPaperEntity();
        paper.setId(600L);
        paper.setEnterpriseId(20L);
        paper.setPaperName("安全考试");
        paper.setDurationMinutes(60);
        paper.setPassScore(10);
        paper.setQuestionScore(10);
        paper.setManualQuestionCount(manualCount);
        paper.setRandomQuestionCount(randomCount);
        paper.setTotalScore((manualCount + randomCount) * 10);
        paper.setStatus(status);
        return paper;
    }

    /** 构造题库中的启用题目。 */
    private ExamQuestionEntity question(Long id, String content) {
        ExamQuestionEntity question = new ExamQuestionEntity();
        question.setId(id);
        question.setEnterpriseId(20L);
        question.setQuestionType("SINGLE_CHOICE");
        question.setContent(content);
        question.setOptionsJson("[\"正确\",\"错误\"]");
        question.setCorrectAnswer("A");
        question.setStatus("ENABLED");
        return question;
    }

    /** 构造试卷中已保存的题目快照。 */
    private ExamPaperQuestionEntity snapshot(Long sourceQuestionId, String type, int order) {
        ExamPaperQuestionEntity snapshot = new ExamPaperQuestionEntity();
        snapshot.setId(700L + order);
        snapshot.setEnterpriseId(20L);
        snapshot.setPaperId(600L);
        snapshot.setSourceQuestionId(sourceQuestionId);
        snapshot.setSelectionType(type);
        snapshot.setQuestionType("SINGLE_CHOICE");
        snapshot.setContent("题目" + order);
        snapshot.setOptionsJson("[\"正确\",\"错误\"]");
        snapshot.setCorrectAnswer("A");
        snapshot.setScore(10);
        snapshot.setSortOrder(order);
        return snapshot;
    }
}
