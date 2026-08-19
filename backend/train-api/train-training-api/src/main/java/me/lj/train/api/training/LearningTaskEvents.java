package me.lj.train.api.training;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学习服务投递给培训服务的任务状态事件契约。
 */
public final class LearningTaskEvents {

    public static final String EXCHANGE = "training.events";
    public static final String QUEUE = "training.learning-task-projection.v1";
    public static final String STARTED_ROUTING_KEY = "learning.task.study.started.v1";
    public static final String COMPLETED_ROUTING_KEY = "learning.task.study.completed.v1";

    private LearningTaskEvents() {
    }

    public record LearningTaskEvent(
            String eventId,
            String eventType,
            LocalDateTime occurredAt,
            Long enterpriseId,
            Long userId,
            Long taskId,
            Long planId) implements Serializable {
    }
}
