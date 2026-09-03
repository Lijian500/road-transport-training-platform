ALTER TABLE train_plan
    ADD COLUMN exam_duration_minutes INT NULL COMMENT '发布时考试时长快照（分钟）'
        AFTER exam_pass_score,
    ADD KEY idx_plan_exam_paper (enterprise_id, exam_paper_id);

CREATE TABLE exam_question (
    id BIGINT NOT NULL COMMENT '题目ID',
    enterprise_id BIGINT NOT NULL COMMENT '所属组织ID',
    question_type VARCHAR(24) NOT NULL COMMENT 'SINGLE_CHOICE或JUDGMENT',
    content VARCHAR(1000) NOT NULL COMMENT '题干',
    options_json LONGTEXT NOT NULL COMMENT '选项JSON',
    correct_answer VARCHAR(16) NOT NULL COMMENT '标准答案',
    analysis VARCHAR(1000) NULL COMMENT '答案解析',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED或DISABLED',
    created_by BIGINT NOT NULL COMMENT '创建人',
    updated_by BIGINT NOT NULL COMMENT '更新人',
    deleted_by BIGINT NULL COMMENT '删除人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL COMMENT '删除时间',
    PRIMARY KEY (id),
    KEY idx_exam_question_enterprise (enterprise_id, status, question_type, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='考试题库';

CREATE TABLE exam_paper (
    id BIGINT NOT NULL COMMENT '试卷ID',
    enterprise_id BIGINT NOT NULL COMMENT '所属组织ID',
    paper_name VARCHAR(128) NOT NULL COMMENT '试卷名称',
    description VARCHAR(1000) NULL COMMENT '试卷说明',
    duration_minutes INT NOT NULL COMMENT '考试时长（分钟）',
    pass_score INT NOT NULL COMMENT '默认及格分',
    question_score INT NOT NULL COMMENT '每题分值',
    manual_question_count INT NOT NULL DEFAULT 0 COMMENT '手工选题数',
    random_question_count INT NOT NULL DEFAULT 0 COMMENT '随机补齐数',
    total_score INT NOT NULL DEFAULT 0 COMMENT '总分',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT或ENABLED',
    enabled_by BIGINT NULL COMMENT '启用人',
    enabled_at DATETIME(3) NULL COMMENT '启用时间',
    created_by BIGINT NOT NULL COMMENT '创建人',
    updated_by BIGINT NOT NULL COMMENT '更新人',
    deleted_by BIGINT NULL COMMENT '删除人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL COMMENT '删除时间',
    PRIMARY KEY (id),
    KEY idx_exam_paper_enterprise (enterprise_id, status, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='考试试卷';

CREATE TABLE exam_paper_question (
    id BIGINT NOT NULL COMMENT '试卷题目ID',
    enterprise_id BIGINT NOT NULL COMMENT '所属组织ID',
    paper_id BIGINT NOT NULL COMMENT '试卷ID',
    source_question_id BIGINT NOT NULL COMMENT '来源题目ID',
    selection_type VARCHAR(16) NOT NULL COMMENT 'MANUAL或RANDOM',
    question_type VARCHAR(24) NOT NULL COMMENT '题型快照',
    content VARCHAR(1000) NOT NULL COMMENT '题干快照',
    options_json LONGTEXT NOT NULL COMMENT '选项快照JSON',
    correct_answer VARCHAR(16) NOT NULL COMMENT '标准答案快照',
    analysis VARCHAR(1000) NULL COMMENT '答案解析快照',
    score INT NOT NULL COMMENT '题目分值快照',
    sort_order INT NOT NULL COMMENT '题目顺序',
    created_by BIGINT NOT NULL COMMENT '创建人',
    updated_by BIGINT NOT NULL COMMENT '更新人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_exam_paper_question (enterprise_id, paper_id, source_question_id),
    KEY idx_exam_paper_question_order (enterprise_id, paper_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='试卷固化题目';

CREATE TABLE exam_record (
    id BIGINT NOT NULL COMMENT '考试记录ID',
    enterprise_id BIGINT NOT NULL COMMENT '所属组织ID',
    task_id BIGINT NOT NULL COMMENT '培训任务ID',
    plan_id BIGINT NOT NULL COMMENT '培训计划ID',
    user_id BIGINT NOT NULL COMMENT '学员ID',
    paper_id BIGINT NOT NULL COMMENT '试卷ID',
    paper_name VARCHAR(128) NOT NULL COMMENT '试卷名称快照',
    duration_minutes INT NOT NULL COMMENT '考试时长快照',
    pass_score INT NOT NULL COMMENT '计划及格分快照',
    total_score INT NOT NULL COMMENT '试卷总分快照',
    status VARCHAR(20) NOT NULL COMMENT 'IN_PROGRESS、SUBMITTED或TIMEOUT',
    started_at DATETIME(3) NOT NULL COMMENT '开始时间',
    deadline_at DATETIME(3) NOT NULL COMMENT '截止时间',
    submitted_at DATETIME(3) NULL COMMENT '提交或超时时间',
    score INT NULL COMMENT '考试得分',
    passed TINYINT(1) NULL COMMENT '是否及格',
    created_by BIGINT NOT NULL COMMENT '创建人',
    updated_by BIGINT NOT NULL COMMENT '更新人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_exam_record_task (enterprise_id, task_id),
    KEY idx_exam_record_owner (enterprise_id, user_id, plan_id),
    KEY idx_exam_record_timeout (status, deadline_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学员考试记录';

CREATE TABLE exam_answer (
    id BIGINT NOT NULL COMMENT '考试答案ID',
    enterprise_id BIGINT NOT NULL COMMENT '所属组织ID',
    record_id BIGINT NOT NULL COMMENT '考试记录ID',
    paper_question_id BIGINT NOT NULL COMMENT '试卷题目ID',
    answer VARCHAR(16) NOT NULL COMMENT '学员答案',
    correct TINYINT(1) NULL COMMENT '是否正确，交卷后写入',
    score INT NULL COMMENT '本题得分，交卷后写入',
    answered_at DATETIME(3) NOT NULL COMMENT '最后答题时间',
    created_by BIGINT NOT NULL COMMENT '创建人',
    updated_by BIGINT NOT NULL COMMENT '更新人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_exam_answer_question (enterprise_id, record_id, paper_question_id),
    KEY idx_exam_answer_record (enterprise_id, record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学员考试答案';
