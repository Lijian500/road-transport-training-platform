package me.lj.train.training.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.api.training.TrainingRecordModels.*;
import me.lj.train.api.training.TrainingRecordService;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.page.PageRequest;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.training.mapper.*;
import me.lj.train.training.model.entity.*;
import me.lj.train.training.support.TrainingGuard;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static me.lj.train.training.constant.TrainingConstants.*;
import static me.lj.train.training.constant.TrainingPermissions.*;
import static me.lj.train.training.model.table.PlanTableDef.PLAN;
import static me.lj.train.training.model.table.PlanUserTableDef.PLAN_USER;
import static me.lj.train.training.model.table.PlanCourseTableDef.PLAN_COURSE;
import static me.lj.train.training.model.table.ExamRecordTableDef.EXAM_RECORD;

/** 培训档案只读取发布快照、任务和成绩，不推进计划或考试状态。 */
@DubboService(timeout = 10000, retries = 0)
public class TrainingRecordServiceImpl extends TrainingServiceSupport implements TrainingRecordService {
    private final PlanMapper planMapper;
    private final PlanUserMapper taskMapper;
    private final PlanCourseMapper courseMapper;
    private final ExamRecordMapper examMapper;

    public TrainingRecordServiceImpl(PlatformTransactionManager transactions, PlanMapper planMapper,
            PlanUserMapper taskMapper, PlanCourseMapper courseMapper, ExamRecordMapper examMapper) {
        super(transactions);
        this.planMapper = planMapper;
        this.taskMapper = taskMapper;
        this.courseMapper = courseMapper;
        this.examMapper = examMapper;
    }

