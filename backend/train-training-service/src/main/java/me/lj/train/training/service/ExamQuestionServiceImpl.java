package me.lj.train.training.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateWrapper;
import me.lj.train.api.training.ExamModels.CreateQuestionCommand;
import me.lj.train.api.training.ExamModels.QuestionQuery;
import me.lj.train.api.training.ExamModels.QuestionView;
import me.lj.train.api.training.ExamModels.UpdateQuestionCommand;
import me.lj.train.api.training.ExamQuestionService;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.page.PageRequest;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.core.util.IdGenerator;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.training.mapper.ExamQuestionMapper;
import me.lj.train.training.model.entity.ExamQuestionEntity;
import me.lj.train.training.support.TrainingGuard;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static me.lj.train.training.constant.TrainingConstants.QUESTION_DISABLED;
import static me.lj.train.training.constant.TrainingConstants.QUESTION_ENABLED;
import static me.lj.train.training.constant.TrainingConstants.QUESTION_JUDGMENT;
import static me.lj.train.training.constant.TrainingConstants.QUESTION_SINGLE_CHOICE;
import static me.lj.train.training.constant.TrainingPermissions.EXAM_MANAGE;
import static me.lj.train.training.constant.TrainingPermissions.EXAM_VIEW;
import static me.lj.train.training.model.table.ExamQuestionTableDef.EXAM_QUESTION;

