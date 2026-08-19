INSERT INTO sys_permission
    (id, parent_id, permission_code, permission_name, permission_type, permission_scope, sort_order)
VALUES
    (900, 100, 'admin:plan:view', '培训计划', 'MENU', 'ENTERPRISE', 900),
    (901, 900, 'admin:plan:create', '创建培训计划', 'ACTION', 'ENTERPRISE', 901),
    (902, 900, 'admin:plan:update', '编辑培训计划', 'ACTION', 'ENTERPRISE', 902),
    (903, 900, 'admin:plan:publish', '发布培训计划', 'ACTION', 'ENTERPRISE', 903),
    (904, 900, 'admin:plan:cancel', '取消培训计划', 'ACTION', 'ENTERPRISE', 904),
    (701, 700, 'student:plan:view', '我的培训任务', 'MENU', 'COMMON', 701);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p
    ON p.permission_code IN (
        'admin:plan:view',
        'admin:plan:create',
        'admin:plan:update',
        'admin:plan:publish',
        'admin:plan:cancel'
    )
WHERE r.role_code = 'ENTERPRISE_ADMIN'
  AND r.built_in = 1
  AND r.deleted_at IS NULL;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'student:plan:view'
WHERE r.role_code = 'STUDENT'
  AND r.built_in = 1
  AND r.deleted_at IS NULL;
