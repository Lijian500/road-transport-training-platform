package me.lj.train.learning.service;

import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.learning.config.LearningProperties;
import me.lj.train.learning.mapper.StudyEventLogMapper;
import me.lj.train.learning.mapper.StudyProgressMapper;
import me.lj.train.learning.mapper.StudySessionMapper;
import me.lj.train.learning.model.entity.StudySessionEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 学习会话超时扫描分页测试。 */
@ExtendWith(MockitoExtension.class)
class LearningSessionTimeoutServiceTest {

    @Mock private StudySessionMapper sessionMapper;
    @Mock private StudyProgressMapper progressMapper;
    @Mock private StudyEventLogMapper eventLogMapper;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;

    @Test
    void shouldContinueScanningAfterFirstFullBatch() {
        List<StudySessionEntity> firstBatch = IntStream.rangeClosed(1, 500)
                .mapToObj(this::session)
                .toList();
        when(sessionMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(firstBatch, Collections.emptyList());
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        LearningSessionTimeoutService service = new LearningSessionTimeoutService(
                sessionMapper, progressMapper, eventLogMapper, new LearningProperties(),
                transactionManager, Clock.fixed(Instant.parse("2026-08-19T08:00:00Z"),
                ZoneId.of("Asia/Shanghai")));

        service.maintainSessions();

        ArgumentCaptor<QueryWrapper> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(sessionMapper, times(2)).selectListByQuery(queryCaptor.capture());
        assertThat(queryCaptor.getAllValues().get(1).toSQL()).contains("`id` > 500");
    }

    private StudySessionEntity session(int id) {
        StudySessionEntity value = new StudySessionEntity();
        value.setId((long) id);
        value.setStatus(LearningSessionServiceImpl.PAUSED);
        return value;
    }
}
