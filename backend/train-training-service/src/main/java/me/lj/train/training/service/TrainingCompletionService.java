package me.lj.train.training.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateWrapper;
import me.lj.train.training.mapper.PlanMapper;
import me.lj.train.training.mapper.PlanUserMapper;
import me.lj.train.training.model.entity.PlanEntity;
import me.lj.train.training.model.entity.PlanUserEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

import static me.lj.train.training.constant.TrainingConstants.COMPLETION_COMPLETED;
import static me.lj.train.training.constant.TrainingConstants.EXAM_PASSED;
import static me.lj.train.training.constant.TrainingConstants.STUDY_COMPLETED;
import static me.lj.train.training.model.table.PlanTableDef.PLAN;
import static me.lj.train.training.model.table.PlanUserTableDef.PLAN_USER;

/** 统一判断学习与考试条件并更新培训完成状态。 */
@Component
public class TrainingCompletionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrainingCompletionService.class);

    private final TransactionTemplate planTransaction;
    private final PlanMapper planMapper;
    private final PlanUserMapper planUserMapper;

    public TrainingCompletionService(PlanMapper planMapper, PlanUserMapper planUserMapper,
                                     PlatformTransactionManager transactionManager) {
        this.planTransaction = new TransactionTemplate(transactionManager);
        this.planTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.planMapper = planMapper;
        this.planUserMapper = planUserMapper;
    }

    /** 调用方事务内重算单个培训任务，重复调用不会覆盖首次完成时间。 */
    public void recalculate(Long taskId, Long enterpriseId, LocalDateTime occurredAt) {
        PlanUserEntity task = planUserMapper.selectOneByQuery(QueryWrapper.create()
                .where(PLAN_USER.ID.eq(taskId))
                .and(PLAN_USER.ENTERPRISE_ID.eq(enterpriseId)).forUpdate());
        if (task == null) {
            return;
        }
        if (COMPLETION_COMPLETED.equals(task.getCompletionStatus())) {
            refreshPlanAfterCommit(enterpriseId, task.getPlanId());
            return;
        }
        PlanEntity plan = planMapper.selectOneByQuery(QueryWrapper.create()
                .where(PLAN.ID.eq(task.getPlanId()))
                .and(PLAN.ENTERPRISE_ID.eq(enterpriseId))
                .and(PLAN.DELETED_AT.isNull()));
        boolean examSatisfied = plan != null
                && (!plan.isExamRequired() || EXAM_PASSED.equals(task.getExamStatus()));
        if (plan == null || !STUDY_COMPLETED.equals(task.getStudyStatus()) || !examSatisfied) {
            return;
        }
        LocalDateTime completedAt = occurredAt == null ? LocalDateTime.now() : occurredAt;
        PlanUserEntity update = UpdateWrapper.of(PlanUserEntity.class)
                .set(PLAN_USER.COMPLETION_STATUS, COMPLETION_COMPLETED)
                .set(PLAN_USER.COMPLETED_AT, completedAt)
                .set(PLAN_USER.UPDATED_BY, 0L).toEntity();
        planUserMapper.updateByCondition(update, PLAN_USER.ID.eq(taskId)
                .and(PLAN_USER.ENTERPRISE_ID.eq(enterpriseId))
                .and(PLAN_USER.COMPLETION_STATUS.ne(COMPLETION_COMPLETED)));
        refreshPlanAfterCommit(enterpriseId, task.getPlanId());
    }

    /** 释放学员任务锁后再检查全员结业，避免交卷事务与计划更新互相等待。 */
    private void refreshPlanAfterCommit(Long enterpriseId, Long planId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    refreshCompletedPlan(enterpriseId, planId);
                }
            });
        } else {
            refreshCompletedPlan(enterpriseId, planId);
        }
    }

    /** 提交回调仍绑定原事务资源，必须开启新事务；失败交由生命周期定时任务补偿。 */
    private void refreshCompletedPlan(Long enterpriseId, Long planId) {
        try {
            planTransaction.executeWithoutResult(status -> planMapper.finishCompletedPlans(enterpriseId, planId));
        } catch (RuntimeException exception) {
            LOGGER.warn("结业已提交，计划状态等待定时补偿，enterpriseId={}, planId={}", enterpriseId, planId, exception);
        }
    }

}
