INSERT INTO sys_permission
    (id, parent_id, permission_code, permission_name, permission_type, permission_scope, sort_order)
VALUES
    (100, NULL, 'admin:workspace:view', '管理工作台', 'MENU', 'COMMON', 100),
    (110, 100, 'admin:dashboard:view', '工作台首页', 'MENU', 'COMMON', 110),

    (200, 100, 'admin:enterprise:view', '企业管理', 'MENU', 'PLATFORM', 200),
    (201, 200, 'admin:enterprise:create', '创建企业', 'ACTION', 'PLATFORM', 201),
    (202, 200, 'admin:enterprise:update', '编辑企业', 'ACTION', 'PLATFORM', 202),
    (203, 200, 'admin:enterprise:status', '启停企业', 'ACTION', 'PLATFORM', 203),

    (300, 100, 'admin:org:view', '部门管理', 'MENU', 'ENTERPRISE', 300),
    (301, 300, 'admin:org:create', '创建部门', 'ACTION', 'ENTERPRISE', 301),
    (302, 300, 'admin:org:update', '编辑部门', 'ACTION', 'ENTERPRISE', 302),
    (303, 300, 'admin:org:delete', '删除部门', 'ACTION', 'ENTERPRISE', 303),

    (400, 100, 'admin:user:view', '用户管理', 'MENU', 'ENTERPRISE', 400),
    (401, 400, 'admin:user:create', '创建用户', 'ACTION', 'ENTERPRISE', 401),
    (402, 400, 'admin:user:update', '编辑用户', 'ACTION', 'ENTERPRISE', 402),
    (403, 400, 'admin:user:status', '启停用户', 'ACTION', 'ENTERPRISE', 403),
    (404, 400, 'admin:user:reset-password', '重置密码', 'ACTION', 'ENTERPRISE', 404),
    (405, 400, 'admin:user:assign-role', '分配角色', 'ACTION', 'ENTERPRISE', 405),

    (500, 100, 'admin:role:view', '角色管理', 'MENU', 'ENTERPRISE', 500),
    (501, 500, 'admin:role:create', '创建角色', 'ACTION', 'ENTERPRISE', 501),
    (502, 500, 'admin:role:update', '编辑角色', 'ACTION', 'ENTERPRISE', 502),
    (503, 500, 'admin:role:status', '启停角色', 'ACTION', 'ENTERPRISE', 503),
    (504, 500, 'admin:role:delete', '删除角色', 'ACTION', 'ENTERPRISE', 504),
    (505, 500, 'admin:role:assign-permission', '分配权限', 'ACTION', 'ENTERPRISE', 505),

    (600, 100, 'admin:permission:view', '权限目录', 'MENU', 'ENTERPRISE', 600),
    (700, NULL, 'student:workspace:view', '学员工作台', 'MENU', 'COMMON', 700);
