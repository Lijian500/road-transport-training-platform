package me.lj.train.training.service;

import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.training.mapper.ExamRecordMapper;
import me.lj.train.training.model.entity.ExamRecordEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

import static me.lj.train.training.constant.TrainingConstants.EXAM_RECORD_IN_PROGRESS;
import static me.lj.train.training.model.table.ExamRecordTableDef.EXAM_RECORD;

/** 扫描考试截止时间并触发自动交卷判分。 */
@Component
public class ExamTimeoutService {

    private static final int BATCH_SIZE = 500;

    private final ExamRecordMapper recordMapper;
    private final ExamServiceImpl examService;

    public ExamTimeoutService(ExamRecordMapper recordMapper, ExamServiceImpl examService) {
        this.recordMapper = recordMapper;
        this.examService = examService;
    }

    /** 分页处理已过期记录，单记录事务会再次确认状态与截止时间。 */
    @Scheduled(fixedDelayString = "${training.exam.timeout-scan-delay-ms:1000}")
    public void submitExpiredRecords() {
        Long lastId = null;
        while (true) {
            QueryWrapper query = QueryWrapper.create()
                    .where(EXAM_RECORD.STATUS.eq(EXAM_RECORD_IN_PROGRESS))
                    .and(EXAM_RECORD.DEADLINE_AT.le(LocalDateTime.now()));
            if (lastId != null) {
                query.and(EXAM_RECORD.ID.gt(lastId));
            }
            List<ExamRecordEntity> records = recordMapper.selectListByQuery(query
                    .orderBy(EXAM_RECORD.ID.asc()).limit(BATCH_SIZE));
            records.forEach(record -> examService.timeout(record.getId()));
            if (records.size() < BATCH_SIZE) {
                return;
            }
            lastId = records.get(records.size() - 1).getId();
        }
    }
}
