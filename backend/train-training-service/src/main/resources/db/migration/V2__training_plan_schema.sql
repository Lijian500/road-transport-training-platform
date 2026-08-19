CREATE TABLE train_plan (
    id BIGINT NOT NULL COMMENT '培训计划ID',
    enterprise_id BIGINT NOT NULL COMMENT '所属组织ID',
    plan_name VARCHAR(128) NOT NULL COMMENT '计划名称',
    description VARCHAR(1000) NULL COMMENT '计划说明',
    start_at DATETIME(3) NOT NULL COMMENT '开始时间',
    end_at DATETIME(3) NOT NULL COMMENT '结束时间',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT、PUBLISHED、IN_PROGRESS、FINISHED或CANCELLED',
    exam_required TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否要求考试',
    exam_paper_id BIGINT NULL COMMENT '试卷ID预留',
    exam_pass_score INT NULL COMMENT '考试及格分预留',
    published_by BIGINT NULL COMMENT '发布人',
    published_at DATETIME(3) NULL COMMENT '发布时间',
    cancelled_by BIGINT NULL COMMENT '取消人',
    cancelled_at DATETIME(3) NULL COMMENT '取消时间',
    created_by BIGINT NOT NULL COMMENT '创建人',
    updated_by BIGINT NOT NULL COMMENT '更新人',
    deleted_by BIGINT NULL COMMENT '删除人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at DATETIME(3) NULL COMMENT '删除时间',
    PRIMARY KEY (id),
    KEY idx_plan_enterprise_status (enterprise_id, status, deleted_at),
    KEY idx_plan_enterprise_time (enterprise_id, start_at, end_at, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='培训计划';

CREATE TABLE train_plan_course (
    id BIGINT NOT NULL COMMENT '计划课程ID',
    enterprise_id BIGINT NOT NULL COMMENT '所属组织ID',
    plan_id BIGINT NOT NULL COMMENT '培训计划ID',
    course_id BIGINT NOT NULL COMMENT '来源课程ID',
    course_name VARCHAR(128) NOT NULL COMMENT '发布时课程名称快照',
    required_duration_seconds INT NOT NULL COMMENT '发布时规定时长快照',
    allow_seek TINYINT(1) NOT NULL COMMENT '发布时允许拖动规则快照',
    progress_report_interval_seconds INT NOT NULL COMMENT '发布时进度上报间隔快照',
    study_tolerance_seconds INT NOT NULL COMMENT '发布时学时误差快照',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '课程顺序',
    created_by BIGINT NOT NULL COMMENT '创建人',
    updated_by BIGINT NOT NULL COMMENT '更新人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_course (enterprise_id, plan_id, course_id),
    KEY idx_plan_course_order (enterprise_id, plan_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='培训计划课程规则快照';

CREATE TABLE train_plan_courseware_snapshot (
    id BIGINT NOT NULL COMMENT '计划课件快照ID',
    enterprise_id BIGINT NOT NULL COMMENT '所属组织ID',
    plan_id BIGINT NOT NULL COMMENT '培训计划ID',
    plan_course_id BIGINT NOT NULL COMMENT '计划课程ID',
    course_id BIGINT NOT NULL COMMENT '来源课程ID',
    source_courseware_id BIGINT NOT NULL COMMENT '来源课件ID',
    storage_object_id BIGINT NOT NULL COMMENT '历史OSS对象元数据ID',
    courseware_title VARCHAR(128) NOT NULL COMMENT '发布时课件标题快照',
    duration_seconds INT NOT NULL COMMENT '发布时视频时长快照',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '发布时课件顺序快照',
    created_by BIGINT NOT NULL COMMENT '创建人',
    updated_by BIGINT NOT NULL COMMENT '更新人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_courseware (enterprise_id, plan_id, source_courseware_id),
    KEY idx_plan_courseware_order (enterprise_id, plan_course_id, sort_order),
    KEY idx_plan_courseware_storage (storage_object_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='培训计划课件清单快照';

CREATE TABLE train_plan_user (
    id BIGINT NOT NULL COMMENT '计划学员任务ID',
    enterprise_id BIGINT NOT NULL COMMENT '所属组织ID',
    plan_id BIGINT NOT NULL COMMENT '培训计划ID',
    user_id BIGINT NOT NULL COMMENT '学员用户ID',
    org_id BIGINT NULL COMMENT '分配时部门ID快照',
    org_name VARCHAR(128) NULL COMMENT '分配时部门名称快照',
    username VARCHAR(64) NOT NULL COMMENT '分配时用户名快照',
    display_name VARCHAR(64) NOT NULL COMMENT '分配时姓名快照',
    assignment_status VARCHAR(16) NOT NULL DEFAULT 'ASSIGNED'
        COMMENT 'ASSIGNED或CANCELLED',
    study_status VARCHAR(24) NOT NULL DEFAULT 'NOT_STARTED' COMMENT '学习状态',
    exam_status VARCHAR(24) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT '考试状态',
    completion_status VARCHAR(24) NOT NULL DEFAULT 'NOT_COMPLETED' COMMENT '完成状态',
    completed_at DATETIME(3) NULL COMMENT '完成时间',
    created_by BIGINT NOT NULL COMMENT '创建人',
    updated_by BIGINT NOT NULL COMMENT '更新人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_user (enterprise_id, plan_id, user_id),
    KEY idx_plan_user_student (enterprise_id, user_id, assignment_status),
    KEY idx_plan_user_status (enterprise_id, plan_id, completion_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='培训计划学员任务';
