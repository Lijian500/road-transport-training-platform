ALTER TABLE sys_user
    ADD COLUMN face_reference_object_id BIGINT NULL COMMENT '人脸登记照存储对象ID' AFTER platform_admin,
    ADD COLUMN face_reference_updated_at DATETIME(3) NULL COMMENT '人脸登记照更新时间' AFTER face_reference_object_id,
    ADD KEY idx_user_face_reference (enterprise_id, face_reference_object_id);

INSERT INTO sys_permission
    (id, parent_id, permission_code, permission_name, permission_type, permission_scope, sort_order)
VALUES
    (406, 400, 'admin:face-check:view', '查看人脸登记状态', 'ACTION', 'ENTERPRISE', 406),
    (407, 400, 'admin:face-check:manage', '管理人脸登记照', 'ACTION', 'ENTERPRISE', 407);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p
    ON p.permission_code IN ('admin:face-check:view', 'admin:face-check:manage')
WHERE r.role_code = 'ENTERPRISE_ADMIN'
  AND r.built_in = 1
  AND r.deleted_at IS NULL;
