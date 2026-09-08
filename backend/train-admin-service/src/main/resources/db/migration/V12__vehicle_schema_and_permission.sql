CREATE TABLE train_vehicle (
    id BIGINT NOT NULL COMMENT '车辆ID',
    enterprise_id BIGINT NOT NULL COMMENT '所属企业',
    plate_number VARCHAR(16) NOT NULL COMMENT '车牌号',
    vehicle_type VARCHAR(64) NOT NULL COMMENT '车辆类型',
    org_id BIGINT NULL COMMENT '所属部门',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED或DISABLED',
    remark VARCHAR(255) NULL COMMENT '备注',
    created_by BIGINT NULL COMMENT '创建人',
    updated_by BIGINT NULL COMMENT '更新人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_vehicle_enterprise_plate (enterprise_id, plate_number),
    KEY idx_vehicle_enterprise_status (enterprise_id, status, created_at),
    KEY idx_vehicle_org (enterprise_id, org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业车辆';

INSERT INTO sys_permission
    (id, parent_id, permission_code, permission_name, permission_type, permission_scope, sort_order)
VALUES
    (1200, 100, 'admin:vehicle:view', '车辆管理', 'MENU', 'ENTERPRISE', 350),
    (1201, 1200, 'admin:vehicle:create', '新增车辆', 'ACTION', 'ENTERPRISE', 351),
    (1202, 1200, 'admin:vehicle:update', '编辑车辆', 'ACTION', 'ENTERPRISE', 352),
    (1203, 1200, 'admin:vehicle:status', '启停车辆', 'ACTION', 'ENTERPRISE', 353);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r
JOIN sys_permission p ON p.permission_code IN
    ('admin:vehicle:view', 'admin:vehicle:create', 'admin:vehicle:update', 'admin:vehicle:status')
WHERE r.role_code = 'ENTERPRISE_ADMIN' AND r.built_in = 1 AND r.deleted_at IS NULL;
