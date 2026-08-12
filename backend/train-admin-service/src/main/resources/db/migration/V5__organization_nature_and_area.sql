ALTER TABLE train_org
    ADD COLUMN organization_nature VARCHAR(16) NULL COMMENT '组织性质：ENTERPRISE企业、REGULATOR行管' AFTER org_type,
    ADD COLUMN area_id BIGINT UNSIGNED NULL COMMENT '关联sys_address行政区域ID' AFTER organization_nature,
    ADD KEY idx_org_nature_area (organization_nature, area_id);

UPDATE train_org
SET organization_nature = 'ENTERPRISE'
WHERE org_type = 'ENTERPRISE'
  AND organization_nature IS NULL;

UPDATE sys_permission
SET permission_name = CASE permission_code
    WHEN 'admin:enterprise:view' THEN '组织管理'
    WHEN 'admin:enterprise:create' THEN '创建组织'
    WHEN 'admin:enterprise:update' THEN '编辑组织'
    WHEN 'admin:enterprise:status' THEN '启停组织'
    ELSE permission_name
END
WHERE permission_code IN (
    'admin:enterprise:view',
    'admin:enterprise:create',
    'admin:enterprise:update',
    'admin:enterprise:status'
);
