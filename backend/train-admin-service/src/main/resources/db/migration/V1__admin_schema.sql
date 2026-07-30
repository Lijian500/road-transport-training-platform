CREATE TABLE train_org (
    id BIGINT NOT NULL COMMENT '组织ID',
    enterprise_id BIGINT NOT NULL COMMENT '所属企业ID，企业根节点取自身ID',
    parent_id BIGINT NULL COMMENT '父组织ID',
    org_type VARCHAR(16) NOT NULL COMMENT 'ENTERPRISE或DEPARTMENT',
    org_code VARCHAR(64) NOT NULL COMMENT '组织编码',
    enterprise_code_key VARCHAR(64)
        GENERATED ALWAYS AS (
            CASE WHEN org_type = 'ENTERPRISE' THEN org_code ELSE NULL END
        ) STORED COMMENT '企业编码唯一约束键',
    org_name VARCHAR(128) NOT NULL COMMENT '组织名称',
    contact_name VARCHAR(64) NULL COMMENT '联系人',
    contact_phone VARCHAR(32) NULL COMMENT '联系电话',
    address VARCHAR(255) NULL COMMENT '地址',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    created_by BIGINT NULL COMMENT '创建人',
    updated_by BIGINT NULL COMMENT '更新人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_org_enterprise_code (enterprise_id, org_code),
    UNIQUE KEY uk_org_enterprise_global_code (enterprise_code_key),
    KEY idx_org_parent (enterprise_id, parent_id, sort_order),
    KEY idx_org_type_status (org_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业及部门';

CREATE TABLE sys_user (
    id BIGINT NOT NULL COMMENT '用户ID',
    enterprise_id BIGINT NULL COMMENT '所属企业ID，平台用户为空',
    org_id BIGINT NULL COMMENT '主部门ID',
    username VARCHAR(64) NOT NULL COMMENT '全平台唯一用户名',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt密码摘要',
    display_name VARCHAR(64) NOT NULL COMMENT '显示姓名',
    phone VARCHAR(32) NULL COMMENT '联系电话',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
    login_version BIGINT NOT NULL DEFAULT 1 COMMENT '登录版本',
    must_change_password TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否必须修改密码',
    platform_admin TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否平台超级管理员',
    platform_admin_key TINYINT
        GENERATED ALWAYS AS (NULLIF(platform_admin, 0)) STORED COMMENT '唯一平台超管约束键',
    created_by BIGINT NULL COMMENT '创建人',
    updated_by BIGINT NULL COMMENT '更新人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    UNIQUE KEY uk_user_single_platform_admin (platform_admin_key),
    KEY idx_user_enterprise_status (enterprise_id, status),
    KEY idx_user_org (enterprise_id, org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户账号';

CREATE TABLE sys_role (
    id BIGINT NOT NULL COMMENT '角色ID',
    enterprise_id BIGINT NULL COMMENT '所属企业ID，平台角色为空',
    role_code VARCHAR(64) NOT NULL COMMENT '角色编码',
    role_name VARCHAR(64) NOT NULL COMMENT '角色名称',
    description VARCHAR(255) NULL COMMENT '角色说明',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
    built_in TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否内置角色',
    created_by BIGINT NULL COMMENT '创建人',
    updated_by BIGINT NULL COMMENT '更新人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_enterprise_code (enterprise_id, role_code),
    KEY idx_role_enterprise_status (enterprise_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色';

CREATE TABLE sys_permission (
    id BIGINT NOT NULL COMMENT '权限ID',
    parent_id BIGINT NULL COMMENT '父权限ID',
    permission_code VARCHAR(96) NOT NULL COMMENT '权限编码',
    permission_name VARCHAR(64) NOT NULL COMMENT '权限名称',
    permission_type VARCHAR(16) NOT NULL COMMENT 'MENU或ACTION',
    permission_scope VARCHAR(16) NOT NULL COMMENT 'PLATFORM、ENTERPRISE或COMMON',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_code (permission_code),
    KEY idx_permission_parent (parent_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='固定权限目录';

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    enterprise_id BIGINT NULL COMMENT '所属企业ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (user_id, role_id),
    KEY idx_user_role_role (role_id, user_id),
    KEY idx_user_role_enterprise (enterprise_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联';

CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (role_id, permission_id),
    KEY idx_role_permission_permission (permission_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关联';

CREATE TABLE train_org_user (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    org_id BIGINT NOT NULL COMMENT '组织ID',
    enterprise_id BIGINT NOT NULL COMMENT '所属企业ID',
    is_primary TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否主部门',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (user_id),
    KEY idx_org_user_org (enterprise_id, org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='组织用户关系';
