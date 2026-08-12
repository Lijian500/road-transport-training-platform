INSERT INTO sys_permission
    (id, parent_id, permission_code, permission_name, permission_type, permission_scope, sort_order)
VALUES
    (800, 100, 'admin:course:view', '课程管理', 'MENU', 'ENTERPRISE', 800),
    (801, 800, 'admin:course:create', '创建课程', 'ACTION', 'ENTERPRISE', 801),
    (802, 800, 'admin:course:update', '编辑课程', 'ACTION', 'ENTERPRISE', 802),
    (803, 800, 'admin:course:status', '启停课程', 'ACTION', 'ENTERPRISE', 803),
    (804, 800, 'admin:course:delete', '删除课程', 'ACTION', 'ENTERPRISE', 804),
    (805, 800, 'admin:courseware:manage', '管理课程课件', 'ACTION', 'ENTERPRISE', 805);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p
    ON p.permission_code IN (
        'admin:course:view',
        'admin:course:create',
        'admin:course:update',
        'admin:course:status',
        'admin:course:delete',
        'admin:courseware:manage'
    )
WHERE r.role_code = 'ENTERPRISE_ADMIN'
  AND r.built_in = 1
  AND r.deleted_at IS NULL;
