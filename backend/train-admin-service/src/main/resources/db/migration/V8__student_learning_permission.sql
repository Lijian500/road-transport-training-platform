INSERT INTO sys_permission
    (id, parent_id, permission_code, permission_name, permission_type, permission_scope, sort_order)
VALUES
    (702, 700, 'student:learning:study', '视频学习', 'ACTION', 'COMMON', 702);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'student:learning:study'
WHERE r.role_code = 'STUDENT'
  AND r.built_in = 1
  AND r.deleted_at IS NULL;
