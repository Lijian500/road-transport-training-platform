CREATE TABLE mq_outbox (
    id BIGINT NOT NULL COMMENT 'Outbox记录ID',
    event_id VARCHAR(64) NOT NULL COMMENT '全局事件ID',
    business_key VARCHAR(128) NOT NULL COMMENT '业务幂等键',
    aggregate_type VARCHAR(32) NOT NULL COMMENT '聚合类型',
    aggregate_id BIGINT NOT NULL COMMENT '聚合ID',
    routing_key VARCHAR(128) NOT NULL COMMENT 'RabbitMQ路由键',
    payload LONGTEXT NOT NULL COMMENT '事件JSON',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING、SENT或FAILED',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    next_retry_at DATETIME(3) NOT NULL COMMENT '下次重试时间',
    last_error VARCHAR(500) NULL COMMENT '最后错误摘要',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    sent_at DATETIME(3) NULL COMMENT '发送时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event (event_id),
    UNIQUE KEY uk_outbox_business (business_key),
    KEY idx_outbox_pending (status, next_retry_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学习事件可靠消息Outbox';
