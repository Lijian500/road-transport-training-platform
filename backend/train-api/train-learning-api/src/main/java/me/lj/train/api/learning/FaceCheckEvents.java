package me.lj.train.api.learning;

/**
 * 人脸抽验实时事件RabbitMQ契约。
 */
public final class FaceCheckEvents {

    /** 复用现有业务事件交换机，实时服务使用独立队列消费。 */
    public static final String EXCHANGE = "training.events";
    public static final String QUEUE = "realtime.face-check.v1";
    public static final String REQUIRED_ROUTING_KEY = "learning.face-check.required.v1";
    public static final String RESULT_ROUTING_KEY = "learning.face-check.result.v1";
    public static final String REQUIRED_EVENT = "FACE_CHECK_REQUIRED";
    public static final String RESULT_EVENT = "FACE_CHECK_RESULT";

    private FaceCheckEvents() {
    }
}
