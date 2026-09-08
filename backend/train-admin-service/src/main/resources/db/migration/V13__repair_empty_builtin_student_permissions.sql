-- 修复旧初始化代码遗漏授权的内置学员角色；已有任何授权的角色保持不变。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN train_org o ON o.id = r.enterprise_id
JOIN sys_permission p ON p.permission_code LIKE 'student:%'
LEFT JOIN sys_role_permission existing ON existing.role_id = r.id
WHERE r.role_code = 'STUDENT'
  AND r.built_in = 1
  AND r.deleted_at IS NULL
  AND o.deleted_at IS NULL
  AND o.organization_nature = 'ENTERPRISE'
  AND existing.role_id IS NULL;
