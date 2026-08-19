CREATE TABLE study_session (
    id BIGINT NOT NULL COMMENT '学习会话ID',
    enterprise_id BIGINT NOT NULL COMMENT '所属组织ID',
    user_id BIGINT NOT NULL COMMENT '学员ID',
    task_id BIGINT NOT NULL COMMENT '培训任务ID',
    plan_id BIGINT NOT NULL COMMENT '培训计划ID',
    plan_course_id BIGINT NOT NULL COMMENT '计划课程ID',
    client_instance_id VARCHAR(64) NOT NULL COMMENT '浏览器实例ID',
    course_name VARCHAR(128) NOT NULL COMMENT '课程名称快照',
    sort_order INT NOT NULL COMMENT '课程顺序快照',
    plan_end_at DATETIME(3) NOT NULL COMMENT '计划结束时间快照',
    status VARCHAR(20) NOT NULL COMMENT '会话状态',
    current_courseware_snapshot_id BIGINT NULL COMMENT '当前课件快照ID',
    last_sequence BIGINT NOT NULL DEFAULT 0 COMMENT '最后接受事件序号',
    last_confirmed_position_ms BIGINT NOT NULL DEFAULT 0 COMMENT '当前课件确认位置',
    last_event_at DATETIME(3) NULL COMMENT '最后事件时间',
    signed_in_at DATETIME(3) NULL COMMENT '签到时间',
    started_at DATETIME(3) NULL COMMENT '首次学习时间',
    paused_at DATETIME(3) NULL COMMENT '暂停时间',
    completed_at DATETIME(3) NULL COMMENT '课程完成时间',
    signed_out_at DATETIME(3) NULL COMMENT '签退时间',
    terminated_at DATETIME(3) NULL COMMENT '终止时间',
    termination_reason VARCHAR(64) NULL COMMENT '终止原因',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    active_user_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('CREATED', 'SIGNED_IN', 'STUDYING', 'PAUSED')
             THEN user_id ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_study_session_active_user (enterprise_id, active_user_id),
    KEY idx_study_session_owner (enterprise_id, user_id, created_at),
    KEY idx_study_session_course (enterprise_id, plan_id, plan_course_id, user_id),
    KEY idx_study_session_timeout (status, last_event_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='在线学习会话';

CREATE TABLE study_progress (
    id BIGINT NOT NULL COMMENT '课程学习进度ID',
    enterprise_id BIGINT NOT NULL COMMENT '所属组织ID',
    user_id BIGINT NOT NULL COMMENT '学员ID',
    task_id BIGINT NOT NULL COMMENT '培训任务ID',
    plan_id BIGINT NOT NULL COMMENT '培训计划ID',
    plan_course_id BIGINT NOT NULL COMMENT '计划课程ID',
    course_name VARCHAR(128) NOT NULL COMMENT '课程名称快照',
    sort_order INT NOT NULL COMMENT '课程顺序快照',
    required_duration_ms BIGINT NOT NULL COMMENT '规定学时毫秒',
    effective_duration_ms BIGINT NOT NULL DEFAULT 0 COMMENT '有效学时毫秒',
    allow_seek TINYINT(1) NOT NULL COMMENT '是否允许拖动',
    progress_report_interval_seconds INT NOT NULL COMMENT '上报间隔快照',
    study_tolerance_seconds INT NOT NULL COMMENT '学时误差快照',
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED' COMMENT '学习状态',
    completed_at DATETIME(3) NULL COMMENT '完成时间',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_study_progress_course (enterprise_id, user_id, plan_id, plan_course_id),
    KEY idx_study_progress_task (enterprise_id, task_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='计划课程学习进度';

CREATE TABLE study_courseware_progress (
    id BIGINT NOT NULL COMMENT '课件学习进度ID',
    enterprise_id BIGINT NOT NULL COMMENT '所属组织ID',
    user_id BIGINT NOT NULL COMMENT '学员ID',
    task_id BIGINT NOT NULL COMMENT '培训任务ID',
    plan_id BIGINT NOT NULL COMMENT '培训计划ID',
    plan_course_id BIGINT NOT NULL COMMENT '计划课程ID',
    courseware_snapshot_id BIGINT NOT NULL COMMENT '课件快照ID',
    courseware_title VARCHAR(128) NOT NULL COMMENT '课件标题快照',
    sort_order INT NOT NULL COMMENT '课件顺序快照',
    duration_ms BIGINT NOT NULL COMMENT '视频时长毫秒',
    confirmed_position_ms BIGINT NOT NULL DEFAULT 0 COMMENT '最近确认位置',
    max_confirmed_position_ms BIGINT NOT NULL DEFAULT 0 COMMENT '最大确认位置',
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED' COMMENT '学习状态',
    completed_at DATETIME(3) NULL COMMENT '完成时间',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_courseware_progress (enterprise_id, user_id, plan_course_id, courseware_snapshot_id),
    KEY idx_courseware_progress_order (enterprise_id, user_id, plan_course_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='计划课件学习进度';

CREATE TABLE study_event_log (
    id BIGINT NOT NULL COMMENT '学习事件ID',
    enterprise_id BIGINT NOT NULL COMMENT '所属组织ID',
    user_id BIGINT NOT NULL COMMENT '学员ID',
    session_id BIGINT NOT NULL COMMENT '学习会话ID',
    request_id VARCHAR(64) NOT NULL COMMENT '幂等请求ID',
    sequence_no BIGINT NOT NULL COMMENT '事件序号',
    event_type VARCHAR(20) NOT NULL COMMENT '事件类型',
    from_status VARCHAR(20) NOT NULL COMMENT '转换前状态',
    to_status VARCHAR(20) NOT NULL COMMENT '转换后状态',
    courseware_snapshot_id BIGINT NULL COMMENT '课件快照ID',
    reported_position_ms BIGINT NOT NULL DEFAULT 0 COMMENT '前端上报位置',
    confirmed_position_ms BIGINT NOT NULL DEFAULT 0 COMMENT '服务端确认位置',
    credited_duration_ms BIGINT NOT NULL DEFAULT 0 COMMENT '本次有效学时',
    result_code VARCHAR(16) NOT NULL COMMENT '处理结果码',
    response_payload LONGTEXT NOT NULL COMMENT '幂等响应JSON',
    server_time DATETIME(3) NOT NULL COMMENT '服务端处理时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_study_event_request (session_id, request_id),
    UNIQUE KEY uk_study_event_sequence (session_id, sequence_no),
    KEY idx_study_event_time (enterprise_id, session_id, server_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='关键学习事件日志';
