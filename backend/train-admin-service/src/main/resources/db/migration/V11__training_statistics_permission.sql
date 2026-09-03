INSERT INTO sys_permission
    (id, parent_id, permission_code, permission_name, permission_type, permission_scope, sort_order)
VALUES
    (1100, 100, 'admin:statistics:view', '培训统计', 'MENU', 'ENTERPRISE', 1100);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'admin:statistics:view'
WHERE r.role_code = 'ENTERPRISE_ADMIN'
  AND r.built_in = 1
  AND r.deleted_at IS NULL;
