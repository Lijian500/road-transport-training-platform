package me.lj.train.learning.service;

import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.learning.mapper.FaceCheckTaskMapper;
import me.lj.train.learning.model.entity.FaceCheckTaskEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static me.lj.train.learning.model.table.FaceCheckTaskTableDef.FACE_CHECK_TASK;

/**
 * 分页扫描已过提交截止时间的人脸抽验任务。
 */
@Component
public class FaceCheckTimeoutService {

    private static final int BATCH_SIZE = 500;

    private final FaceCheckTaskMapper taskMapper;
    private final FaceCheckServiceImpl faceCheckService;
    private final Clock clock;

    public FaceCheckTimeoutService(
            FaceCheckTaskMapper taskMapper,
            FaceCheckServiceImpl faceCheckService,
            Clock clock) {
        this.taskMapper = taskMapper;
        this.faceCheckService = faceCheckService;
        this.clock = clock;
    }

    /** 只读取任务ID，具体超时竞态在单任务事务中重新加锁确认。 */
    @Scheduled(fixedDelayString = "${learning.face-check-timeout-scan-interval-ms:1000}")
    public void expirePendingTasks() {
        Long lastId = null;
        while (true) {
            QueryWrapper query = QueryWrapper.create()
                    .where(FACE_CHECK_TASK.STATUS.eq(FaceCheckServiceImpl.PENDING))
                    .and(FACE_CHECK_TASK.DEADLINE_AT.le(LocalDateTime.now(clock)));
            if (lastId != null) {
                query.and(FACE_CHECK_TASK.ID.gt(lastId));
            }
            List<FaceCheckTaskEntity> tasks = taskMapper.selectListByQuery(query
                    .orderBy(FACE_CHECK_TASK.ID.asc()).limit(BATCH_SIZE));
            for (FaceCheckTaskEntity task : tasks) {
                faceCheckService.timeoutTask(task.getId());
            }
            if (tasks.size() < BATCH_SIZE) {
                return;
            }
            lastId = tasks.get(tasks.size() - 1).getId();
        }
    }
}
