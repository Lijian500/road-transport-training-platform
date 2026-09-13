-- 平台管理员无企业归属；其个人登记照按owner_user_id隔离。
ALTER TABLE train_storage_object MODIFY COLUMN enterprise_id BIGINT NULL COMMENT '所属组织ID，平台个人图片为空';
ALTER TABLE train_private_image_upload_session MODIFY COLUMN enterprise_id BIGINT NULL COMMENT '所属组织ID，平台个人图片为空';
