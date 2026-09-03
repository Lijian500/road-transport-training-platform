package me.lj.train.training.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateWrapper;
import me.lj.train.training.mapper.PlanMapper;
import me.lj.train.training.mapper.PlanUserMapper;
import me.lj.train.training.model.entity.PlanEntity;
import me.lj.train.training.model.entity.PlanUserEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static me.lj.train.training.constant.TrainingConstants.COMPLETION_COMPLETED;
import static me.lj.train.training.constant.TrainingConstants.EXAM_PASSED;
import static me.lj.train.training.constant.TrainingConstants.STUDY_COMPLETED;
import static me.lj.train.training.model.table.PlanTableDef.PLAN;
import static me.lj.train.training.model.table.PlanUserTableDef.PLAN_USER;

/** 统一判断学习与考试条件并更新培训完成状态。 */
@Component
public class TrainingCompletionService {

    private final PlanMapper planMapper;
    private final PlanUserMapper planUserMapper;

    public TrainingCompletionService(PlanMapper planMapper, PlanUserMapper planUserMapper) {
        this.planMapper = planMapper;
        this.planUserMapper = planUserMapper;
    }

    /** 调用方事务内重算单个培训任务，重复调用不会覆盖首次完成时间。 */
    public void recalculate(Long taskId, Long enterpriseId, LocalDateTime occurredAt) {
        PlanUserEntity task = planUserMapper.selectOneByQuery(QueryWrapper.create()
                .where(PLAN_USER.ID.eq(taskId))
                .and(PLAN_USER.ENTERPRISE_ID.eq(enterpriseId)).forUpdate());
        if (task == null || COMPLETION_COMPLETED.equals(task.getCompletionStatus())) {
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
    }
}
