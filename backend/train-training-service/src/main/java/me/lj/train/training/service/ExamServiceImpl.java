package me.lj.train.training.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateWrapper;
import me.lj.train.api.training.ExamModels.AnswerCommand;
import me.lj.train.api.training.ExamModels.ExamQuestionView;
import me.lj.train.api.training.ExamModels.ExamRecordView;
import me.lj.train.api.training.ExamModels.SaveAnswersCommand;
import me.lj.train.api.training.ExamService;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.core.util.IdGenerator;
import me.lj.train.common.security.context.UserContext;
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
import me.lj.train.training.support.TrainingGuard;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static me.lj.train.training.constant.TrainingConstants.ASSIGNMENT_ASSIGNED;
import static me.lj.train.training.constant.TrainingConstants.EXAM_FAILED;
import static me.lj.train.training.constant.TrainingConstants.EXAM_IN_PROGRESS;
import static me.lj.train.training.constant.TrainingConstants.EXAM_PASSED;
import static me.lj.train.training.constant.TrainingConstants.EXAM_RECORD_IN_PROGRESS;
import static me.lj.train.training.constant.TrainingConstants.EXAM_RECORD_SUBMITTED;
import static me.lj.train.training.constant.TrainingConstants.EXAM_RECORD_TIMEOUT;
import static me.lj.train.training.constant.TrainingConstants.PAPER_ENABLED;
import static me.lj.train.training.constant.TrainingConstants.QUESTION_JUDGMENT;
import static me.lj.train.training.constant.TrainingPermissions.STUDENT_EXAM_TAKE;
import static me.lj.train.training.model.table.ExamAnswerTableDef.EXAM_ANSWER;
import static me.lj.train.training.model.table.ExamPaperQuestionTableDef.EXAM_PAPER_QUESTION;
import static me.lj.train.training.model.table.ExamPaperTableDef.EXAM_PAPER;
import static me.lj.train.training.model.table.ExamRecordTableDef.EXAM_RECORD;
import static me.lj.train.training.model.table.PlanTableDef.PLAN;
import static me.lj.train.training.model.table.PlanUserTableDef.PLAN_USER;

/** 学员唯一考试、答案保存、超时和自动判分实现。 */
@DubboService(timeout = 10000, retries = 0)
public class ExamServiceImpl extends TrainingServiceSupport implements ExamService {

    private static final int MAX_ANSWERS_PER_REQUEST = 200;

    private final PlanMapper planMapper;
    private final PlanUserMapper planUserMapper;
    private final ExamPaperMapper paperMapper;
    private final ExamPaperQuestionMapper paperQuestionMapper;
    private final ExamRecordMapper recordMapper;
    private final ExamAnswerMapper answerMapper;
    private final ExamJsonSupport jsonSupport;
    private final TrainingCompletionService completionService;

    public ExamServiceImpl(
            PlatformTransactionManager transactionManager,
            PlanMapper planMapper,
            PlanUserMapper planUserMapper,
            ExamPaperMapper paperMapper,
            ExamPaperQuestionMapper paperQuestionMapper,
            ExamRecordMapper recordMapper,
            ExamAnswerMapper answerMapper,
            ExamJsonSupport jsonSupport,
            TrainingCompletionService completionService) {
        super(transactionManager);
        this.planMapper = planMapper;
        this.planUserMapper = planUserMapper;
        this.paperMapper = paperMapper;
        this.paperQuestionMapper = paperQuestionMapper;
        this.recordMapper = recordMapper;
        this.answerMapper = answerMapper;
        this.jsonSupport = jsonSupport;
        this.completionService = completionService;
    }

    @Override
    public Result<ExamRecordView> open(Long planId) {
        return executeTransactional(() -> openExam(planId));
    }

    @Override
    public Result<ExamRecordView> get(Long recordId) {
        return executeTransactional(() -> {
            Owner owner = requireOwner();
            ExamRecordEntity record = requireRecord(recordId, owner, true);
            settleIfExpired(record, LocalDateTime.now());
            return toView(requireRecord(recordId, owner, false));
        });
    }

    @Override
    public Result<ExamRecordView> saveAnswers(SaveAnswersCommand command) {
        return executeTransactional(() -> save(command));
    }

    @Override
    public Result<ExamRecordView> submit(Long recordId) {
        return executeTransactional(() -> {
            Owner owner = requireOwner();
            ExamRecordEntity record = requireRecord(recordId, owner, true);
            if (EXAM_RECORD_IN_PROGRESS.equals(record.getStatus())) {
                LocalDateTime now = LocalDateTime.now();
                grade(record, !record.getDeadlineAt().isAfter(now)
                        ? EXAM_RECORD_TIMEOUT : EXAM_RECORD_SUBMITTED, now);
            }
            return toView(requireRecord(recordId, owner, false));
        });
    }