    /** 服务端限定本人及企业后，在数据库分页筛选已发布任务。 */
    @Override
    public Result<PageResult<TrainingRecordView>> pageMyRecords(RecordQuery query) {
        return execute(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(STUDENT_PLAN_VIEW);
            if (query == null) throw new BusinessException(AppErrorCode.PARAM_INVALID);
            String keyword = TrainingGuard.optionalText(query.keyword(), "计划名称", 128);
            String completion = TrainingGuard.optionalText(query.completionStatus(), "完成状态", 32);
            String activity = TrainingGuard.optionalText(query.activity(), "任务类型", 32);
            if (completion != null && !Set.of(COMPLETION_COMPLETED, COMPLETION_NOT_COMPLETED).contains(completion)
                    || activity != null && !Set.of("TO_STUDY", "TO_EXAM", "COMPLETED").contains(activity)
                    || query.fromDate() != null && query.toDate() != null && query.fromDate().isAfter(query.toDate())) {
                throw new BusinessException(AppErrorCode.PARAM_INVALID, "档案筛选条件不正确");
            }
            QueryWrapper filter = taskQuery(enterpriseId, UserContext.require().getUserId())
                    .and(PLAN.PLAN_NAME.like(keyword).when(keyword != null))
                    .and(PLAN_USER.COMPLETION_STATUS.eq(completion).when(completion != null));
            if (query.fromDate() != null) filter.and(PLAN.START_AT.ge(query.fromDate().atStartOfDay()));
            if (query.toDate() != null) filter.and(PLAN.START_AT.lt(query.toDate().plusDays(1).atStartOfDay()));
            applyActivity(filter, activity);
            PageRequest page = new PageRequest(query.pageNumber(), query.pageSize());
            Page<PlanUserEntity> tasks = taskMapper.paginate(page.getPageNumber(), page.getPageSize(),
                    filter.orderBy(PLAN.START_AT.desc(), PLAN_USER.ID.desc()));
            return PageResult.of(assemble(tasks.getRecords(), enterpriseId, false), tasks.getTotalRow(), page);
        });
    }

    /** 当前学员的历史任务详情，计划结束后仍可查看。 */
    @Override
    public Result<TrainingRecordView> getMyRecord(Long taskId) {
        return execute(() -> detail(taskId, false));
    }

    /** 统计管理员查询本企业任务，不授予跨企业查询权限。 */
    @Override
    public Result<TrainingRecordView> getAdminRecord(Long taskId) {
        return execute(() -> detail(taskId, true));
    }

    /** 首页统计通过数据库计数，推荐任务最多读取五条。 */
    @Override
    public Result<StudentOverviewView> myOverview() {
        return execute(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(STUDENT_PLAN_VIEW);
            Long userId = UserContext.require().getUserId();
            long total = taskMapper.selectCountByQuery(taskQuery(enterpriseId, userId));
            long study = taskMapper.selectCountByQuery(applyActivity(taskQuery(enterpriseId, userId), "TO_STUDY"));
            long exam = taskMapper.selectCountByQuery(applyActivity(taskQuery(enterpriseId, userId), "TO_EXAM"));
            long completed = taskMapper.selectCountByQuery(applyActivity(taskQuery(enterpriseId, userId), "COMPLETED"));
            List<PlanUserEntity> recent = taskMapper.selectListByQuery(activeTasks(taskQuery(enterpriseId, userId))
                    .and(PLAN_USER.COMPLETION_STATUS.ne(COMPLETION_COMPLETED))
                    .orderBy(PLAN.END_AT.asc(), PLAN_USER.ID.desc()).limit(5));
            return new StudentOverviewView(total, study, exam, completed, assemble(recent, enterpriseId, false));
        });
    }

    /** 联表筛选仅选任务列，所有查询均带租户条件。 */
    private QueryWrapper taskQuery(Long enterpriseId, Long userId) {
        return QueryWrapper.create().select(PLAN_USER.ALL_COLUMNS).from(PLAN_USER)
                .innerJoin(PLAN).on(PLAN.ID.eq(PLAN_USER.PLAN_ID).and(PLAN.ENTERPRISE_ID.eq(PLAN_USER.ENTERPRISE_ID)))
                .where(PLAN_USER.ENTERPRISE_ID.eq(enterpriseId))
                .and(PLAN_USER.USER_ID.eq(userId).when(userId != null))
                .and(PLAN_USER.ASSIGNMENT_STATUS.eq(ASSIGNMENT_ASSIGNED))
                .and(PLAN.STATUS.ne(PLAN_DRAFT)).and(PLAN.DELETED_AT.isNull());
    }

    /** 待办基于时间计算，查询本身不触发计划状态更新。 */
    private QueryWrapper activeTasks(QueryWrapper query) {
        LocalDateTime now = LocalDateTime.now();
        return query.and(PLAN.STATUS.in(PLAN_PUBLISHED, PLAN_IN_PROGRESS))
                .and(PLAN.START_AT.le(now)).and(PLAN.END_AT.gt(now));
    }

    /** 与既有学习、考试准入一致，不增加先学后考的限制。 */
    private QueryWrapper applyActivity(QueryWrapper query, String activity) {
        if ("COMPLETED".equals(activity)) return query.and(PLAN_USER.COMPLETION_STATUS.eq(COMPLETION_COMPLETED));
        if ("TO_STUDY".equals(activity)) return activeTasks(query).and(PLAN_USER.STUDY_STATUS.ne(STUDY_COMPLETED));
        if ("TO_EXAM".equals(activity)) return activeTasks(query).and(PLAN.EXAM_REQUIRED.eq(true))
                .and(PLAN_USER.EXAM_STATUS.in(EXAM_NOT_STARTED, EXAM_IN_PROGRESS));
        return query;
    }

    /** 校验所有权后一次性读取详情所需快照。 */
    private TrainingRecordView detail(Long taskId, boolean administrator) {
        Long enterpriseId = TrainingGuard.requireEnterprisePermission(administrator ? STATISTICS_VIEW : STUDENT_PLAN_VIEW);
        if (taskId == null || taskId <= 0) throw new BusinessException(AppErrorCode.PARAM_INVALID);
        Long userId = administrator ? null : UserContext.require().getUserId();
        PlanUserEntity task = taskMapper.selectOneByQuery(taskQuery(enterpriseId, userId).and(PLAN_USER.ID.eq(taskId)));
        if (task == null) throw new BusinessException(AppErrorCode.STUDENT_TASK_NOT_FOUND);
        TrainingGuard.checkEnterprise(task.getEnterpriseId(), enterpriseId);
        if (userId != null && !userId.equals(task.getUserId())) throw new BusinessException(AppErrorCode.STUDENT_TASK_NOT_FOUND);
        List<TrainingRecordView> records = assemble(List.of(task), enterpriseId, true);
        if (records.isEmpty()) throw new BusinessException(AppErrorCode.STUDENT_TASK_NOT_FOUND);
        return records.get(0);
    }

    /** 批量加载计划、课程和成绩，避免逐条任务访问数据库。 */
    private List<TrainingRecordView> assemble(List<PlanUserEntity> tasks, Long enterpriseId, boolean detail) {
        if (tasks.isEmpty()) return List.of();
        List<Long> planIds = tasks.stream().map(PlanUserEntity::getPlanId).distinct().toList();
        List<Long> taskIds = tasks.stream().map(PlanUserEntity::getId).toList();
        Map<Long, PlanEntity> plans = planMapper.selectListByQuery(QueryWrapper.create()
                .where(PLAN.ENTERPRISE_ID.eq(enterpriseId)).and(PLAN.ID.in(planIds)).and(PLAN.DELETED_AT.isNull()))
                .stream().collect(Collectors.toMap(PlanEntity::getId, Function.identity()));
        Map<Long, List<PlanCourseEntity>> courses = courseMapper.selectListByQuery(QueryWrapper.create()
                .where(PLAN_COURSE.ENTERPRISE_ID.eq(enterpriseId)).and(PLAN_COURSE.PLAN_ID.in(planIds))
                .orderBy(PLAN_COURSE.SORT_ORDER.asc(), PLAN_COURSE.ID.asc())).stream()
                .collect(Collectors.groupingBy(PlanCourseEntity::getPlanId));
        Map<Long, ExamRecordEntity> exams = examMapper.selectListByQuery(QueryWrapper.create()
                .where(EXAM_RECORD.ENTERPRISE_ID.eq(enterpriseId)).and(EXAM_RECORD.TASK_ID.in(taskIds)))
                .stream().collect(Collectors.toMap(ExamRecordEntity::getTaskId, Function.identity()));
        return tasks.stream().filter(task -> plans.containsKey(task.getPlanId())).map(task -> {
            PlanEntity plan = plans.get(task.getPlanId());
            ExamRecordEntity exam = exams.get(task.getId());
            List<RecordCourseView> snapshots = courses.getOrDefault(plan.getId(), List.of()).stream()
                    .map(course -> new RecordCourseView(course.getId(), course.getCourseName(),
                            course.getRequiredDurationSeconds() * 1000L, course.getSortOrder())).toList();
            return new TrainingRecordView(task.getId(), plan.getId(), task.getUserId(), task.getDisplayName(),
                    plan.getPlanName(), displayStatus(plan), plan.getStartAt(), plan.getEndAt(), task.getStudyStatus(),
                    task.getExamStatus(), task.getCompletionStatus(), exam == null ? null : exam.getScore(),
                    exam == null ? null : exam.getPassed(), task.getCompletedAt(),
                    snapshots.stream().mapToLong(RecordCourseView::requiredDurationMillis).sum(), detail ? snapshots : List.of());
        }).toList();
    }

    /** 在定时任务执行前也能展示准确状态，保留取消等明确终态。 */
    private String displayStatus(PlanEntity plan) {
        if (!Set.of(PLAN_PUBLISHED, PLAN_IN_PROGRESS).contains(plan.getStatus())) return plan.getStatus();
        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(plan.getEndAt())) return PLAN_FINISHED;
        return now.isBefore(plan.getStartAt()) ? PLAN_PUBLISHED : PLAN_IN_PROGRESS;
    }
}
