INSERT INTO sys_permission
    (id, parent_id, permission_code, permission_name, permission_type, permission_scope, sort_order)
VALUES
    (1000, 100, 'admin:exam:view', '考试管理', 'MENU', 'ENTERPRISE', 1000),
    (1001, 1000, 'admin:exam:manage', '维护题库与试卷', 'ACTION', 'ENTERPRISE', 1001),
    (703, 700, 'student:exam:take', '参加在线考试', 'ACTION', 'COMMON', 703);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p
    ON p.permission_code IN ('admin:exam:view', 'admin:exam:manage')
WHERE r.role_code = 'ENTERPRISE_ADMIN'
  AND r.built_in = 1
  AND r.deleted_at IS NULL;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'student:exam:take'
WHERE r.role_code = 'STUDENT'
  AND r.built_in = 1
  AND r.deleted_at IS NULL;
