-- 论文结构核查用归并快照，不是迁移；不得直接对共享数据库执行。
-- 30张迁移表按仓库版本归并，外部sys_address单独取自用户提供的DDL。

-- road_training_admin
CREATE TABLE train_org (
    id BIGINT NOT NULL COMMENT '组织ID',
    enterprise_id BIGINT NOT NULL COMMENT '所属企业ID，企业根节点取自身ID',
    parent_id BIGINT NULL COMMENT '父组织ID',
    org_type VARCHAR(16) NOT NULL COMMENT 'ENTERPRISE或DEPARTMENT',
    organization_nature VARCHAR(16) NULL COMMENT '组织性质：ENTERPRISE企业、REGULATOR行管',
    area_id BIGINT UNSIGNED NULL COMMENT '关联sys_address行政区域ID',
    org_code VARCHAR(64) NOT NULL COMMENT '组织编码',
    enterprise_code_key VARCHAR(64) GENERATED ALWAYS AS ( CASE WHEN org_type = 'ENTERPRISE' THEN org_code ELSE NULL END ) STORED COMMENT '企业编码唯一约束键',
    org_name VARCHAR(128) NOT NULL COMMENT '组织名称',
    contact_name VARCHAR(64) NULL COMMENT '联系人',
    contact_phone VARCHAR(32) NULL COMMENT '联系电话',
    address VARCHAR(255) NULL COMMENT '地址',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    created_by BIGINT NULL COMMENT '创建人',
    updated_by BIGINT NULL COMMENT '更新人',
    deleted_by BIGINT NULL COMMENT '删除人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at DATETIME(3) NULL COMMENT '删除时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_org_enterprise_code (enterprise_id, org_code),
    UNIQUE KEY uk_org_enterprise_global_code (enterprise_code_key),
    KEY idx_org_parent (enterprise_id, parent_id, sort_order),
    KEY idx_org_type_status (org_type, status),
    KEY idx_org_enterprise_deleted (enterprise_id, deleted_at),
    KEY idx_org_nature_area (organization_nature, area_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业及部门';

-- road_training_admin
CREATE TABLE sys_user (
    id BIGINT NOT NULL COMMENT '用户ID',
    enterprise_id BIGINT NULL COMMENT '所属企业ID，平台用户为空',
    org_id BIGINT NULL COMMENT '主部门ID',
    vehicle_id BIGINT NULL COMMENT '绑定车辆ID',
    username VARCHAR(64) NOT NULL COMMENT '全平台唯一用户名',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt密码摘要',
    display_name VARCHAR(64) NOT NULL COMMENT '显示姓名',
    phone VARCHAR(32) NULL COMMENT '联系电话',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
    login_version BIGINT NOT NULL DEFAULT 1 COMMENT '登录版本',
    must_change_password TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否必须修改密码',
    platform_admin TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否平台超级管理员',
    face_reference_object_id BIGINT NULL COMMENT '人脸登记照存储对象ID',
    face_reference_updated_at DATETIME(3) NULL COMMENT '人脸登记照更新时间',
    platform_admin_key TINYINT GENERATED ALWAYS AS (NULLIF(platform_admin, 0)) STORED COMMENT '唯一平台超管约束键',
    created_by BIGINT NULL COMMENT '创建人',
    updated_by BIGINT NULL COMMENT '更新人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    UNIQUE KEY uk_user_single_platform_admin (platform_admin_key),
    KEY idx_user_enterprise_status (enterprise_id, status),
    KEY idx_user_org (enterprise_id, org_id),
    KEY idx_user_face_reference (enterprise_id, face_reference_object_id),
    KEY idx_user_enterprise_vehicle (enterprise_id, vehicle_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户账号';

-- road_training_admin
CREATE TABLE sys_role (
    id BIGINT NOT NULL COMMENT '角色ID',
    enterprise_id BIGINT NULL COMMENT '所属企业ID，平台角色为空',
    role_code VARCHAR(64) NOT NULL COMMENT '角色编码',
    role_name VARCHAR(64) NOT NULL COMMENT '角色名称',
    description VARCHAR(255) NULL COMMENT '角色说明',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
    built_in TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否内置角色',
    created_by BIGINT NULL COMMENT '创建人',
    updated_by BIGINT NULL COMMENT '更新人',
    deleted_by BIGINT NULL COMMENT '删除人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at DATETIME(3) NULL COMMENT '删除时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_enterprise_code (enterprise_id, role_code),
    KEY idx_role_enterprise_status (enterprise_id, status),
    KEY idx_role_enterprise_deleted (enterprise_id, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色';

-- road_training_admin
CREATE TABLE sys_permission (
    id BIGINT NOT NULL COMMENT '权限ID',
    parent_id BIGINT NULL COMMENT '父权限ID',
    permission_code VARCHAR(96) NOT NULL COMMENT '权限编码',
    permission_name VARCHAR(64) NOT NULL COMMENT '权限名称',
    permission_type VARCHAR(16) NOT NULL COMMENT 'MENU或ACTION',
    permission_scope VARCHAR(16) NOT NULL COMMENT 'PLATFORM、ENTERPRISE或COMMON',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_code (permission_code),
    KEY idx_permission_parent (parent_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='固定权限目录';

-- road_training_admin
CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    enterprise_id BIGINT NULL COMMENT '所属企业ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (user_id, role_id),
    KEY idx_user_role_role (role_id, user_id),
    KEY idx_user_role_enterprise (enterprise_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联';

-- road_training_admin
CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (role_id, permission_id),
    KEY idx_role_permission_permission (permission_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关联';

-- road_training_admin
CREATE TABLE train_org_user (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    org_id BIGINT NOT NULL COMMENT '组织ID',
    enterprise_id BIGINT NOT NULL COMMENT '所属企业ID',
    is_primary TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否主部门',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (user_id),
    KEY idx_org_user_org (enterprise_id, org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='组织用户关系';

-- road_training_admin
CREATE TABLE train_vehicle (
    id BIGINT NOT NULL COMMENT '车辆ID',
    enterprise_id BIGINT NOT NULL COMMENT '所属企业',
    plate_number VARCHAR(16) NOT NULL COMMENT '车牌号',
    vehicle_type VARCHAR(64) NOT NULL COMMENT '车辆类型',
    org_id BIGINT NULL COMMENT '所属部门',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED或DISABLED',
    remark VARCHAR(255) NULL COMMENT '备注',
    created_by BIGINT NULL COMMENT '创建人',
    updated_by BIGINT NULL COMMENT '更新人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_vehicle_enterprise_plate (enterprise_id, plate_number),
    KEY idx_vehicle_enterprise_status (enterprise_id, status, created_at),
    KEY idx_vehicle_org (enterprise_id, org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业车辆';

-- road_training_training
CREATE TABLE train_course (
    id BIGINT NOT NULL COMMENT '课程ID',
    enterprise_id BIGINT NOT NULL COMMENT '所属组织ID',
    course_name VARCHAR(128) NOT NULL COMMENT '课程名称',
    description VARCHAR(1000) NULL COMMENT '课程简介',
    cover_object_id BIGINT NULL COMMENT '封面存储对象ID',
    required_duration_seconds INT NOT NULL COMMENT '规定时长（秒）',
    allow_seek TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否允许拖动视频',
    progress_report_interval_seconds INT NOT NULL DEFAULT 20 COMMENT '进度上报间隔（秒）',
    study_tolerance_seconds INT NOT NULL DEFAULT 30 COMMENT '学时误差（秒）',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT、ENABLED或DISABLED',
    ever_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否曾经启用',
    created_by BIGINT NOT NULL COMMENT '创建人',
    updated_by BIGINT NOT NULL COMMENT '更新人',
    deleted_by BIGINT NULL COMMENT '删除人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at DATETIME(3) NULL COMMENT '删除时间',
    PRIMARY KEY (id),
    KEY idx_course_enterprise_status (enterprise_id, status, deleted_at),
    KEY idx_course_enterprise_name (enterprise_id, course_name, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='培训课程';

-- road_training_training
CREATE TABLE train_storage_object (
    id BIGINT NOT NULL COMMENT '存储对象ID',
    enterprise_id BIGINT NULL COMMENT '所属组织ID，平台个人图片为空',
    owner_user_id BIGINT NULL COMMENT '私有对象所属用户ID',
    provider VARCHAR(16) NOT NULL DEFAULT 'ALIYUN_OSS' COMMENT '存储提供商',
    bucket_name VARCHAR(128) NOT NULL COMMENT 'Bucket名称',
    object_key VARCHAR(512) NOT NULL COMMENT '对象Key',
    original_filename VARCHAR(255) NOT NULL COMMENT '原文件名',
    object_type VARCHAR(32) NOT NULL COMMENT 'COVER、VIDEO或FACE_REFERENCE',
    content_type VARCHAR(128) NOT NULL COMMENT '文件内容类型',
    file_size BIGINT NOT NULL COMMENT '文件大小（字节）',
    etag VARCHAR(128) NULL COMMENT 'OSS ETag',
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE、PENDING_DELETE、RETAINED或DELETED',
    created_by BIGINT NOT NULL COMMENT '创建人',
    updated_by BIGINT NOT NULL COMMENT '更新人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_storage_bucket_object (bucket_name, object_key),
    KEY idx_storage_enterprise_status (enterprise_id, status, updated_at),
    KEY idx_storage_enterprise_type (enterprise_id, object_type),
    KEY idx_storage_owner_type (enterprise_id, owner_user_id, object_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='对象存储元数据';

-- road_training_training
CREATE TABLE train_courseware (
    id BIGINT NOT NULL COMMENT '课件ID',
    enterprise_id BIGINT NOT NULL COMMENT '所属组织ID',
    course_id BIGINT NOT NULL COMMENT '课程ID',
    storage_object_id BIGINT NOT NULL COMMENT '存储对象ID',
    courseware_title VARCHAR(128) NOT NULL COMMENT '课件标题',
    duration_seconds INT NOT NULL COMMENT '视频时长（秒）',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    created_by BIGINT NOT NULL COMMENT '创建人',
    updated_by BIGINT NOT NULL COMMENT '更新人',
    deleted_by BIGINT NULL COMMENT '删除人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at DATETIME(3) NULL COMMENT '删除时间',
    PRIMARY KEY (id),
    KEY idx_courseware_course_order (enterprise_id, course_id, deleted_at, sort_order),
    KEY idx_courseware_storage (storage_object_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='视频课件';

-- road_training_training
CREATE TABLE train_upload_session (
    id BIGINT NOT NULL COMMENT '上传会话ID',
    enterprise_id BIGINT NOT NULL COMMENT '所属组织ID',
    course_id BIGINT NOT NULL COMMENT '课程ID',
    storage_object_id BIGINT NOT NULL COMMENT '预分配存储对象ID',
    courseware_id BIGINT NULL COMMENT '预分配课件ID',
    upload_type VARCHAR(16) NOT NULL COMMENT 'COVER或VIDEO',
    bucket_name VARCHAR(128) NOT NULL COMMENT 'Bucket名称',
    object_key VARCHAR(512) NOT NULL COMMENT '对象Key',
    oss_upload_id VARCHAR(256) NULL COMMENT 'OSS分片上传ID',
    original_filename VARCHAR(255) NOT NULL COMMENT '原文件名',
    expected_content_type VARCHAR(128) NOT NULL COMMENT '预期内容类型',
    expected_file_size BIGINT NOT NULL COMMENT '预期文件大小',
    client_last_modified BIGINT NULL COMMENT '浏览器文件最后修改时间',
    video_duration_seconds INT NULL COMMENT '视频时长（秒）',
    courseware_title VARCHAR(128) NULL COMMENT '课件标题',
    part_size_bytes BIGINT NOT NULL COMMENT '分片大小',
    part_count INT NOT NULL COMMENT '分片数量',
    status VARCHAR(24) NOT NULL DEFAULT 'INITIATED' COMMENT 'INITIATED、COMPLETED、CANCELLED或EXPIRED',
    expires_at DATETIME(3) NOT NULL COMMENT '过期时间',
    completed_at DATETIME(3) NULL COMMENT '完成时间',
    created_by BIGINT NOT NULL COMMENT '创建人',
    updated_by BIGINT NOT NULL COMMENT '更新人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_upload_enterprise_course (enterprise_id, course_id, status),
    KEY idx_upload_expiry (status, expires_at),
    KEY idx_upload_storage_object (storage_object_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='浏览器直传会话';

-- road_training_training
CREATE TABLE train_plan (
    id BIGINT NOT NULL COMMENT '培训计划ID',
    enterprise_id BIGINT NOT NULL COMMENT '所属组织ID',
    plan_name VARCHAR(128) NOT NULL COMMENT '计划名称',
    description VARCHAR(1000) NULL COMMENT '计划说明',
    start_at DATETIME(3) NOT NULL COMMENT '开始时间',
    end_at DATETIME(3) NOT NULL COMMENT '结束时间',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT、PUBLISHED、IN_PROGRESS、FINISHED或CANCELLED',
    exam_required TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否要求考试',
    exam_paper_id BIGINT NULL COMMENT '试卷ID预留',
    exam_pass_score INT NULL COMMENT '考试及格分预留',
    exam_duration_minutes INT NULL COMMENT '发布时考试时长快照（分钟）',
    face_check_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用人脸抽验',
    face_check_min_interval_seconds INT NOT NULL DEFAULT 300 COMMENT '抽验最小有效学时间隔（秒）',
    face_check_max_interval_seconds INT NOT NULL DEFAULT 600 COMMENT '抽验最大有效学时间隔（秒）',
    face_check_timeout_seconds INT NOT NULL DEFAULT 60 COMMENT '单次抽验响应时限（秒）',
    face_check_max_attempts INT NOT NULL DEFAULT 3 COMMENT '单次抽验最多提交次数',
    published_by BIGINT NULL COMMENT '发布人',
    published_at DATETIME(3) NULL COMMENT '发布时间',
    cancelled_by BIGINT NULL COMMENT '取消人',
    cancelled_at DATETIME(3) NULL COMMENT '取消时间',
    created_by BIGINT NOT NULL COMMENT '创建人',
    updated_by BIGINT NOT NULL COMMENT '更新人',
    deleted_by BIGINT NULL COMMENT '删除人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at DATETIME(3) NULL COMMENT '删除时间',
    PRIMARY KEY (id),
    KEY idx_plan_enterprise_status (enterprise_id, status, deleted_at),
    KEY idx_plan_enterprise_time (enterprise_id, start_at, end_at, deleted_at),
    KEY idx_plan_exam_paper (enterprise_id, exam_paper_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='培训计划';

-- road_training_training
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
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_course (enterprise_id, plan_id, course_id),
    KEY idx_plan_course_order (enterprise_id, plan_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='培训计划课程规则快照';

-- road_training_training
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
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_courseware (enterprise_id, plan_id, source_courseware_id),
    KEY idx_plan_courseware_order (enterprise_id, plan_course_id, sort_order),
    KEY idx_plan_courseware_storage (storage_object_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='培训计划课件清单快照';

-- road_training_training
CREATE TABLE train_plan_user (
    id BIGINT NOT NULL COMMENT '计划学员任务ID',
    enterprise_id BIGINT NOT NULL COMMENT '所属组织ID',
    plan_id BIGINT NOT NULL COMMENT '培训计划ID',
    user_id BIGINT NOT NULL COMMENT '学员用户ID',
    org_id BIGINT NULL COMMENT '分配时部门ID快照',
    org_name VARCHAR(128) NULL COMMENT '分配时部门名称快照',
    username VARCHAR(64) NOT NULL COMMENT '分配时用户名快照',
    display_name VARCHAR(64) NOT NULL COMMENT '分配时姓名快照',
    assignment_status VARCHAR(16) NOT NULL DEFAULT 'ASSIGNED' COMMENT 'ASSIGNED或CANCELLED',
    study_status VARCHAR(24) NOT NULL DEFAULT 'NOT_STARTED' COMMENT '学习状态',
    exam_status VARCHAR(24) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT '考试状态',
    completion_status VARCHAR(24) NOT NULL DEFAULT 'NOT_COMPLETED' COMMENT '完成状态',
    completed_at DATETIME(3) NULL COMMENT '完成时间',
    created_by BIGINT NOT NULL COMMENT '创建人',
    updated_by BIGINT NOT NULL COMMENT '更新人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_user (enterprise_id, plan_id, user_id),
    KEY idx_plan_user_student (enterprise_id, user_id, assignment_status),
    KEY idx_plan_user_status (enterprise_id, plan_id, completion_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='培训计划学员任务';

-- road_training_training
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

-- road_training_training
CREATE TABLE train_private_image_upload_session (
    id BIGINT NOT NULL COMMENT '私有图片上传会话ID',
    enterprise_id BIGINT NULL COMMENT '所属组织ID，平台个人图片为空',
    owner_user_id BIGINT NOT NULL COMMENT '私有图片所属用户ID',
    storage_object_id BIGINT NOT NULL COMMENT '预分配存储对象ID',
    upload_type VARCHAR(32) NOT NULL COMMENT 'FACE_REFERENCE',
    bucket_name VARCHAR(128) NOT NULL COMMENT 'Bucket名称',
    object_key VARCHAR(512) NOT NULL COMMENT '对象Key',
    original_filename VARCHAR(255) NOT NULL COMMENT '原文件名',
    expected_content_type VARCHAR(128) NOT NULL COMMENT '预期内容类型',
    expected_file_size BIGINT NOT NULL COMMENT '预期文件大小',
    client_last_modified BIGINT NULL COMMENT '浏览器文件最后修改时间',
    status VARCHAR(24) NOT NULL DEFAULT 'INITIATED' COMMENT 'INITIATED、COMPLETED、CANCELLED或EXPIRED',
    expires_at DATETIME(3) NOT NULL COMMENT '过期时间',
    completed_at DATETIME(3) NULL COMMENT '完成时间',
    created_by BIGINT NOT NULL COMMENT '创建人',
    updated_by BIGINT NOT NULL COMMENT '更新人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_private_upload_owner (enterprise_id, owner_user_id, status),
    KEY idx_private_upload_expiry (status, expires_at),
    KEY idx_private_upload_storage (storage_object_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='私有图片浏览器直传会话';

-- road_training_training
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
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL COMMENT '删除时间',
    PRIMARY KEY (id),
    KEY idx_exam_question_enterprise (enterprise_id, status, question_type, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='考试题库';

-- road_training_training
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
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL COMMENT '删除时间',
    PRIMARY KEY (id),
    KEY idx_exam_paper_enterprise (enterprise_id, status, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='考试试卷';

-- road_training_training
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
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_exam_paper_question (enterprise_id, paper_id, source_question_id),
    KEY idx_exam_paper_question_order (enterprise_id, paper_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='试卷固化题目';

-- road_training_training
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
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_exam_record_task (enterprise_id, task_id),
    KEY idx_exam_record_owner (enterprise_id, user_id, plan_id),
    KEY idx_exam_record_timeout (status, deadline_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学员考试记录';

-- road_training_training
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
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_exam_answer_question (enterprise_id, record_id, paper_question_id),
    KEY idx_exam_answer_record (enterprise_id, record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学员考试答案';

-- road_training_learning
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
    face_check_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用人脸抽验',
    face_check_min_interval_seconds INT NOT NULL DEFAULT 300 COMMENT '最小抽验间隔秒数快照',
    face_check_max_interval_seconds INT NOT NULL DEFAULT 600 COMMENT '最大抽验间隔秒数快照',
    face_check_timeout_seconds INT NOT NULL DEFAULT 60 COMMENT '单次抽验响应超时秒数快照',
    face_check_max_attempts INT NOT NULL DEFAULT 3 COMMENT '单次抽验最多提交次数快照',
    next_face_check_effective_duration_ms BIGINT NULL COMMENT '下次抽验对应的累计有效学时阈值',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    active_user_id BIGINT GENERATED ALWAYS AS ( CASE WHEN status IN ('CREATED', 'SIGNED_IN', 'STUDYING', 'PAUSED', 'FACE_PENDING') THEN user_id ELSE NULL END ) STORED,
    attendance_action VARCHAR(16) NULL,
    attendance_sequence BIGINT NULL,
    attendance_verified_at DATETIME(3) NULL,
    attendance_photo_object_id BIGINT NULL COMMENT '待消费的人脸验证照片对象ID',
    sign_in_photo_object_id BIGINT NULL COMMENT '签到照片对象ID',
    sign_out_photo_object_id BIGINT NULL COMMENT '签退照片对象ID',
    PRIMARY KEY (id),
    KEY idx_study_session_owner (enterprise_id, user_id, created_at),
    KEY idx_study_session_course (enterprise_id, plan_id, plan_course_id, user_id),
    KEY idx_study_session_timeout (status, last_event_at),
    UNIQUE KEY uk_study_session_active_user (enterprise_id, active_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='在线学习会话';

-- road_training_learning
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
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_study_progress_course (enterprise_id, user_id, plan_id, plan_course_id),
    KEY idx_study_progress_task (enterprise_id, task_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='计划课程学习进度';

-- road_training_learning
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
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_courseware_progress (enterprise_id, user_id, plan_course_id, courseware_snapshot_id),
    KEY idx_courseware_progress_order (enterprise_id, user_id, plan_course_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='计划课件学习进度';

-- road_training_learning
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

-- road_training_learning
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

-- road_training_learning
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
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    pending_session_id BIGINT GENERATED ALWAYS AS ( CASE WHEN status = 'PENDING' THEN session_id ELSE NULL END ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_face_check_pending_session (pending_session_id),
    KEY idx_face_check_owner (enterprise_id, user_id, session_id, created_at),
    KEY idx_face_check_timeout (status, deadline_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学习人脸抽验任务';

-- road_training_learning
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
    photo_object_id BIGINT NULL COMMENT '本次抽验提交照片对象ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_face_check_log_request (task_id, request_id),
    UNIQUE KEY uk_face_check_log_attempt (task_id, attempt_no),
    KEY idx_face_check_log_owner (enterprise_id, user_id, session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学习人脸抽验尝试日志';

-- road_training_admin
CREATE TABLE sys_address (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `level` tinyint unsigned NOT NULL COMMENT '层级',
    `parent_code` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL DEFAULT '0' COMMENT '父级行政代码',
    `area_code` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL DEFAULT '0' COMMENT '行政代码',
    `zip_code` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL DEFAULT '0' COMMENT '邮政编码',
    `city_code` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL DEFAULT '' COMMENT '区号',
    `name` varchar(50) NOT NULL DEFAULT '' COMMENT '名称',
    `short_name` varchar(50) NOT NULL DEFAULT '' COMMENT '简称',
    `merger_name` varchar(50) NOT NULL DEFAULT '' COMMENT '组合名',
    `pinyin` varchar(30) NOT NULL DEFAULT '' COMMENT '拼音',
    `lng` decimal(10,6) NOT NULL DEFAULT '0.000000' COMMENT '经度',
    `lat` decimal(10,6) NOT NULL DEFAULT '0.000000' COMMENT '纬度',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`area_code`) USING BTREE,
    KEY `idx_parent_code` (`parent_code`) USING BTREE
) ENGINE=MyISAM AUTO_INCREMENT=4063 DEFAULT CHARSET=utf8mb3 COMMENT='中国行政地区表';
