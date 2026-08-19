package me.lj.train.training.service;

import com.mybatisflex.core.update.UpdateWrapper;
import me.lj.train.training.mapper.PlanMapper;
import me.lj.train.training.model.entity.PlanEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static me.lj.train.training.constant.TrainingConstants.PLAN_FINISHED;
import static me.lj.train.training.constant.TrainingConstants.PLAN_IN_PROGRESS;
import static me.lj.train.training.constant.TrainingConstants.PLAN_PUBLISHED;
import static me.lj.train.training.model.table.PlanTableDef.PLAN;

/**
 * 根据计划起止时间幂等推进计划生命周期。
 */
@Component
public class PlanLifecycleService {

    private static final long SYSTEM_OPERATOR_ID = 0L;

    private final PlanMapper planMapper;

    public PlanLifecycleService(PlanMapper planMapper) {
        this.planMapper = planMapper;
    }

    @Scheduled(fixedDelayString = "${training.plan.lifecycle-delay-ms:60000}")
    public void refreshStatuses() {
        refreshStatuses(null, null);
    }

    /** 在学习请求链路中只推进当前企业的指定计划，避免全表状态刷新。 */
    public void refreshStatus(Long enterpriseId, Long planId) {
        if (enterpriseId == null || planId == null) {
            return;
        }
        refreshStatuses(enterpriseId, planId);
    }

    private void refreshStatuses(Long enterpriseId, Long planId) {
        LocalDateTime now = LocalDateTime.now();
        UpdateWrapper<PlanEntity> starting = UpdateWrapper.of(PlanEntity.class)
                .set(PLAN.STATUS, PLAN_IN_PROGRESS)
                .set(PLAN.UPDATED_BY, SYSTEM_OPERATOR_ID);
        planMapper.updateByCondition(starting.toEntity(),
                PLAN.STATUS.eq(PLAN_PUBLISHED)
                        .and(PLAN.START_AT.le(now))
                        .and(PLAN.END_AT.gt(now))
                        .and(PLAN.ENTERPRISE_ID.eq(enterpriseId).when(enterpriseId != null))
                        .and(PLAN.ID.eq(planId).when(planId != null))
                        .and(PLAN.DELETED_AT.isNull()));

        UpdateWrapper<PlanEntity> finishing = UpdateWrapper.of(PlanEntity.class)
                .set(PLAN.STATUS, PLAN_FINISHED)
                .set(PLAN.UPDATED_BY, SYSTEM_OPERATOR_ID);
        planMapper.updateByCondition(finishing.toEntity(),
                PLAN.STATUS.in(PLAN_PUBLISHED, PLAN_IN_PROGRESS)
                        .and(PLAN.END_AT.le(now))
                        .and(PLAN.ENTERPRISE_ID.eq(enterpriseId).when(enterpriseId != null))
                        .and(PLAN.ID.eq(planId).when(planId != null))
                        .and(PLAN.DELETED_AT.isNull()));
    }
}
