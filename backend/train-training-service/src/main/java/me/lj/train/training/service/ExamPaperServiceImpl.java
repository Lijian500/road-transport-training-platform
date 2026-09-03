package me.lj.train.training.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateWrapper;
import me.lj.train.api.training.ExamModels.CreatePaperCommand;
import me.lj.train.api.training.ExamModels.PaperOptionView;
import me.lj.train.api.training.ExamModels.PaperQuery;
import me.lj.train.api.training.ExamModels.PaperQuestionView;
import me.lj.train.api.training.ExamModels.PaperView;
import me.lj.train.api.training.ExamModels.UpdatePaperCommand;
import me.lj.train.api.training.ExamPaperService;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.page.PageRequest;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.core.util.IdGenerator;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.training.mapper.ExamPaperMapper;
import me.lj.train.training.mapper.ExamPaperQuestionMapper;
import me.lj.train.training.mapper.ExamQuestionMapper;
import me.lj.train.training.model.entity.ExamPaperEntity;
import me.lj.train.training.model.entity.ExamPaperQuestionEntity;
import me.lj.train.training.model.entity.ExamQuestionEntity;
import me.lj.train.training.support.TrainingGuard;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static me.lj.train.training.constant.TrainingConstants.PAPER_DRAFT;
import static me.lj.train.training.constant.TrainingConstants.PAPER_ENABLED;
import static me.lj.train.training.constant.TrainingConstants.PAPER_SELECTION_MANUAL;
import static me.lj.train.training.constant.TrainingConstants.PAPER_SELECTION_RANDOM;
import static me.lj.train.training.constant.TrainingConstants.QUESTION_ENABLED;
import static me.lj.train.training.constant.TrainingPermissions.EXAM_MANAGE;
import static me.lj.train.training.constant.TrainingPermissions.EXAM_VIEW;
import static me.lj.train.training.constant.TrainingPermissions.PLAN_CREATE;
import static me.lj.train.training.constant.TrainingPermissions.PLAN_UPDATE;
import static me.lj.train.training.model.table.ExamPaperQuestionTableDef.EXAM_PAPER_QUESTION;
import static me.lj.train.training.model.table.ExamPaperTableDef.EXAM_PAPER;
import static me.lj.train.training.model.table.ExamQuestionTableDef.EXAM_QUESTION;

/** 试卷草稿、手工选题、随机补齐和启用固化实现。 */
@DubboService(timeout = 10000, retries = 0)
public class ExamPaperServiceImpl extends TrainingServiceSupport implements ExamPaperService {

    private static final int MAX_QUESTIONS = 200;

    private final ExamPaperMapper paperMapper;
    private final ExamQuestionMapper questionMapper;
    private final ExamPaperQuestionMapper paperQuestionMapper;
    private final ExamJsonSupport jsonSupport;

    public ExamPaperServiceImpl(
            PlatformTransactionManager transactionManager,
            ExamPaperMapper paperMapper,
            ExamQuestionMapper questionMapper,
            ExamPaperQuestionMapper paperQuestionMapper,
            ExamJsonSupport jsonSupport) {
        super(transactionManager);
        this.paperMapper = paperMapper;
        this.questionMapper = questionMapper;
        this.paperQuestionMapper = paperQuestionMapper;
        this.jsonSupport = jsonSupport;
    }

