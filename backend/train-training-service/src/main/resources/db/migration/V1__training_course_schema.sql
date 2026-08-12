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
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at DATETIME(3) NULL COMMENT '删除时间',
    PRIMARY KEY (id),
    KEY idx_course_enterprise_status (enterprise_id, status, deleted_at),
    KEY idx_course_enterprise_name (enterprise_id, course_name, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='培训课程';

CREATE TABLE train_storage_object (
    id BIGINT NOT NULL COMMENT '存储对象ID',
    enterprise_id BIGINT NOT NULL COMMENT '所属组织ID',
    provider VARCHAR(16) NOT NULL DEFAULT 'ALIYUN_OSS' COMMENT '存储提供商',
    bucket_name VARCHAR(128) NOT NULL COMMENT 'Bucket名称',
    object_key VARCHAR(512) NOT NULL COMMENT '对象Key',
    original_filename VARCHAR(255) NOT NULL COMMENT '原文件名',
    object_type VARCHAR(16) NOT NULL COMMENT 'COVER或VIDEO',
    content_type VARCHAR(128) NOT NULL COMMENT '文件内容类型',
    file_size BIGINT NOT NULL COMMENT '文件大小（字节）',
    etag VARCHAR(128) NULL COMMENT 'OSS ETag',
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE'
        COMMENT 'ACTIVE、PENDING_DELETE、RETAINED或DELETED',
    created_by BIGINT NOT NULL COMMENT '创建人',
    updated_by BIGINT NOT NULL COMMENT '更新人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_storage_bucket_object (bucket_name, object_key),
    KEY idx_storage_enterprise_status (enterprise_id, status, updated_at),
    KEY idx_storage_enterprise_type (enterprise_id, object_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='对象存储元数据';

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
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at DATETIME(3) NULL COMMENT '删除时间',
    PRIMARY KEY (id),
    KEY idx_courseware_course_order (enterprise_id, course_id, deleted_at, sort_order),
    KEY idx_courseware_storage (storage_object_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='视频课件';

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
    status VARCHAR(24) NOT NULL DEFAULT 'INITIATED'
        COMMENT 'INITIATED、COMPLETED、CANCELLED或EXPIRED',
    expires_at DATETIME(3) NOT NULL COMMENT '过期时间',
    completed_at DATETIME(3) NULL COMMENT '完成时间',
    created_by BIGINT NOT NULL COMMENT '创建人',
    updated_by BIGINT NOT NULL COMMENT '更新人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_upload_enterprise_course (enterprise_id, course_id, status),
    KEY idx_upload_expiry (status, expires_at),
    KEY idx_upload_storage_object (storage_object_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='浏览器直传会话';
