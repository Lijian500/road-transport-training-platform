CREATE TABLE mq_consume_log (
    id BIGINT NOT NULL COMMENT '消费记录ID',
    consumer_name VARCHAR(64) NOT NULL COMMENT '消费者名称',
    event_id VARCHAR(64) NOT NULL COMMENT '事件ID',
    event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
    processed_at DATETIME(3) NOT NULL COMMENT '处理时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_consume_event (consumer_name, event_id),
    KEY idx_consume_processed (processed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='RabbitMQ消费幂等日志';
