ALTER TABLE train_plan
    ADD COLUMN face_check_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用人脸抽验' AFTER exam_pass_score,
    ADD COLUMN face_check_min_interval_seconds INT NOT NULL DEFAULT 300 COMMENT '抽验最小有效学时间隔（秒）' AFTER face_check_enabled,
    ADD COLUMN face_check_max_interval_seconds INT NOT NULL DEFAULT 600 COMMENT '抽验最大有效学时间隔（秒）' AFTER face_check_min_interval_seconds,
    ADD COLUMN face_check_timeout_seconds INT NOT NULL DEFAULT 60 COMMENT '单次抽验响应时限（秒）' AFTER face_check_max_interval_seconds,
    ADD COLUMN face_check_max_attempts INT NOT NULL DEFAULT 3 COMMENT '单次抽验最多提交次数' AFTER face_check_timeout_seconds;

ALTER TABLE train_storage_object
    ADD COLUMN owner_user_id BIGINT NULL COMMENT '私有对象所属用户ID' AFTER enterprise_id,
    MODIFY COLUMN object_type VARCHAR(32) NOT NULL COMMENT 'COVER、VIDEO或FACE_REFERENCE',
    ADD KEY idx_storage_owner_type (enterprise_id, owner_user_id, object_type, status);

CREATE TABLE train_private_image_upload_session (
    id BIGINT NOT NULL COMMENT '私有图片上传会话ID',
    enterprise_id BIGINT NOT NULL COMMENT '所属组织ID',
    owner_user_id BIGINT NOT NULL COMMENT '私有图片所属用户ID',
    storage_object_id BIGINT NOT NULL COMMENT '预分配存储对象ID',
    upload_type VARCHAR(32) NOT NULL COMMENT 'FACE_REFERENCE',
    bucket_name VARCHAR(128) NOT NULL COMMENT 'Bucket名称',
    object_key VARCHAR(512) NOT NULL COMMENT '对象Key',
    original_filename VARCHAR(255) NOT NULL COMMENT '原文件名',
    expected_content_type VARCHAR(128) NOT NULL COMMENT '预期内容类型',
    expected_file_size BIGINT NOT NULL COMMENT '预期文件大小',
    client_last_modified BIGINT NULL COMMENT '浏览器文件最后修改时间',
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
    KEY idx_private_upload_owner (enterprise_id, owner_user_id, status),
    KEY idx_private_upload_expiry (status, expires_at),
    KEY idx_private_upload_storage (storage_object_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='私有图片浏览器直传会话';