    /** 定时任务调用的单记录超时入口。 */
    void timeout(Long recordId) {
        runInTransaction(() -> {
            ExamRecordEntity record = recordMapper.selectOneByQuery(QueryWrapper.create()
                    .where(EXAM_RECORD.ID.eq(recordId)).forUpdate());
            if (record != null && EXAM_RECORD_IN_PROGRESS.equals(record.getStatus())
                    && !record.getDeadlineAt().isAfter(LocalDateTime.now())) {
                grade(record, EXAM_RECORD_TIMEOUT, LocalDateTime.now());
            }
        });
    }

    private ExamRecordView openExam(Long planId) {
        Owner owner = requireOwner();
        LocalDateTime now = LocalDateTime.now();
        PlanUserEntity task = planUserMapper.selectOneByQuery(QueryWrapper.create()
                .where(PLAN_USER.ENTERPRISE_ID.eq(owner.enterpriseId()))
                .and(PLAN_USER.PLAN_ID.eq(planId))
                .and(PLAN_USER.USER_ID.eq(owner.userId()))
                .and(PLAN_USER.ASSIGNMENT_STATUS.eq(ASSIGNMENT_ASSIGNED)).forUpdate());
        if (task == null) {
            throw new BusinessException(AppErrorCode.EXAM_ACCESS_DENIED);
        }
        ExamRecordEntity existing = recordMapper.selectOneByQuery(QueryWrapper.create()
                .where(EXAM_RECORD.ENTERPRISE_ID.eq(owner.enterpriseId()))
                .and(EXAM_RECORD.TASK_ID.eq(task.getId())));
        if (existing != null) {
            settleIfExpired(existing, now);
            return toView(requireRecord(existing.getId(), owner, false));
        }
        PlanEntity plan = planMapper.selectOneByQuery(QueryWrapper.create()
                .where(PLAN.ID.eq(planId))
                .and(PLAN.ENTERPRISE_ID.eq(owner.enterpriseId()))
                .and(PLAN.DELETED_AT.isNull()));
        if (plan == null || !plan.isExamRequired() || plan.getExamPaperId() == null
                || plan.getExamPassScore() == null || plan.getExamDurationMinutes() == null
                || now.isBefore(plan.getStartAt()) || !now.isBefore(plan.getEndAt())) {
            throw new BusinessException(AppErrorCode.EXAM_ACCESS_DENIED);
        }
        ExamPaperEntity paper = paperMapper.selectOneByQuery(QueryWrapper.create()
                .where(EXAM_PAPER.ID.eq(plan.getExamPaperId()))
                .and(EXAM_PAPER.ENTERPRISE_ID.eq(owner.enterpriseId()))
                .and(EXAM_PAPER.STATUS.eq(PAPER_ENABLED))
                .and(EXAM_PAPER.DELETED_AT.isNull()));
        List<ExamPaperQuestionEntity> questions = listQuestions(
                plan.getExamPaperId(), owner.enterpriseId());
        if (paper == null || questions.isEmpty()) {
            throw new BusinessException(AppErrorCode.EXAM_ACCESS_DENIED,
                    "计划试卷暂不可用");
        }
        ExamRecordEntity record = new ExamRecordEntity();
        record.setId(IdGenerator.nextId());
        record.setEnterpriseId(owner.enterpriseId());
        record.setTaskId(task.getId());
        record.setPlanId(plan.getId());
        record.setUserId(owner.userId());
        record.setPaperId(paper.getId());
        record.setPaperName(paper.getPaperName());
        record.setDurationMinutes(plan.getExamDurationMinutes());
        record.setPassScore(plan.getExamPassScore());
        record.setTotalScore(paper.getTotalScore());
        record.setStatus(EXAM_RECORD_IN_PROGRESS);
        record.setStartedAt(now);
        record.setDeadlineAt(min(now.plusMinutes(plan.getExamDurationMinutes()), plan.getEndAt()));
        record.setCreatedBy(owner.userId());
        record.setUpdatedBy(owner.userId());
        recordMapper.insertSelective(record);
        PlanUserEntity taskUpdate = UpdateWrapper.of(PlanUserEntity.class)
                .set(PLAN_USER.EXAM_STATUS, EXAM_IN_PROGRESS)
                .set(PLAN_USER.UPDATED_BY, owner.userId()).toEntity();
        planUserMapper.updateByCondition(taskUpdate, PLAN_USER.ID.eq(task.getId())
                .and(PLAN_USER.ENTERPRISE_ID.eq(owner.enterpriseId())));
        return toView(record);
    }

