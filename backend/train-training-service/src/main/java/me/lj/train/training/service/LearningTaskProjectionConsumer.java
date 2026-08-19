package me.lj.train.training.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateWrapper;
import me.lj.train.api.training.LearningTaskEvents;
import me.lj.train.api.training.LearningTaskEvents.LearningTaskEvent;
import me.lj.train.common.core.util.IdGenerator;
import me.lj.train.training.mapper.MqConsumeLogMapper;
import me.lj.train.training.mapper.PlanMapper;
import me.lj.train.training.mapper.PlanUserMapper;
import me.lj.train.training.model.entity.MqConsumeLogEntity;
import me.lj.train.training.model.entity.PlanEntity;
import me.lj.train.training.model.entity.PlanUserEntity;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static me.lj.train.training.constant.TrainingConstants.ASSIGNMENT_ASSIGNED;
import static me.lj.train.training.constant.TrainingConstants.COMPLETION_COMPLETED;
import static me.lj.train.training.constant.TrainingConstants.STUDY_COMPLETED;
import static me.lj.train.training.constant.TrainingConstants.STUDY_IN_PROGRESS;
import static me.lj.train.training.constant.TrainingConstants.STUDY_NOT_STARTED;
import static me.lj.train.training.model.table.MqConsumeLogTableDef.MQ_CONSUME_LOG;
import static me.lj.train.training.model.table.PlanTableDef.PLAN;
import static me.lj.train.training.model.table.PlanUserTableDef.PLAN_USER;

/**
 * 将学习服务事件幂等投影到培训任务汇总状态。
 */
@Component
@ConditionalOnProperty(prefix = "learning.mq", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class LearningTaskProjectionConsumer {

    private static final String CONSUMER_NAME = "training-learning-task-projection-v1";

    private final MqConsumeLogMapper consumeLogMapper;
    private final PlanUserMapper planUserMapper;
    private final PlanMapper planMapper;
    private final TransactionTemplate transactionTemplate;

    public LearningTaskProjectionConsumer(
            MqConsumeLogMapper consumeLogMapper,
            PlanUserMapper planUserMapper,
            PlanMapper planMapper,
            PlatformTransactionManager transactionManager) {
        this.consumeLogMapper = consumeLogMapper;
        this.planUserMapper = planUserMapper;
        this.planMapper = planMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @RabbitListener(queues = LearningTaskEvents.QUEUE)
    public void consume(LearningTaskEvent event) {
        transactionTemplate.executeWithoutResult(status -> apply(event));
    }

    private void apply(LearningTaskEvent event) {
        if (event == null || event.eventId() == null || event.taskId() == null) {
            throw new IllegalArgumentException("学习任务事件字段不完整");
        }
        long consumed = consumeLogMapper.selectCountByQuery(QueryWrapper.create()
                .where(MQ_CONSUME_LOG.CONSUMER_NAME.eq(CONSUMER_NAME))
                .and(MQ_CONSUME_LOG.EVENT_ID.eq(event.eventId())));
        if (consumed > 0) {
            return;
        }
        PlanUserEntity task = planUserMapper.selectOneByQuery(QueryWrapper.create()
                .where(PLAN_USER.ID.eq(event.taskId()))
                .and(PLAN_USER.ENTERPRISE_ID.eq(event.enterpriseId()))
                .and(PLAN_USER.USER_ID.eq(event.userId()))
                .and(PLAN_USER.PLAN_ID.eq(event.planId()))
                .and(PLAN_USER.ASSIGNMENT_STATUS.eq(ASSIGNMENT_ASSIGNED)));
        if (task == null) {
            throw new IllegalStateException("学习任务事件找不到对应培训任务");
        }
        if (LearningTaskEvents.STARTED_ROUTING_KEY.equals(event.eventType())
                && STUDY_NOT_STARTED.equals(task.getStudyStatus())) {
            PlanUserEntity update = UpdateWrapper.of(PlanUserEntity.class)
                    .set(PLAN_USER.STUDY_STATUS, STUDY_IN_PROGRESS)
                    .set(PLAN_USER.UPDATED_BY, 0L).toEntity();
            planUserMapper.updateByCondition(update, PLAN_USER.ID.eq(task.getId())
                    .and(PLAN_USER.STUDY_STATUS.eq(STUDY_NOT_STARTED)));
        }
        if (LearningTaskEvents.COMPLETED_ROUTING_KEY.equals(event.eventType())
                && !STUDY_COMPLETED.equals(task.getStudyStatus())) {
            PlanEntity plan = planMapper.selectOneByQuery(QueryWrapper.create()
                    .where(PLAN.ID.eq(event.planId()))
                    .and(PLAN.ENTERPRISE_ID.eq(event.enterpriseId()))
                    .and(PLAN.DELETED_AT.isNull()));
            if (plan == null || plan.isExamRequired()) {
                throw new IllegalStateException("当前计划不能直接完成培训任务");
            }
            LocalDateTime completedAt = event.occurredAt() == null
                    ? LocalDateTime.now() : event.occurredAt();
            PlanUserEntity update = UpdateWrapper.of(PlanUserEntity.class)
                    .set(PLAN_USER.STUDY_STATUS, STUDY_COMPLETED)
                    .set(PLAN_USER.COMPLETION_STATUS, COMPLETION_COMPLETED)
                    .set(PLAN_USER.COMPLETED_AT, completedAt)
                    .set(PLAN_USER.UPDATED_BY, 0L).toEntity();
            planUserMapper.updateByCondition(update, PLAN_USER.ID.eq(task.getId())
                    .and(PLAN_USER.STUDY_STATUS.ne(STUDY_COMPLETED)));
        }
        MqConsumeLogEntity log = new MqConsumeLogEntity();
        log.setId(IdGenerator.nextId());
        log.setConsumerName(CONSUMER_NAME);
        log.setEventId(event.eventId());
        log.setEventType(event.eventType());
        log.setProcessedAt(LocalDateTime.now());
        consumeLogMapper.insertSelective(log);
    }
}