/** 单选题、判断题题库管理实现。 */
@DubboService(timeout = 10000, retries = 0)
public class ExamQuestionServiceImpl extends TrainingServiceSupport
        implements ExamQuestionService {

    private final ExamQuestionMapper questionMapper;
    private final ExamJsonSupport jsonSupport;

    public ExamQuestionServiceImpl(
            PlatformTransactionManager transactionManager,
            ExamQuestionMapper questionMapper,
            ExamJsonSupport jsonSupport) {
        super(transactionManager);
        this.questionMapper = questionMapper;
        this.jsonSupport = jsonSupport;
    }

    @Override
    public Result<PageResult<QuestionView>> page(QuestionQuery query) {
        return execute(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(EXAM_VIEW);
            PageRequest request = query.toPageRequest();
            String keyword = trim(query.keyword());
            String type = optionalType(query.questionType());
            String status = optionalStatus(query.status());
            Page<ExamQuestionEntity> page = questionMapper.paginate(
                    request.getPageNumber(), request.getPageSize(), QueryWrapper.create()
                            .where(EXAM_QUESTION.ENTERPRISE_ID.eq(enterpriseId))
                            .and(EXAM_QUESTION.DELETED_AT.isNull())
                            .and(EXAM_QUESTION.CONTENT.like(keyword).when(hasText(keyword)))
                            .and(EXAM_QUESTION.QUESTION_TYPE.eq(type).when(type != null))
                            .and(EXAM_QUESTION.STATUS.eq(status).when(status != null))
                            .orderBy(EXAM_QUESTION.CREATED_AT.desc()));
            List<QuestionView> records = page.getRecords().stream()
                    .map(this::toView).collect(Collectors.toList());
            return PageResult.of(records, page.getTotalRow(), request);
        });
    }

    @Override
    public Result<QuestionView> create(CreateQuestionCommand command) {
        return executeTransactional(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(EXAM_MANAGE);
            QuestionValues values = validate(command.questionType(), command.content(),
                    command.options(), command.correctAnswer(), command.analysis());
            Long operatorId = UserContext.require().getUserId();
            ExamQuestionEntity question = new ExamQuestionEntity();
            question.setId(IdGenerator.nextId());
            question.setEnterpriseId(enterpriseId);
            apply(question, values);
            question.setStatus(QUESTION_ENABLED);
            question.setCreatedBy(operatorId);
            question.setUpdatedBy(operatorId);
            questionMapper.insertSelective(question);
            return toView(requireQuestion(question.getId(), enterpriseId));
        });
    }

    @Override
    public Result<QuestionView> get(Long id) {
        return execute(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(EXAM_VIEW);
            return toView(requireQuestion(id, enterpriseId));
        });
    }

    @Override
    public Result<QuestionView> update(UpdateQuestionCommand command) {
        return executeTransactional(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(EXAM_MANAGE);
            ExamQuestionEntity existing = requireQuestion(command.id(), enterpriseId);
            QuestionValues values = validate(command.questionType(), command.content(),
                    command.options(), command.correctAnswer(), command.analysis());
            ExamQuestionEntity update = new ExamQuestionEntity();
            apply(update, values);
            update.setUpdatedBy(UserContext.require().getUserId());
            questionMapper.updateByCondition(update, EXAM_QUESTION.ID.eq(existing.getId())
                    .and(EXAM_QUESTION.ENTERPRISE_ID.eq(enterpriseId))
                    .and(EXAM_QUESTION.DELETED_AT.isNull()));
            return toView(requireQuestion(existing.getId(), enterpriseId));
        });
    }

    @Override
    public Result<QuestionView> changeStatus(Long id, String status) {
        return executeTransactional(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(EXAM_MANAGE);
            ExamQuestionEntity existing = requireQuestion(id, enterpriseId);
            String normalized = requiredStatus(status);
            ExamQuestionEntity update = new ExamQuestionEntity();
            update.setStatus(normalized);
            update.setUpdatedBy(UserContext.require().getUserId());
            questionMapper.updateByCondition(update, EXAM_QUESTION.ID.eq(existing.getId())
                    .and(EXAM_QUESTION.ENTERPRISE_ID.eq(enterpriseId))
                    .and(EXAM_QUESTION.DELETED_AT.isNull()));
            return toView(requireQuestion(existing.getId(), enterpriseId));
        });
    }

    @Override
    public Result<?> delete(Long id) {
        return executeVoidTransactional(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(EXAM_MANAGE);
            ExamQuestionEntity existing = requireQuestion(id, enterpriseId);
            LocalDateTime now = LocalDateTime.now();
            ExamQuestionEntity update = new ExamQuestionEntity();
            update.setDeletedBy(UserContext.require().getUserId());
            update.setDeletedAt(now);
            update.setUpdatedBy(UserContext.require().getUserId());
            questionMapper.updateByCondition(update, EXAM_QUESTION.ID.eq(existing.getId())
                    .and(EXAM_QUESTION.ENTERPRISE_ID.eq(enterpriseId))
                    .and(EXAM_QUESTION.DELETED_AT.isNull()));
        });
    }

    private ExamQuestionEntity requireQuestion(Long id, Long enterpriseId) {
        ExamQuestionEntity question = id == null ? null : questionMapper.selectOneByQuery(
                QueryWrapper.create().where(EXAM_QUESTION.ID.eq(id))
                        .and(EXAM_QUESTION.ENTERPRISE_ID.eq(enterpriseId))
                        .and(EXAM_QUESTION.DELETED_AT.isNull()));
        if (question == null) {
            throw new BusinessException(AppErrorCode.EXAM_QUESTION_NOT_FOUND);
        }
        TrainingGuard.checkEnterprise(question.getEnterpriseId(), enterpriseId);
        return question;
    }

    private QuestionValues validate(
            String typeValue,
            String contentValue,
            List<String> optionValues,
            String answerValue,
            String analysisValue) {
        String type = requiredType(typeValue);
        String content = TrainingGuard.requireText(contentValue, "题干", 1000);
        String analysis = TrainingGuard.optionalText(analysisValue, "答案解析", 1000);
        String answer = TrainingGuard.requireText(answerValue, "标准答案", 16)
                .toUpperCase(Locale.ROOT);
        List<String> options;
        if (QUESTION_JUDGMENT.equals(type)) {
            options = Arrays.asList("TRUE", "FALSE");
            if (!"TRUE".equals(answer) && !"FALSE".equals(answer)) {
                throw new BusinessException(AppErrorCode.PARAM_INVALID,
                        "判断题标准答案只能是TRUE或FALSE");
            }
        } else {
            options = normalizeOptions(optionValues);
            int answerIndex = answer.length() == 1 ? answer.charAt(0) - 'A' : -1;
            if (answerIndex < 0 || answerIndex >= options.size()) {
                throw new BusinessException(AppErrorCode.PARAM_INVALID,
                        "单选题标准答案必须对应有效选项");
            }
        }
        return new QuestionValues(type, content, options, answer, analysis);
    }

    private List<String> normalizeOptions(List<String> values) {
        if (values == null || values.size() < 2 || values.size() > 6) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "单选题须包含2至6个选项");
        }
        List<String> options = new ArrayList<String>();
        for (String value : values) {
            options.add(TrainingGuard.requireText(value, "题目选项", 500));
        }
        if (new LinkedHashSet<String>(options).size() != options.size()) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "题目选项不能重复");
        }
        return options;
    }

    private void apply(ExamQuestionEntity question, QuestionValues values) {
        question.setQuestionType(values.type());
        question.setContent(values.content());
        question.setOptionsJson(jsonSupport.writeOptions(values.options()));
        question.setCorrectAnswer(values.correctAnswer());
        question.setAnalysis(values.analysis());
    }

    private QuestionView toView(ExamQuestionEntity question) {
        return new QuestionView(
                question.getId(), question.getQuestionType(), question.getContent(),
                jsonSupport.readOptions(question.getOptionsJson()), question.getCorrectAnswer(),
                question.getAnalysis(), question.getStatus(), question.getCreatedAt(),
                question.getUpdatedAt());
    }

    private String requiredType(String value) {
        String type = trim(value);
        type = type == null ? null : type.toUpperCase(Locale.ROOT);
        if (!QUESTION_SINGLE_CHOICE.equals(type) && !QUESTION_JUDGMENT.equals(type)) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "题型不正确");
        }
        return type;
    }

    private String optionalType(String value) {
        return hasText(value) ? requiredType(value) : null;
    }

    private String requiredStatus(String value) {
        String status = trim(value);
        status = status == null ? null : status.toUpperCase(Locale.ROOT);
        if (!QUESTION_ENABLED.equals(status) && !QUESTION_DISABLED.equals(status)) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "题目状态不正确");
        }
        return status;
    }

    private String optionalStatus(String value) {
        return hasText(value) ? requiredStatus(value) : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private record QuestionValues(
            String type,
            String content,
            List<String> options,
            String correctAnswer,
            String analysis) {
    }
}
