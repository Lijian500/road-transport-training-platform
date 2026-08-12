INSERT INTO sys_permission
    (id, parent_id, permission_code, permission_name, permission_type, permission_scope, sort_order)
VALUES
    (250, 100, 'admin:address:view', '地址管理', 'MENU', 'PLATFORM', 250),
    (251, 250, 'admin:address:create', '新增地址', 'ACTION', 'PLATFORM', 251),
    (252, 250, 'admin:address:update', '编辑地址', 'ACTION', 'PLATFORM', 252);
