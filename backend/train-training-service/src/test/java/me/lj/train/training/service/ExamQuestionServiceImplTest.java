package me.lj.train.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.api.training.ExamModels.CreateQuestionCommand;
import me.lj.train.api.training.ExamModels.QuestionView;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;
import me.lj.train.training.mapper.ExamQuestionMapper;
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

import java.util.Arrays;
import java.util.Collections;

import static me.lj.train.training.constant.TrainingPermissions.EXAM_MANAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 题库题型校验和持久化快照测试。 */
@ExtendWith(MockitoExtension.class)
class ExamQuestionServiceImplTest {

    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;
    @Mock private ExamQuestionMapper questionMapper;

    private ExamQuestionServiceImpl service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        service = new ExamQuestionServiceImpl(
                transactionManager, questionMapper, new ExamJsonSupport(new ObjectMapper()));
        UserContext.set(administrator());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldCreateSingleChoiceQuestionWithNormalizedAnswer() {
        when(questionMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(savedQuestion());

        Result<QuestionView> result = service.create(new CreateQuestionCommand(
                "single_choice", "安全驾驶应当？", Arrays.asList("谨慎驾驶", "超速驾驶"),
                "a", "保持安全车距"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().correctAnswer()).isEqualTo("A");
        ArgumentCaptor<ExamQuestionEntity> captor =
                ArgumentCaptor.forClass(ExamQuestionEntity.class);
        verify(questionMapper).insertSelective(captor.capture());
        assertThat(captor.getValue().getQuestionType()).isEqualTo("SINGLE_CHOICE");
        assertThat(captor.getValue().getOptionsJson()).contains("谨慎驾驶", "超速驾驶");
    }

    @Test
    void shouldRejectDuplicateSingleChoiceOptions() {
        Result<QuestionView> result = service.create(new CreateQuestionCommand(
                "SINGLE_CHOICE", "请选择", Arrays.asList("相同选项", "相同选项"),
                "A", null));

        assertThat(result.getCode()).isEqualTo(AppErrorCode.PARAM_INVALID.getCode());
        verifyNoInteractions(questionMapper);
    }

    /** 构造具备题库维护权限的企业管理员。 */
    private LoginUser administrator() {
        LoginUser user = new LoginUser();
        user.setUserId(10L);
        user.setEnterpriseId(20L);
        user.setPermissions(Collections.singletonList(EXAM_MANAGE));
        return user;
    }

    /** 构造创建后重新查询到的题目。 */
    private ExamQuestionEntity savedQuestion() {
        ExamQuestionEntity question = new ExamQuestionEntity();
        question.setId(100L);
        question.setEnterpriseId(20L);
        question.setQuestionType("SINGLE_CHOICE");
        question.setContent("安全驾驶应当？");
        question.setOptionsJson("[\"谨慎驾驶\",\"超速驾驶\"]");
        question.setCorrectAnswer("A");
        question.setAnalysis("保持安全车距");
        question.setStatus("ENABLED");
        return question;
    }
}
