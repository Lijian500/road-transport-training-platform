ALTER TABLE study_session
    DROP INDEX uk_study_session_active_user,
    DROP COLUMN active_user_id,
    ADD COLUMN face_check_enabled TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '是否启用人脸抽验' AFTER termination_reason,
    ADD COLUMN face_check_min_interval_seconds INT NOT NULL DEFAULT 300
        COMMENT '最小抽验间隔秒数快照' AFTER face_check_enabled,
    ADD COLUMN face_check_max_interval_seconds INT NOT NULL DEFAULT 600
        COMMENT '最大抽验间隔秒数快照' AFTER face_check_min_interval_seconds,
    ADD COLUMN face_check_timeout_seconds INT NOT NULL DEFAULT 60
        COMMENT '单次抽验响应超时秒数快照' AFTER face_check_max_interval_seconds,
    ADD COLUMN face_check_max_attempts INT NOT NULL DEFAULT 3
        COMMENT '单次抽验最多提交次数快照' AFTER face_check_timeout_seconds,
    ADD COLUMN next_face_check_effective_duration_ms BIGINT NULL
        COMMENT '下次抽验对应的累计有效学时阈值' AFTER face_check_max_attempts,
    ADD COLUMN active_user_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('CREATED', 'SIGNED_IN', 'STUDYING', 'PAUSED', 'FACE_PENDING')
             THEN user_id ELSE NULL END
    ) STORED,
    ADD UNIQUE KEY uk_study_session_active_user (enterprise_id, active_user_id);

CREATE TABLE face_check_task (
    id BIGINT NOT NULL COMMENT '人脸抽验任务ID',
    enterprise_id BIGINT NOT NULL COMMENT '所属组织ID',
    user_id BIGINT NOT NULL COMMENT '学员ID',
    session_id BIGINT NOT NULL COMMENT '学习会话ID',
    training_task_id BIGINT NOT NULL COMMENT '培训任务ID',
    plan_id BIGINT NOT NULL COMMENT '培训计划ID',
    plan_course_id BIGINT NOT NULL COMMENT '计划课程ID',
    status VARCHAR(20) NOT NULL COMMENT 'PENDING、PASSED、FAILED或TIMED_OUT',
    triggered_at DATETIME(3) NOT NULL COMMENT '触发时间',
    deadline_at DATETIME(3) NOT NULL COMMENT '提交截止时间',
    attempt_count INT NOT NULL DEFAULT 0 COMMENT '已提交次数',
    max_attempts INT NOT NULL COMMENT '最多提交次数',
    result VARCHAR(32) NULL COMMENT '最终或最近一次核验结果',
    failure_reason VARCHAR(64) NULL COMMENT '失败原因',
    similarity DECIMAL(9,6) NULL COMMENT 'SFace余弦相似度',
    completed_at DATETIME(3) NULL COMMENT '终态时间',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    pending_session_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'PENDING' THEN session_id ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_face_check_pending_session (pending_session_id),
    KEY idx_face_check_owner (enterprise_id, user_id, session_id, created_at),
    KEY idx_face_check_timeout (status, deadline_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学习人脸抽验任务';

CREATE TABLE face_check_log (
    id BIGINT NOT NULL COMMENT '人脸抽验尝试日志ID',
    enterprise_id BIGINT NOT NULL COMMENT '所属组织ID',
    user_id BIGINT NOT NULL COMMENT '学员ID',
    task_id BIGINT NOT NULL COMMENT '人脸抽验任务ID',
    session_id BIGINT NOT NULL COMMENT '学习会话ID',
    request_id VARCHAR(64) NOT NULL COMMENT '幂等请求ID',
    attempt_no INT NOT NULL COMMENT '提交序号',
    result VARCHAR(32) NOT NULL COMMENT '核验结果',
    failure_reason VARCHAR(64) NULL COMMENT '失败原因',
    similarity DECIMAL(9,6) NULL COMMENT 'SFace余弦相似度',
    elapsed_ms BIGINT NOT NULL DEFAULT 0 COMMENT '核验处理耗时',
    image_sha256 CHAR(64) NOT NULL COMMENT '抽验照片SHA-256摘要',
    response_payload LONGTEXT NOT NULL COMMENT '幂等响应JSON',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_face_check_log_request (task_id, request_id),
    UNIQUE KEY uk_face_check_log_attempt (task_id, attempt_no),
    KEY idx_face_check_log_owner (enterprise_id, user_id, session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学习人脸抽验尝试日志';
