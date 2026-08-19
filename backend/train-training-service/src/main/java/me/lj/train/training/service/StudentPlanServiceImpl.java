package me.lj.train.training.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.api.training.PlanModels.StudentPlanQuery;
import me.lj.train.api.training.PlanModels.StudentPlanView;
import me.lj.train.api.training.StudentPlanService;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.page.PageRequest;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.training.mapper.PlanMapper;
import me.lj.train.training.mapper.PlanUserMapper;
import me.lj.train.training.model.entity.PlanEntity;
import me.lj.train.training.model.entity.PlanUserEntity;
import me.lj.train.training.support.TrainingGuard;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static me.lj.train.training.constant.TrainingConstants.PLAN_CANCELLED;
import static me.lj.train.training.constant.TrainingConstants.PLAN_DRAFT;
import static me.lj.train.training.constant.TrainingConstants.PLAN_FINISHED;
import static me.lj.train.training.constant.TrainingConstants.PLAN_IN_PROGRESS;
import static me.lj.train.training.constant.TrainingConstants.PLAN_PUBLISHED;
import static me.lj.train.training.constant.TrainingPermissions.STUDENT_PLAN_VIEW;
import static me.lj.train.training.model.table.PlanTableDef.PLAN;
import static me.lj.train.training.model.table.PlanUserTableDef.PLAN_USER;

/**
 * 当前登录学员培训任务查询RPC实现。
 */
@DubboService(timeout = 8000, retries = 0)
public class StudentPlanServiceImpl extends TrainingServiceSupport implements StudentPlanService {

    private final PlanMapper planMapper;
    private final PlanUserMapper planUserMapper;
    private final PlanLifecycleService lifecycleService;
    private final PlanViewAssembler viewAssembler;

    public StudentPlanServiceImpl(
            PlatformTransactionManager transactionManager,
            PlanMapper planMapper,
            PlanUserMapper planUserMapper,
            PlanLifecycleService lifecycleService,
            PlanViewAssembler viewAssembler) {
        super(transactionManager);
        this.planMapper = planMapper;
        this.planUserMapper = planUserMapper;
        this.lifecycleService = lifecycleService;
        this.viewAssembler = viewAssembler;
    }

    @Override
    public Result<PageResult<StudentPlanView>> pageMyPlans(StudentPlanQuery query) {
        return execute(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(STUDENT_PLAN_VIEW);
            Long userId = UserContext.require().getUserId();
            lifecycleService.refreshStatuses();
            PageRequest request = query.toPageRequest();
            String status = normalizeStatus(query.status());
            List<PlanUserEntity> tasks = planUserMapper.selectListByQuery(QueryWrapper.create()
                    .where(PLAN_USER.ENTERPRISE_ID.eq(enterpriseId))
                    .and(PLAN_USER.USER_ID.eq(userId)));
            if (tasks.isEmpty()) {
                return PageResult.of(Collections.emptyList(), 0L, request);
            }
            Map<Long, PlanUserEntity> taskMap = tasks.stream()
                    .collect(Collectors.toMap(PlanUserEntity::getPlanId, Function.identity()));
            Page<PlanEntity> page = planMapper.paginate(
                    request.getPageNumber(), request.getPageSize(), QueryWrapper.create()
                            .where(PLAN.ENTERPRISE_ID.eq(enterpriseId))
                            .and(PLAN.ID.in(taskMap.keySet()))
                            .and(PLAN.STATUS.ne(PLAN_DRAFT))
                            .and(PLAN.STATUS.eq(status).when(status != null))
                            .and(PLAN.DELETED_AT.isNull())
                            .orderBy(PLAN.START_AT.desc(), PLAN.ID.desc()));
            List<StudentPlanView> records = page.getRecords().stream()
                    .map(plan -> viewAssembler.toStudentPlanView(
                            plan, taskMap.get(plan.getId()), false))
                    .collect(Collectors.toList());
            return PageResult.of(records, page.getTotalRow(), request);
        });
    }

    @Override
    public Result<StudentPlanView> getMyPlan(Long planId) {
        return execute(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(STUDENT_PLAN_VIEW);
            Long userId = UserContext.require().getUserId();
            lifecycleService.refreshStatuses();
            PlanUserEntity task = planId == null ? null : planUserMapper.selectOneByQuery(
                    QueryWrapper.create()
                            .where(PLAN_USER.ENTERPRISE_ID.eq(enterpriseId))
                            .and(PLAN_USER.PLAN_ID.eq(planId))
                            .and(PLAN_USER.USER_ID.eq(userId)));
            if (task == null) {
                throw new BusinessException(AppErrorCode.STUDENT_TASK_NOT_FOUND);
            }
            TrainingGuard.checkEnterprise(task.getEnterpriseId(), enterpriseId);
            if (!userId.equals(task.getUserId())) {
                throw new BusinessException(AppErrorCode.STUDENT_TASK_NOT_FOUND);
            }
            PlanEntity plan = planMapper.selectOneByQuery(QueryWrapper.create()
                    .where(PLAN.ID.eq(planId))
                    .and(PLAN.ENTERPRISE_ID.eq(enterpriseId))
                    .and(PLAN.STATUS.ne(PLAN_DRAFT))
                    .and(PLAN.DELETED_AT.isNull()));
            if (plan == null) {
                throw new BusinessException(AppErrorCode.STUDENT_TASK_NOT_FOUND);
            }
            TrainingGuard.checkEnterprise(plan.getEnterpriseId(), enterpriseId);
            return viewAssembler.toStudentPlanView(plan, task, true);
        });
    }

    private String normalizeStatus(String value) {
        String status = value == null ? null : value.trim();
        if (status == null || status.isEmpty()) {
            return null;
        }
        if (!PLAN_PUBLISHED.equals(status) && !PLAN_IN_PROGRESS.equals(status)
                && !PLAN_FINISHED.equals(status) && !PLAN_CANCELLED.equals(status)) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "计划状态不正确");
        }
        return status;
    }
}