    @Override
    public Result<PageResult<PaperView>> page(PaperQuery query) {
        return execute(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(EXAM_VIEW);
            PageRequest request = query.toPageRequest();
            String keyword = trim(query.keyword());
            String status = optionalStatus(query.status());
            Page<ExamPaperEntity> page = paperMapper.paginate(
                    request.getPageNumber(), request.getPageSize(), QueryWrapper.create()
                            .where(EXAM_PAPER.ENTERPRISE_ID.eq(enterpriseId))
                            .and(EXAM_PAPER.DELETED_AT.isNull())
                            .and(EXAM_PAPER.PAPER_NAME.like(keyword).when(hasText(keyword)))
                            .and(EXAM_PAPER.STATUS.eq(status).when(status != null))
                            .orderBy(EXAM_PAPER.CREATED_AT.desc()));
            List<PaperView> records = page.getRecords().stream()
                    .map(this::toView).collect(Collectors.toList());
            return PageResult.of(records, page.getTotalRow(), request);
        });
    }

    @Override
    public Result<PaperView> create(CreatePaperCommand command) {
        return executeTransactional(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(EXAM_MANAGE);
            PaperValues values = validate(command.name(), command.description(),
                    command.durationMinutes(), command.passScore(), command.questionScore(),
                    command.manualQuestionIds(), command.randomFillCount());
            Long operatorId = UserContext.require().getUserId();
            ExamPaperEntity paper = new ExamPaperEntity();
            paper.setId(IdGenerator.nextId());
            paper.setEnterpriseId(enterpriseId);
            apply(paper, values);
            paper.setStatus(PAPER_DRAFT);
            paper.setCreatedBy(operatorId);
            paper.setUpdatedBy(operatorId);
            paperMapper.insertSelective(paper);
            replaceManualQuestions(paper, values.manualQuestionIds(), operatorId);
            return toView(requirePaper(paper.getId(), enterpriseId, false));
        });
    }

    @Override
    public Result<PaperView> get(Long id) {
        return execute(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(EXAM_VIEW);
            return toView(requirePaper(id, enterpriseId, false));
        });
    }

    @Override
    public Result<PaperView> update(UpdatePaperCommand command) {
        return executeTransactional(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(EXAM_MANAGE);
            ExamPaperEntity paper = requirePaper(command.id(), enterpriseId, true);
            requireDraft(paper);
            PaperValues values = validate(command.name(), command.description(),
                    command.durationMinutes(), command.passScore(), command.questionScore(),
                    command.manualQuestionIds(), command.randomFillCount());
            Long operatorId = UserContext.require().getUserId();
            UpdateWrapper<ExamPaperEntity> update = UpdateWrapper.of(ExamPaperEntity.class)
                    .set(EXAM_PAPER.PAPER_NAME, values.name())
                    .set(EXAM_PAPER.DESCRIPTION, values.description())
                    .set(EXAM_PAPER.DURATION_MINUTES, values.durationMinutes())
                    .set(EXAM_PAPER.PASS_SCORE, values.passScore())
                    .set(EXAM_PAPER.QUESTION_SCORE, values.questionScore())
                    .set(EXAM_PAPER.MANUAL_QUESTION_COUNT, values.manualQuestionIds().size())
                    .set(EXAM_PAPER.RANDOM_QUESTION_COUNT, values.randomFillCount())
                    .set(EXAM_PAPER.TOTAL_SCORE, values.totalScore())
                    .set(EXAM_PAPER.UPDATED_BY, operatorId);
            paperMapper.updateByCondition(update.toEntity(), EXAM_PAPER.ID.eq(paper.getId())
                    .and(EXAM_PAPER.ENTERPRISE_ID.eq(enterpriseId))
                    .and(EXAM_PAPER.STATUS.eq(PAPER_DRAFT))
                    .and(EXAM_PAPER.DELETED_AT.isNull()));
            ExamPaperEntity updated = requirePaper(paper.getId(), enterpriseId, true);
            replaceManualQuestions(updated, values.manualQuestionIds(), operatorId);
            return toView(requirePaper(paper.getId(), enterpriseId, false));
        });
    }

    @Override
    public Result<?> delete(Long id) {
        return executeVoidTransactional(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(EXAM_MANAGE);
            ExamPaperEntity paper = requirePaper(id, enterpriseId, true);
            requireDraft(paper);
            paperQuestionMapper.deleteByQuery(QueryWrapper.create()
                    .where(EXAM_PAPER_QUESTION.ENTERPRISE_ID.eq(enterpriseId))
                    .and(EXAM_PAPER_QUESTION.PAPER_ID.eq(paper.getId())));
            Long operatorId = UserContext.require().getUserId();
            ExamPaperEntity update = new ExamPaperEntity();
            update.setDeletedBy(operatorId);
            update.setDeletedAt(LocalDateTime.now());
            update.setUpdatedBy(operatorId);
            paperMapper.updateByCondition(update, EXAM_PAPER.ID.eq(paper.getId())
                    .and(EXAM_PAPER.ENTERPRISE_ID.eq(enterpriseId))
                    .and(EXAM_PAPER.STATUS.eq(PAPER_DRAFT))
                    .and(EXAM_PAPER.DELETED_AT.isNull()));
        });
    }

    @Override
    public Result<PaperView> enable(Long id) {
        return executeTransactional(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(EXAM_MANAGE);
            ExamPaperEntity paper = requirePaper(id, enterpriseId, true);
            requireDraft(paper);
            List<ExamPaperQuestionEntity> draftQuestions = listPaperQuestions(
                    paper.getId(), enterpriseId);
            List<Long> manualIds = draftQuestions.stream()
                    .filter(item -> PAPER_SELECTION_MANUAL.equals(item.getSelectionType()))
                    .map(ExamPaperQuestionEntity::getSourceQuestionId)
                    .collect(Collectors.toList());
            if (manualIds.size() != paper.getManualQuestionCount()) {
                throw new BusinessException(AppErrorCode.EXAM_PAPER_ENABLE_INVALID,
                        "试卷手工题数量与草稿配置不一致");
            }
            List<ExamQuestionEntity> manualQuestions = loadEnabledQuestions(
                    manualIds, enterpriseId, AppErrorCode.EXAM_PAPER_ENABLE_INVALID);
            List<ExamQuestionEntity> randomCandidates = questionMapper.selectListByQuery(
                    QueryWrapper.create().where(EXAM_QUESTION.ENTERPRISE_ID.eq(enterpriseId))
                            .and(EXAM_QUESTION.STATUS.eq(QUESTION_ENABLED))
                            .and(EXAM_QUESTION.DELETED_AT.isNull())
                            .and(EXAM_QUESTION.ID.notIn(manualIds).when(!manualIds.isEmpty())));
            if (randomCandidates.size() < paper.getRandomQuestionCount()) {
                throw new BusinessException(AppErrorCode.EXAM_PAPER_ENABLE_INVALID,
                        "题库中可用于随机补齐的启用题目不足");
            }
            Collections.shuffle(randomCandidates);
            List<ExamQuestionEntity> frozen = new ArrayList<ExamQuestionEntity>(manualQuestions);
            frozen.addAll(randomCandidates.subList(0, paper.getRandomQuestionCount()));
            if (frozen.isEmpty() || paper.getPassScore() > paper.getTotalScore()) {
                throw new BusinessException(AppErrorCode.EXAM_PAPER_ENABLE_INVALID);
            }
            Long operatorId = UserContext.require().getUserId();
            paperQuestionMapper.deleteByQuery(QueryWrapper.create()
                    .where(EXAM_PAPER_QUESTION.ENTERPRISE_ID.eq(enterpriseId))
                    .and(EXAM_PAPER_QUESTION.PAPER_ID.eq(paper.getId())));
            for (int index = 0; index < frozen.size(); index++) {
                String selectionType = index < manualQuestions.size()
                        ? PAPER_SELECTION_MANUAL : PAPER_SELECTION_RANDOM;
                insertSnapshot(paper, frozen.get(index), selectionType, index + 1, operatorId);
            }
            LocalDateTime now = LocalDateTime.now();
            UpdateWrapper<ExamPaperEntity> enable = UpdateWrapper.of(ExamPaperEntity.class)
                    .set(EXAM_PAPER.STATUS, PAPER_ENABLED)
                    .set(EXAM_PAPER.ENABLED_BY, operatorId)
                    .set(EXAM_PAPER.ENABLED_AT, now)
                    .set(EXAM_PAPER.UPDATED_BY, operatorId);
            paperMapper.updateByCondition(enable.toEntity(), EXAM_PAPER.ID.eq(paper.getId())
                    .and(EXAM_PAPER.ENTERPRISE_ID.eq(enterpriseId))
                    .and(EXAM_PAPER.STATUS.eq(PAPER_DRAFT))
                    .and(EXAM_PAPER.DELETED_AT.isNull()));
            return toView(requirePaper(paper.getId(), enterpriseId, false));
        });
    }

    @Override
    public Result<List<PaperOptionView>> listEnabled(String keyword) {
        return execute(() -> {
            Long enterpriseId = TrainingGuard.requireEnterpriseAnyPermission(
                    EXAM_VIEW, EXAM_MANAGE, PLAN_CREATE, PLAN_UPDATE);
            String normalized = trim(keyword);
            return paperMapper.selectListByQuery(QueryWrapper.create()
                            .where(EXAM_PAPER.ENTERPRISE_ID.eq(enterpriseId))
                            .and(EXAM_PAPER.STATUS.eq(PAPER_ENABLED))
                            .and(EXAM_PAPER.DELETED_AT.isNull())
                            .and(EXAM_PAPER.PAPER_NAME.like(normalized)
                                    .when(hasText(normalized)))
                            .orderBy(EXAM_PAPER.PAPER_NAME.asc(), EXAM_PAPER.ID.asc()))
                    .stream().map(paper -> new PaperOptionView(
                            paper.getId(), paper.getPaperName(), paper.getDurationMinutes(),
                            paper.getPassScore(), paper.getTotalScore(),
                            paper.getManualQuestionCount() + paper.getRandomQuestionCount()))
                    .collect(Collectors.toList());
        });
    }

    private PaperValues validate(
            String nameValue,
            String descriptionValue,
            int durationMinutes,
            int passScore,
            int questionScore,
            List<Long> manualQuestionIds,
            int randomFillCount) {
        String name = TrainingGuard.requireText(nameValue, "试卷名称", 128);
        String description = TrainingGuard.optionalText(descriptionValue, "试卷说明", 1000);
        List<Long> manualIds = normalizeIds(manualQuestionIds);
        int questionCount = manualIds.size() + randomFillCount;
        if (durationMinutes < 1 || durationMinutes > 480) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "考试时长须为1至480分钟");
        }
        if (questionScore < 1 || questionScore > 100) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "每题分值须为1至100分");
        }
        if (randomFillCount < 0 || questionCount < 1 || questionCount > MAX_QUESTIONS) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID,
                    "试卷题目总数须为1至" + MAX_QUESTIONS + "题");
        }
        int totalScore = Math.multiplyExact(questionCount, questionScore);
        if (passScore < 1 || passScore > totalScore) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "及格分须在1至试卷总分之间");
        }
        return new PaperValues(name, description, durationMinutes, passScore, questionScore,
                manualIds, randomFillCount, totalScore);
    }

    private void apply(ExamPaperEntity paper, PaperValues values) {
        paper.setPaperName(values.name());
        paper.setDescription(values.description());
        paper.setDurationMinutes(values.durationMinutes());
        paper.setPassScore(values.passScore());
        paper.setQuestionScore(values.questionScore());
        paper.setManualQuestionCount(values.manualQuestionIds().size());
        paper.setRandomQuestionCount(values.randomFillCount());
        paper.setTotalScore(values.totalScore());
    }

    private void replaceManualQuestions(
            ExamPaperEntity paper, List<Long> manualIds, Long operatorId) {
        List<ExamQuestionEntity> questions = loadEnabledQuestions(
                manualIds, paper.getEnterpriseId(), AppErrorCode.EXAM_QUESTION_NOT_FOUND);
        paperQuestionMapper.deleteByQuery(QueryWrapper.create()
                .where(EXAM_PAPER_QUESTION.ENTERPRISE_ID.eq(paper.getEnterpriseId()))
                .and(EXAM_PAPER_QUESTION.PAPER_ID.eq(paper.getId())));
        for (int index = 0; index < questions.size(); index++) {
            insertSnapshot(paper, questions.get(index), PAPER_SELECTION_MANUAL,
                    index + 1, operatorId);
        }
    }

    private List<ExamQuestionEntity> loadEnabledQuestions(
            List<Long> ids, Long enterpriseId, AppErrorCode errorCode) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, ExamQuestionEntity> questionMap = questionMapper.selectListByQuery(
                        QueryWrapper.create().where(EXAM_QUESTION.ENTERPRISE_ID.eq(enterpriseId))
                                .and(EXAM_QUESTION.ID.in(ids))
                                .and(EXAM_QUESTION.STATUS.eq(QUESTION_ENABLED))
                                .and(EXAM_QUESTION.DELETED_AT.isNull()))
                .stream().collect(Collectors.toMap(ExamQuestionEntity::getId, Function.identity()));
        if (questionMap.size() != ids.size()) {
            throw new BusinessException(errorCode, "试卷只能选择当前组织已启用的题目");
        }
        return ids.stream().map(questionMap::get).collect(Collectors.toList());
    }

    private void insertSnapshot(
            ExamPaperEntity paper,
            ExamQuestionEntity question,
            String selectionType,
            int sortOrder,
            Long operatorId) {
        ExamPaperQuestionEntity snapshot = new ExamPaperQuestionEntity();
        snapshot.setId(IdGenerator.nextId());
        snapshot.setEnterpriseId(paper.getEnterpriseId());
        snapshot.setPaperId(paper.getId());
        snapshot.setSourceQuestionId(question.getId());
        snapshot.setSelectionType(selectionType);
        snapshot.setQuestionType(question.getQuestionType());
        snapshot.setContent(question.getContent());
        snapshot.setOptionsJson(question.getOptionsJson());
        snapshot.setCorrectAnswer(question.getCorrectAnswer());
        snapshot.setAnalysis(question.getAnalysis());
        snapshot.setScore(paper.getQuestionScore());
        snapshot.setSortOrder(sortOrder);
        snapshot.setCreatedBy(operatorId);
        snapshot.setUpdatedBy(operatorId);
        paperQuestionMapper.insertSelective(snapshot);
    }

    private ExamPaperEntity requirePaper(Long id, Long enterpriseId, boolean lock) {
        QueryWrapper wrapper = QueryWrapper.create().where(EXAM_PAPER.ID.eq(id))
                .and(EXAM_PAPER.ENTERPRISE_ID.eq(enterpriseId))
                .and(EXAM_PAPER.DELETED_AT.isNull());
        if (lock) {
            wrapper.forUpdate();
        }
        ExamPaperEntity paper = id == null ? null : paperMapper.selectOneByQuery(wrapper);
        if (paper == null) {
            throw new BusinessException(AppErrorCode.EXAM_PAPER_NOT_FOUND);
        }
        TrainingGuard.checkEnterprise(paper.getEnterpriseId(), enterpriseId);
        return paper;
    }

    private void requireDraft(ExamPaperEntity paper) {
        if (!PAPER_DRAFT.equals(paper.getStatus())) {
            throw new BusinessException(AppErrorCode.EXAM_PAPER_STATE_INVALID,
                    "只有草稿试卷可以编辑或删除");
        }
    }

    private List<ExamPaperQuestionEntity> listPaperQuestions(Long paperId, Long enterpriseId) {
        return paperQuestionMapper.selectListByQuery(QueryWrapper.create()
                .where(EXAM_PAPER_QUESTION.ENTERPRISE_ID.eq(enterpriseId))
                .and(EXAM_PAPER_QUESTION.PAPER_ID.eq(paperId))
                .orderBy(EXAM_PAPER_QUESTION.SORT_ORDER.asc(), EXAM_PAPER_QUESTION.ID.asc()));
    }

    private PaperView toView(ExamPaperEntity paper) {
        List<PaperQuestionView> questions = listPaperQuestions(
                paper.getId(), paper.getEnterpriseId()).stream()
                .map(item -> new PaperQuestionView(
                        item.getId(), item.getSourceQuestionId(), item.getSelectionType(),
                        item.getQuestionType(), item.getContent(),
                        jsonSupport.readOptions(item.getOptionsJson()), item.getCorrectAnswer(),
                        item.getAnalysis(), item.getScore(), item.getSortOrder()))
                .collect(Collectors.toList());
        return new PaperView(
                paper.getId(), paper.getPaperName(), paper.getDescription(),
                paper.getDurationMinutes(), paper.getPassScore(), paper.getQuestionScore(),
                paper.getManualQuestionCount(), paper.getRandomQuestionCount(),
                paper.getTotalScore(), paper.getStatus(), questions, paper.getEnabledAt(),
                paper.getCreatedAt(), paper.getUpdatedAt());
    }

    private List<Long> normalizeIds(List<Long> values) {
        List<Long> ids = values == null ? Collections.emptyList()
                : values.stream().filter(item -> item != null)
                        .collect(Collectors.toList());
        if (new LinkedHashSet<Long>(ids).size() != ids.size()) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "试卷题目不能重复");
        }
        return ids;
    }

    private String optionalStatus(String value) {
        if (!hasText(value)) {
            return null;
        }
        String status = value.trim().toUpperCase(Locale.ROOT);
        if (!PAPER_DRAFT.equals(status) && !PAPER_ENABLED.equals(status)) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "试卷状态不正确");
        }
        return status;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private record PaperValues(
            String name,
            String description,
            int durationMinutes,
            int passScore,
            int questionScore,
            List<Long> manualQuestionIds,
            int randomFillCount,
            int totalScore) {
    }
}