    private ExamRecordView save(SaveAnswersCommand command) {
        if (command == null || command.recordId() == null || command.answers() == null
                || command.answers().size() > MAX_ANSWERS_PER_REQUEST) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "答案列表不正确");
        }
        Owner owner = requireOwner();
        ExamRecordEntity record = requireRecord(command.recordId(), owner, true);
        LocalDateTime now = LocalDateTime.now();
        if (!EXAM_RECORD_IN_PROGRESS.equals(record.getStatus())) {
            return toView(record);
        }
        if (!record.getDeadlineAt().isAfter(now)) {
            grade(record, EXAM_RECORD_TIMEOUT, now);
            return toView(requireRecord(record.getId(), owner, false));
        }
        Map<Long, ExamPaperQuestionEntity> questionMap = listQuestions(
                record.getPaperId(), owner.enterpriseId()).stream().collect(
                        Collectors.toMap(ExamPaperQuestionEntity::getId, Function.identity()));
        Map<Long, String> normalized = new LinkedHashMap<Long, String>();
        for (AnswerCommand answer : command.answers()) {
            if (answer == null || answer.paperQuestionId() == null
                    || normalized.put(answer.paperQuestionId(), normalizeAnswer(
                    questionMap.get(answer.paperQuestionId()), answer.answer())) != null) {
                throw new BusinessException(AppErrorCode.PARAM_INVALID,
                        "同一道题不能重复保存");
            }
        }
        Long operatorId = owner.userId();
        normalized.forEach((questionId, answer) -> upsertAnswer(
                record, questionId, answer, now, operatorId));
        return toView(record);
    }

    private void upsertAnswer(
            ExamRecordEntity record,
            Long questionId,
            String answer,
            LocalDateTime now,
            Long operatorId) {
        ExamAnswerEntity existing = answerMapper.selectOneByQuery(QueryWrapper.create()
                .where(EXAM_ANSWER.ENTERPRISE_ID.eq(record.getEnterpriseId()))
                .and(EXAM_ANSWER.RECORD_ID.eq(record.getId()))
                .and(EXAM_ANSWER.PAPER_QUESTION_ID.eq(questionId)));
        if (existing == null) {
            ExamAnswerEntity entity = new ExamAnswerEntity();
            entity.setId(IdGenerator.nextId());
            entity.setEnterpriseId(record.getEnterpriseId());
            entity.setRecordId(record.getId());
            entity.setPaperQuestionId(questionId);
            entity.setAnswer(answer);
            entity.setAnsweredAt(now);
            entity.setCreatedBy(operatorId);
            entity.setUpdatedBy(operatorId);
            answerMapper.insertSelective(entity);
            return;
        }
        ExamAnswerEntity update = UpdateWrapper.of(ExamAnswerEntity.class)
                .set(EXAM_ANSWER.ANSWER, answer)
                .set(EXAM_ANSWER.ANSWERED_AT, now)
                .set(EXAM_ANSWER.UPDATED_BY, operatorId).toEntity();
        answerMapper.updateByCondition(update, EXAM_ANSWER.ID.eq(existing.getId()));
    }

    private void settleIfExpired(ExamRecordEntity record, LocalDateTime now) {
        if (EXAM_RECORD_IN_PROGRESS.equals(record.getStatus())
                && !record.getDeadlineAt().isAfter(now)) {
            grade(record, EXAM_RECORD_TIMEOUT, now);
        }
    }

    private void grade(ExamRecordEntity record, String finalStatus, LocalDateTime now) {
        List<ExamPaperQuestionEntity> questions = listQuestions(
                record.getPaperId(), record.getEnterpriseId());
        Map<Long, ExamAnswerEntity> answers = listAnswers(
                record.getId(), record.getEnterpriseId()).stream().collect(
                        Collectors.toMap(ExamAnswerEntity::getPaperQuestionId, Function.identity()));
        int score = 0;
        for (ExamPaperQuestionEntity question : questions) {
            ExamAnswerEntity answer = answers.get(question.getId());
            if (answer == null) {
                continue;
            }
            boolean correct = question.getCorrectAnswer().equalsIgnoreCase(answer.getAnswer());
            int questionScore = correct ? question.getScore() : 0;
            score += questionScore;
            ExamAnswerEntity update = UpdateWrapper.of(ExamAnswerEntity.class)
                    .set(EXAM_ANSWER.CORRECT, correct)
                    .set(EXAM_ANSWER.SCORE, questionScore)
                    .set(EXAM_ANSWER.UPDATED_BY, record.getUserId()).toEntity();
            answerMapper.updateByCondition(update, EXAM_ANSWER.ID.eq(answer.getId()));
        }
        boolean passed = score >= record.getPassScore();
        ExamRecordEntity recordUpdate = UpdateWrapper.of(ExamRecordEntity.class)
                .set(EXAM_RECORD.STATUS, finalStatus)
                .set(EXAM_RECORD.SUBMITTED_AT, now)
                .set(EXAM_RECORD.SCORE, score)
                .set(EXAM_RECORD.PASSED, passed)
                .set(EXAM_RECORD.UPDATED_BY, record.getUserId()).toEntity();
        recordMapper.updateByCondition(recordUpdate, EXAM_RECORD.ID.eq(record.getId())
                .and(EXAM_RECORD.STATUS.eq(EXAM_RECORD_IN_PROGRESS)));
        PlanUserEntity taskUpdate = UpdateWrapper.of(PlanUserEntity.class)
                .set(PLAN_USER.EXAM_STATUS, passed ? EXAM_PASSED : EXAM_FAILED)
                .set(PLAN_USER.UPDATED_BY, record.getUserId()).toEntity();
        planUserMapper.updateByCondition(taskUpdate, PLAN_USER.ID.eq(record.getTaskId())
                .and(PLAN_USER.ENTERPRISE_ID.eq(record.getEnterpriseId())));
        completionService.recalculate(record.getTaskId(), record.getEnterpriseId(), now);
    }

    private Owner requireOwner() {
        Long enterpriseId = TrainingGuard.requireEnterprisePermission(STUDENT_EXAM_TAKE);
        return new Owner(enterpriseId, UserContext.require().getUserId());
    }

    private ExamRecordEntity requireRecord(Long id, Owner owner, boolean lock) {
        QueryWrapper query = QueryWrapper.create().where(EXAM_RECORD.ID.eq(id))
                .and(EXAM_RECORD.ENTERPRISE_ID.eq(owner.enterpriseId()))
                .and(EXAM_RECORD.USER_ID.eq(owner.userId()));
        if (lock) {
            query.forUpdate();
        }
        ExamRecordEntity record = id == null ? null : recordMapper.selectOneByQuery(query);
        if (record == null) {
            throw new BusinessException(AppErrorCode.EXAM_RECORD_NOT_FOUND);
        }
        return record;
    }

    private List<ExamPaperQuestionEntity> listQuestions(Long paperId, Long enterpriseId) {
        return paperQuestionMapper.selectListByQuery(QueryWrapper.create()
                .where(EXAM_PAPER_QUESTION.ENTERPRISE_ID.eq(enterpriseId))
                .and(EXAM_PAPER_QUESTION.PAPER_ID.eq(paperId))
                .orderBy(EXAM_PAPER_QUESTION.SORT_ORDER.asc(), EXAM_PAPER_QUESTION.ID.asc()));
    }

    private List<ExamAnswerEntity> listAnswers(Long recordId, Long enterpriseId) {
        return answerMapper.selectListByQuery(QueryWrapper.create()
                .where(EXAM_ANSWER.ENTERPRISE_ID.eq(enterpriseId))
                .and(EXAM_ANSWER.RECORD_ID.eq(recordId)));
    }

    private ExamRecordView toView(ExamRecordEntity record) {
        Map<Long, String> answers = listAnswers(record.getId(), record.getEnterpriseId()).stream()
                .collect(Collectors.toMap(
                        ExamAnswerEntity::getPaperQuestionId, ExamAnswerEntity::getAnswer));
        List<ExamQuestionView> questions = listQuestions(
                record.getPaperId(), record.getEnterpriseId()).stream()
                .map(question -> new ExamQuestionView(
                        question.getId(), question.getQuestionType(), question.getContent(),
                        jsonSupport.readOptions(question.getOptionsJson()), question.getScore(),
                        question.getSortOrder(), answers.get(question.getId())))
                .collect(Collectors.toList());
        return new ExamRecordView(
                record.getId(), record.getTaskId(), record.getPlanId(), record.getPaperId(),
                record.getPaperName(), record.getStatus(), record.getStartedAt(),
                record.getDeadlineAt(), record.getSubmittedAt(), record.getPassScore(),
                record.getTotalScore(), record.getScore(), record.getPassed(), questions);
    }

    private String normalizeAnswer(ExamPaperQuestionEntity question, String value) {
        if (question == null) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "答案题目不属于当前试卷");
        }
        String answer = value == null ? null : value.trim().toUpperCase(Locale.ROOT);
        if (answer == null || answer.isEmpty() || answer.length() > 16) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "答案内容不正确");
        }
        if (QUESTION_JUDGMENT.equals(question.getQuestionType())) {
            if (!"TRUE".equals(answer) && !"FALSE".equals(answer)) {
                throw new BusinessException(AppErrorCode.PARAM_INVALID, "判断题答案不正确");
            }
            return answer;
        }
        List<String> options = jsonSupport.readOptions(question.getOptionsJson());
        int index = answer.length() == 1 ? answer.charAt(0) - 'A' : -1;
        if (index < 0 || index >= options.size()) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "单选题答案不正确");
        }
        return answer;
    }

    private LocalDateTime min(LocalDateTime first, LocalDateTime second) {
        return first.isBefore(second) ? first : second;
    }

    private record Owner(Long enterpriseId, Long userId) {
    }
}
