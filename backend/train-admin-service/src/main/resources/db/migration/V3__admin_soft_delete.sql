ALTER TABLE train_org
    ADD COLUMN deleted_by BIGINT NULL COMMENT '删除人' AFTER updated_by,
    ADD COLUMN deleted_at DATETIME(3) NULL COMMENT '删除时间' AFTER updated_at,
    ADD KEY idx_org_enterprise_deleted (enterprise_id, deleted_at);

ALTER TABLE sys_role
    ADD COLUMN deleted_by BIGINT NULL COMMENT '删除人' AFTER updated_by,
    ADD COLUMN deleted_at DATETIME(3) NULL COMMENT '删除时间' AFTER updated_at,
    ADD KEY idx_role_enterprise_deleted (enterprise_id, deleted_at);
