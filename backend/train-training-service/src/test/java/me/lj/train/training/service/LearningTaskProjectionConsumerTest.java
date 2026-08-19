package me.lj.train.training.service;

import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.api.training.LearningTaskEvents;
import me.lj.train.api.training.LearningTaskEvents.LearningTaskEvent;
import me.lj.train.training.mapper.MqConsumeLogMapper;
import me.lj.train.training.mapper.PlanMapper;
import me.lj.train.training.mapper.PlanUserMapper;
import me.lj.train.training.model.entity.MqConsumeLogEntity;
import me.lj.train.training.model.entity.PlanEntity;
import me.lj.train.training.model.entity.PlanUserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 学习结果消息的幂等消费与单向状态投影测试。 */
@ExtendWith(MockitoExtension.class)
class LearningTaskProjectionConsumerTest {

    @Mock private MqConsumeLogMapper consumeLogMapper;
    @Mock private PlanUserMapper planUserMapper;
    @Mock private PlanMapper planMapper;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;

    private LearningTaskProjectionConsumer consumer;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        consumer = new LearningTaskProjectionConsumer(
                consumeLogMapper, planUserMapper, planMapper, transactionManager);
    }

    @Test
    void shouldIgnoreAlreadyConsumedEvent() {
        when(consumeLogMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);

        consumer.consume(event("event-1", LearningTaskEvents.STARTED_ROUTING_KEY));

        verifyNoInteractions(planUserMapper, planMapper);
        verify(consumeLogMapper, never()).insertSelective(any(MqConsumeLogEntity.class));
    }

    @Test
    void shouldCompleteTaskOnlyForPlanWithoutExam() {
        when(consumeLogMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        PlanUserEntity task = new PlanUserEntity();
        task.setId(500L);
        task.setStudyStatus("IN_PROGRESS");
        when(planUserMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(task);
        PlanEntity plan = new PlanEntity();
        plan.setExamRequired(false);
        when(planMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(plan);

        consumer.consume(event("event-2", LearningTaskEvents.COMPLETED_ROUTING_KEY));

        verify(planUserMapper).updateByCondition(any(PlanUserEntity.class), any());
        verify(consumeLogMapper).insertSelective(any(MqConsumeLogEntity.class));
    }

    private LearningTaskEvent event(String eventId, String type) {
        return new LearningTaskEvent(
                eventId, type, LocalDateTime.of(2026, 8, 19, 16, 0),
                20L, 10L, 500L, 100L);
    }
}
