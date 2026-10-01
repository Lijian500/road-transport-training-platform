# 数据表结构字典

本字典覆盖31张表、476个字段和113项索引：30张迁移管理表按各服务Flyway版本归并，外部sys_address按用户提供的真实DDL核验。它描述仓库及所提供DDL对应的结构，不证明运行库已应用所有迁移。

全部31张表均未声明物理外键；字段说明中的关联是由服务校验的逻辑标识。PK为主键，FK为数据库外键。未显式设置默认值的可空普通列记为隐式NULL，非空列记为无；生成列另列计算表达式，自增属性单独注明。

正文与附录使用同一份JSON结构源；更新迁移后运行 `docs/thesis/tools/build_data_dictionary.py` 重新生成。外部表原始定义保存在 `database/design/sys_address.source.sql`，其来源与指纹单独记录，不作为Flyway迁移。

### train_org 企业及部门

所属数据库：`road_training_admin`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT | PK；无FK | 否 | 无 | 组织ID |
| enterprise_id | BIGINT | 无 | 否 | 无 | 所属企业ID，企业根节点取自身ID；逻辑关联 train_org.id |
| parent_id | BIGINT | 无 | 是 | 隐式 NULL | 父组织ID；逻辑关联 train_org.id |
| org_type | VARCHAR(16) | 无 | 否 | 无 | ENTERPRISE或DEPARTMENT |
| organization_nature | VARCHAR(16) | 无 | 是 | 隐式 NULL | 组织性质：ENTERPRISE企业、REGULATOR行管 |
| area_id | BIGINT UNSIGNED | 无 | 是 | 隐式 NULL | 关联sys_address行政区域ID；逻辑关联 sys_address.id |
| org_code | VARCHAR(64) | 无 | 否 | 无 | 组织编码 |
| enterprise_code_key | VARCHAR(64) | 无 | 是 | 生成列 | 企业编码唯一约束键 |
| org_name | VARCHAR(128) | 无 | 否 | 无 | 组织名称 |
| contact_name | VARCHAR(64) | 无 | 是 | 隐式 NULL | 联系人 |
| contact_phone | VARCHAR(32) | 无 | 是 | 隐式 NULL | 联系电话 |
| address | VARCHAR(255) | 无 | 是 | 隐式 NULL | 地址 |
| status | VARCHAR(16) | 无 | 否 | 'ENABLED' | 状态 |
| sort_order | INT | 无 | 否 | 0 | 排序 |
| created_by | BIGINT | 无 | 是 | 隐式 NULL | 创建人；逻辑关联 sys_user.id（操作人标识） |
| updated_by | BIGINT | 无 | 是 | 隐式 NULL | 更新人；逻辑关联 sys_user.id（操作人标识） |
| deleted_by | BIGINT | 无 | 是 | 隐式 NULL | 删除人；逻辑关联 sys_user.id（操作人标识） |
| created_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |
| updated_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 更新时间；ON UPDATE CURRENT_TIMESTAMP(3) |
| deleted_at | DATETIME(3) | 无 | 是 | 隐式 NULL | 删除时间 |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | id | 唯一标识记录 |
| uk_org_enterprise_code | UNIQUE | enterprise_id, org_code | 约束字段组合唯一 |
| uk_org_enterprise_global_code | UNIQUE | enterprise_code_key | 约束字段组合唯一 |
| idx_org_parent | INDEX | enterprise_id, parent_id, sort_order | 支持所列字段组合的检索 |
| idx_org_type_status | INDEX | org_type, status | 支持所列字段组合的检索 |
| idx_org_enterprise_deleted | INDEX | enterprise_id, deleted_at | 支持所列字段组合的检索 |
| idx_org_nature_area | INDEX | organization_nature, area_id | 支持所列字段组合的检索 |

生成列 `enterprise_code_key`：`CASE WHEN org_type = 'ENTERPRISE' THEN org_code ELSE NULL END`，采用 STORED 存储。

结构依据：`backend/train-admin-service/src/main/resources/db/migration/V1__admin_schema.sql`、`backend/train-admin-service/src/main/resources/db/migration/V3__admin_soft_delete.sql`、`backend/train-admin-service/src/main/resources/db/migration/V5__organization_nature_and_area.sql`。

### sys_user 用户账号

所属数据库：`road_training_admin`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT | PK；无FK | 否 | 无 | 用户ID |
| enterprise_id | BIGINT | 无 | 是 | 隐式 NULL | 所属企业ID，平台用户为空；逻辑关联 train_org.id |
| org_id | BIGINT | 无 | 是 | 隐式 NULL | 主部门ID；逻辑关联 train_org.id |
| vehicle_id | BIGINT | 无 | 是 | 隐式 NULL | 绑定车辆ID；逻辑关联 train_vehicle.id |
| username | VARCHAR(64) | 无 | 否 | 无 | 全平台唯一用户名 |
| password_hash | VARCHAR(100) | 无 | 否 | 无 | BCrypt密码摘要 |
| display_name | VARCHAR(64) | 无 | 否 | 无 | 显示姓名 |
| phone | VARCHAR(32) | 无 | 是 | 隐式 NULL | 联系电话 |
| status | VARCHAR(16) | 无 | 否 | 'ENABLED' | 状态 |
| login_version | BIGINT | 无 | 否 | 1 | 登录版本 |
| must_change_password | TINYINT(1) | 无 | 否 | 1 | 是否必须修改密码 |
| platform_admin | TINYINT(1) | 无 | 否 | 0 | 是否平台超级管理员 |
| face_reference_object_id | BIGINT | 无 | 是 | 隐式 NULL | 人脸登记照存储对象ID；逻辑关联 train_storage_object.id |
| face_reference_updated_at | DATETIME(3) | 无 | 是 | 隐式 NULL | 人脸登记照更新时间 |
| platform_admin_key | TINYINT | 无 | 是 | 生成列 | 唯一平台超管约束键 |
| created_by | BIGINT | 无 | 是 | 隐式 NULL | 创建人；逻辑关联 sys_user.id（操作人标识） |
| updated_by | BIGINT | 无 | 是 | 隐式 NULL | 更新人；逻辑关联 sys_user.id（操作人标识） |
| created_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |
| updated_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 更新时间；ON UPDATE CURRENT_TIMESTAMP(3) |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | id | 唯一标识记录 |
| uk_user_username | UNIQUE | username | 约束字段组合唯一 |
| uk_user_single_platform_admin | UNIQUE | platform_admin_key | 约束字段组合唯一 |
| idx_user_enterprise_status | INDEX | enterprise_id, status | 支持所列字段组合的检索 |
| idx_user_org | INDEX | enterprise_id, org_id | 支持所列字段组合的检索 |
| idx_user_face_reference | INDEX | enterprise_id, face_reference_object_id | 支持所列字段组合的检索 |
| idx_user_enterprise_vehicle | INDEX | enterprise_id, vehicle_id | 支持所列字段组合的检索 |

生成列 `platform_admin_key`：`NULLIF(platform_admin, 0)`，采用 STORED 存储。

结构依据：`backend/train-admin-service/src/main/resources/db/migration/V1__admin_schema.sql`、`backend/train-admin-service/src/main/resources/db/migration/V9__face_reference_metadata.sql`、`backend/train-admin-service/src/main/resources/db/migration/V14__user_vehicle_binding.sql`。

### sys_role 角色

所属数据库：`road_training_admin`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT | PK；无FK | 否 | 无 | 角色ID |
| enterprise_id | BIGINT | 无 | 是 | 隐式 NULL | 所属企业ID，平台角色为空；逻辑关联 train_org.id |
| role_code | VARCHAR(64) | 无 | 否 | 无 | 角色编码 |
| role_name | VARCHAR(64) | 无 | 否 | 无 | 角色名称 |
| description | VARCHAR(255) | 无 | 是 | 隐式 NULL | 角色说明 |
| status | VARCHAR(16) | 无 | 否 | 'ENABLED' | 状态 |
| built_in | TINYINT(1) | 无 | 否 | 0 | 是否内置角色 |
| created_by | BIGINT | 无 | 是 | 隐式 NULL | 创建人；逻辑关联 sys_user.id（操作人标识） |
| updated_by | BIGINT | 无 | 是 | 隐式 NULL | 更新人；逻辑关联 sys_user.id（操作人标识） |
| deleted_by | BIGINT | 无 | 是 | 隐式 NULL | 删除人；逻辑关联 sys_user.id（操作人标识） |
| created_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |
| updated_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 更新时间；ON UPDATE CURRENT_TIMESTAMP(3) |
| deleted_at | DATETIME(3) | 无 | 是 | 隐式 NULL | 删除时间 |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | id | 唯一标识记录 |
| uk_role_enterprise_code | UNIQUE | enterprise_id, role_code | 约束字段组合唯一 |
| idx_role_enterprise_status | INDEX | enterprise_id, status | 支持所列字段组合的检索 |
| idx_role_enterprise_deleted | INDEX | enterprise_id, deleted_at | 支持所列字段组合的检索 |

结构依据：`backend/train-admin-service/src/main/resources/db/migration/V1__admin_schema.sql`、`backend/train-admin-service/src/main/resources/db/migration/V3__admin_soft_delete.sql`。

### sys_permission 固定权限目录

所属数据库：`road_training_admin`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT | PK；无FK | 否 | 无 | 权限ID |
| parent_id | BIGINT | 无 | 是 | 隐式 NULL | 父权限ID；逻辑关联 sys_permission.id |
| permission_code | VARCHAR(96) | 无 | 否 | 无 | 权限编码 |
| permission_name | VARCHAR(64) | 无 | 否 | 无 | 权限名称 |
| permission_type | VARCHAR(16) | 无 | 否 | 无 | MENU或ACTION |
| permission_scope | VARCHAR(16) | 无 | 否 | 无 | PLATFORM、ENTERPRISE或COMMON |
| sort_order | INT | 无 | 否 | 0 | 排序 |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | id | 唯一标识记录 |
| uk_permission_code | UNIQUE | permission_code | 约束字段组合唯一 |
| idx_permission_parent | INDEX | parent_id, sort_order | 支持所列字段组合的检索 |

结构依据：`backend/train-admin-service/src/main/resources/db/migration/V1__admin_schema.sql`。

### sys_user_role 用户角色关联

所属数据库：`road_training_admin`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| user_id | BIGINT | PK；无FK | 否 | 无 | 用户ID；逻辑关联 sys_user.id |
| role_id | BIGINT | PK；无FK | 否 | 无 | 角色ID；逻辑关联 sys_role.id |
| enterprise_id | BIGINT | 无 | 是 | 隐式 NULL | 所属企业ID；逻辑关联 train_org.id |
| created_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | user_id, role_id | 唯一标识记录 |
| idx_user_role_role | INDEX | role_id, user_id | 支持所列字段组合的检索 |
| idx_user_role_enterprise | INDEX | enterprise_id | 支持所列字段组合的检索 |

结构依据：`backend/train-admin-service/src/main/resources/db/migration/V1__admin_schema.sql`。

### sys_role_permission 角色权限关联

所属数据库：`road_training_admin`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| role_id | BIGINT | PK；无FK | 否 | 无 | 角色ID；逻辑关联 sys_role.id |
| permission_id | BIGINT | PK；无FK | 否 | 无 | 权限ID；逻辑关联 sys_permission.id |
| created_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | role_id, permission_id | 唯一标识记录 |
| idx_role_permission_permission | INDEX | permission_id, role_id | 支持所列字段组合的检索 |

结构依据：`backend/train-admin-service/src/main/resources/db/migration/V1__admin_schema.sql`。

### train_org_user 组织用户关系

所属数据库：`road_training_admin`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| user_id | BIGINT | PK；无FK | 否 | 无 | 用户ID；逻辑关联 sys_user.id |
| org_id | BIGINT | 无 | 否 | 无 | 组织ID；逻辑关联 train_org.id |
| enterprise_id | BIGINT | 无 | 否 | 无 | 所属企业ID；逻辑关联 train_org.id |
| is_primary | TINYINT(1) | 无 | 否 | 1 | 是否主部门 |
| created_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | user_id | 唯一标识记录 |
| idx_org_user_org | INDEX | enterprise_id, org_id | 支持所列字段组合的检索 |

结构依据：`backend/train-admin-service/src/main/resources/db/migration/V1__admin_schema.sql`。

### train_vehicle 企业车辆

所属数据库：`road_training_admin`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT | PK；无FK | 否 | 无 | 车辆ID |
| enterprise_id | BIGINT | 无 | 否 | 无 | 所属企业；逻辑关联 train_org.id |
| plate_number | VARCHAR(16) | 无 | 否 | 无 | 车牌号 |
| vehicle_type | VARCHAR(64) | 无 | 否 | 无 | 车辆类型 |
| org_id | BIGINT | 无 | 是 | 隐式 NULL | 所属部门；逻辑关联 train_org.id |
| status | VARCHAR(16) | 无 | 否 | 'ENABLED' | ENABLED或DISABLED |
| remark | VARCHAR(255) | 无 | 是 | 隐式 NULL | 备注 |
| created_by | BIGINT | 无 | 是 | 隐式 NULL | 创建人；逻辑关联 sys_user.id（操作人标识） |
| updated_by | BIGINT | 无 | 是 | 隐式 NULL | 更新人；逻辑关联 sys_user.id（操作人标识） |
| created_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |
| updated_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 更新时间；ON UPDATE CURRENT_TIMESTAMP(3) |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | id | 唯一标识记录 |
| uk_vehicle_enterprise_plate | UNIQUE | enterprise_id, plate_number | 约束字段组合唯一 |
| idx_vehicle_enterprise_status | INDEX | enterprise_id, status, created_at | 支持所列字段组合的检索 |
| idx_vehicle_org | INDEX | enterprise_id, org_id | 支持所列字段组合的检索 |

结构依据：`backend/train-admin-service/src/main/resources/db/migration/V12__vehicle_schema_and_permission.sql`。

### train_course 培训课程

所属数据库：`road_training_training`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT | PK；无FK | 否 | 无 | 课程ID |
| enterprise_id | BIGINT | 无 | 否 | 无 | 所属组织ID；逻辑关联 train_org.id |
| course_name | VARCHAR(128) | 无 | 否 | 无 | 课程名称 |
| description | VARCHAR(1000) | 无 | 是 | 隐式 NULL | 课程简介 |
| cover_object_id | BIGINT | 无 | 是 | 隐式 NULL | 封面存储对象ID；逻辑关联 train_storage_object.id |
| required_duration_seconds | INT | 无 | 否 | 无 | 规定时长（秒） |
| allow_seek | TINYINT(1) | 无 | 否 | 0 | 是否允许拖动视频 |
| progress_report_interval_seconds | INT | 无 | 否 | 20 | 进度上报间隔（秒） |
| study_tolerance_seconds | INT | 无 | 否 | 30 | 学时误差（秒） |
| status | VARCHAR(16) | 无 | 否 | 'DRAFT' | DRAFT、ENABLED或DISABLED |
| ever_enabled | TINYINT(1) | 无 | 否 | 0 | 是否曾经启用 |
| created_by | BIGINT | 无 | 否 | 无 | 创建人；逻辑关联 sys_user.id（操作人标识） |
| updated_by | BIGINT | 无 | 否 | 无 | 更新人；逻辑关联 sys_user.id（操作人标识） |
| deleted_by | BIGINT | 无 | 是 | 隐式 NULL | 删除人；逻辑关联 sys_user.id（操作人标识） |
| created_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |
| updated_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 更新时间；ON UPDATE CURRENT_TIMESTAMP(3) |
| deleted_at | DATETIME(3) | 无 | 是 | 隐式 NULL | 删除时间 |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | id | 唯一标识记录 |
| idx_course_enterprise_status | INDEX | enterprise_id, status, deleted_at | 支持所列字段组合的检索 |
| idx_course_enterprise_name | INDEX | enterprise_id, course_name, deleted_at | 支持所列字段组合的检索 |

结构依据：`backend/train-training-service/src/main/resources/db/migration/V1__training_course_schema.sql`。

### train_storage_object 对象存储元数据

所属数据库：`road_training_training`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT | PK；无FK | 否 | 无 | 存储对象ID |
| enterprise_id | BIGINT | 无 | 是 | 隐式 NULL | 所属组织ID，平台个人图片为空；逻辑关联 train_org.id |
| owner_user_id | BIGINT | 无 | 是 | 隐式 NULL | 私有对象所属用户ID；逻辑关联 sys_user.id |
| provider | VARCHAR(16) | 无 | 否 | 'ALIYUN_OSS' | 存储提供商 |
| bucket_name | VARCHAR(128) | 无 | 否 | 无 | Bucket名称 |
| object_key | VARCHAR(512) | 无 | 否 | 无 | 对象Key |
| original_filename | VARCHAR(255) | 无 | 否 | 无 | 原文件名 |
| object_type | VARCHAR(32) | 无 | 否 | 无 | COVER、VIDEO或FACE_REFERENCE；当前实现亦支持 LEARNING_PHOTO 学习照片 |
| content_type | VARCHAR(128) | 无 | 否 | 无 | 文件内容类型 |
| file_size | BIGINT | 无 | 否 | 无 | 文件大小（字节） |
| etag | VARCHAR(128) | 无 | 是 | 隐式 NULL | OSS ETag |
| status | VARCHAR(24) | 无 | 否 | 'ACTIVE' | ACTIVE、PENDING_DELETE、RETAINED或DELETED |
| created_by | BIGINT | 无 | 否 | 无 | 创建人；逻辑关联 sys_user.id（操作人标识） |
| updated_by | BIGINT | 无 | 否 | 无 | 更新人；逻辑关联 sys_user.id（操作人标识） |
| created_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |
| updated_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 更新时间；ON UPDATE CURRENT_TIMESTAMP(3) |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | id | 唯一标识记录 |
| uk_storage_bucket_object | UNIQUE | bucket_name, object_key | 约束字段组合唯一 |
| idx_storage_enterprise_status | INDEX | enterprise_id, status, updated_at | 支持所列字段组合的检索 |
| idx_storage_enterprise_type | INDEX | enterprise_id, object_type | 支持所列字段组合的检索 |
| idx_storage_owner_type | INDEX | enterprise_id, owner_user_id, object_type, status | 支持所列字段组合的检索 |

结构依据：`backend/train-training-service/src/main/resources/db/migration/V1__training_course_schema.sql`、`backend/train-training-service/src/main/resources/db/migration/V4__face_check_plan_and_private_image.sql`、`backend/train-training-service/src/main/resources/db/migration/V6__platform_profile_images.sql`。

### train_courseware 视频课件

所属数据库：`road_training_training`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT | PK；无FK | 否 | 无 | 课件ID |
| enterprise_id | BIGINT | 无 | 否 | 无 | 所属组织ID；逻辑关联 train_org.id |
| course_id | BIGINT | 无 | 否 | 无 | 课程ID；逻辑关联 train_course.id |
| storage_object_id | BIGINT | 无 | 否 | 无 | 存储对象ID；逻辑关联 train_storage_object.id |
| courseware_title | VARCHAR(128) | 无 | 否 | 无 | 课件标题 |
| duration_seconds | INT | 无 | 否 | 无 | 视频时长（秒） |
| sort_order | INT | 无 | 否 | 0 | 排序 |
| created_by | BIGINT | 无 | 否 | 无 | 创建人；逻辑关联 sys_user.id（操作人标识） |
| updated_by | BIGINT | 无 | 否 | 无 | 更新人；逻辑关联 sys_user.id（操作人标识） |
| deleted_by | BIGINT | 无 | 是 | 隐式 NULL | 删除人；逻辑关联 sys_user.id（操作人标识） |
| created_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |
| updated_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 更新时间；ON UPDATE CURRENT_TIMESTAMP(3) |
| deleted_at | DATETIME(3) | 无 | 是 | 隐式 NULL | 删除时间 |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | id | 唯一标识记录 |
| idx_courseware_course_order | INDEX | enterprise_id, course_id, deleted_at, sort_order | 支持所列字段组合的检索 |
| idx_courseware_storage | INDEX | storage_object_id | 支持所列字段组合的检索 |

结构依据：`backend/train-training-service/src/main/resources/db/migration/V1__training_course_schema.sql`。

### train_upload_session 浏览器直传会话

所属数据库：`road_training_training`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT | PK；无FK | 否 | 无 | 上传会话ID |
| enterprise_id | BIGINT | 无 | 否 | 无 | 所属组织ID；逻辑关联 train_org.id |
| course_id | BIGINT | 无 | 否 | 无 | 课程ID；逻辑关联 train_course.id |
| storage_object_id | BIGINT | 无 | 否 | 无 | 预分配存储对象ID；逻辑关联 train_storage_object.id |
| courseware_id | BIGINT | 无 | 是 | 隐式 NULL | 预分配课件ID；逻辑关联 train_courseware.id |
| upload_type | VARCHAR(16) | 无 | 否 | 无 | COVER或VIDEO |
| bucket_name | VARCHAR(128) | 无 | 否 | 无 | Bucket名称 |
| object_key | VARCHAR(512) | 无 | 否 | 无 | 对象Key |
| oss_upload_id | VARCHAR(256) | 无 | 是 | 隐式 NULL | OSS分片上传ID |
| original_filename | VARCHAR(255) | 无 | 否 | 无 | 原文件名 |
| expected_content_type | VARCHAR(128) | 无 | 否 | 无 | 预期内容类型 |
| expected_file_size | BIGINT | 无 | 否 | 无 | 预期文件大小 |
| client_last_modified | BIGINT | 无 | 是 | 隐式 NULL | 浏览器文件最后修改时间 |
| video_duration_seconds | INT | 无 | 是 | 隐式 NULL | 视频时长（秒） |
| courseware_title | VARCHAR(128) | 无 | 是 | 隐式 NULL | 课件标题 |
| part_size_bytes | BIGINT | 无 | 否 | 无 | 分片大小 |
| part_count | INT | 无 | 否 | 无 | 分片数量 |
| status | VARCHAR(24) | 无 | 否 | 'INITIATED' | INITIATED、COMPLETED、CANCELLED或EXPIRED |
| expires_at | DATETIME(3) | 无 | 否 | 无 | 过期时间 |
| completed_at | DATETIME(3) | 无 | 是 | 隐式 NULL | 完成时间 |
| created_by | BIGINT | 无 | 否 | 无 | 创建人；逻辑关联 sys_user.id（操作人标识） |
| updated_by | BIGINT | 无 | 否 | 无 | 更新人；逻辑关联 sys_user.id（操作人标识） |
| created_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |
| updated_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 更新时间；ON UPDATE CURRENT_TIMESTAMP(3) |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | id | 唯一标识记录 |
| idx_upload_enterprise_course | INDEX | enterprise_id, course_id, status | 支持所列字段组合的检索 |
| idx_upload_expiry | INDEX | status, expires_at | 支持所列字段组合的检索 |
| idx_upload_storage_object | INDEX | storage_object_id | 支持所列字段组合的检索 |

结构依据：`backend/train-training-service/src/main/resources/db/migration/V1__training_course_schema.sql`。

### train_plan 培训计划

所属数据库：`road_training_training`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT | PK；无FK | 否 | 无 | 培训计划ID |
| enterprise_id | BIGINT | 无 | 否 | 无 | 所属组织ID；逻辑关联 train_org.id |
| plan_name | VARCHAR(128) | 无 | 否 | 无 | 计划名称 |
| description | VARCHAR(1000) | 无 | 是 | 隐式 NULL | 计划说明 |
| start_at | DATETIME(3) | 无 | 否 | 无 | 开始时间 |
| end_at | DATETIME(3) | 无 | 否 | 无 | 结束时间 |
| status | VARCHAR(16) | 无 | 否 | 'DRAFT' | DRAFT、PUBLISHED、IN_PROGRESS、FINISHED或CANCELLED |
| exam_required | TINYINT(1) | 无 | 否 | 0 | 是否要求考试 |
| exam_paper_id | BIGINT | 无 | 是 | 隐式 NULL | 关联试卷ID；逻辑关联 exam_paper.id |
| exam_pass_score | INT | 无 | 是 | 隐式 NULL | 计划考试及格分快照 |
| exam_duration_minutes | INT | 无 | 是 | 隐式 NULL | 发布时考试时长快照（分钟） |
| face_check_enabled | TINYINT(1) | 无 | 否 | 0 | 是否启用人脸抽验 |
| face_check_min_interval_seconds | INT | 无 | 否 | 300 | 抽验最小有效学时间隔（秒） |
| face_check_max_interval_seconds | INT | 无 | 否 | 600 | 抽验最大有效学时间隔（秒） |
| face_check_timeout_seconds | INT | 无 | 否 | 60 | 单次抽验响应时限（秒） |
| face_check_max_attempts | INT | 无 | 否 | 3 | 单次抽验最多提交次数 |
| published_by | BIGINT | 无 | 是 | 隐式 NULL | 发布人；逻辑关联 sys_user.id（操作人标识） |
| published_at | DATETIME(3) | 无 | 是 | 隐式 NULL | 发布时间 |
| cancelled_by | BIGINT | 无 | 是 | 隐式 NULL | 取消人；逻辑关联 sys_user.id（操作人标识） |
| cancelled_at | DATETIME(3) | 无 | 是 | 隐式 NULL | 取消时间 |
| created_by | BIGINT | 无 | 否 | 无 | 创建人；逻辑关联 sys_user.id（操作人标识） |
| updated_by | BIGINT | 无 | 否 | 无 | 更新人；逻辑关联 sys_user.id（操作人标识） |
| deleted_by | BIGINT | 无 | 是 | 隐式 NULL | 删除人；逻辑关联 sys_user.id（操作人标识） |
| created_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |
| updated_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 更新时间；ON UPDATE CURRENT_TIMESTAMP(3) |
| deleted_at | DATETIME(3) | 无 | 是 | 隐式 NULL | 删除时间 |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | id | 唯一标识记录 |
| idx_plan_enterprise_status | INDEX | enterprise_id, status, deleted_at | 支持所列字段组合的检索 |
| idx_plan_enterprise_time | INDEX | enterprise_id, start_at, end_at, deleted_at | 支持所列字段组合的检索 |
| idx_plan_exam_paper | INDEX | enterprise_id, exam_paper_id | 支持所列字段组合的检索 |

结构依据：`backend/train-training-service/src/main/resources/db/migration/V2__training_plan_schema.sql`、`backend/train-training-service/src/main/resources/db/migration/V4__face_check_plan_and_private_image.sql`、`backend/train-training-service/src/main/resources/db/migration/V5__exam_schema.sql`。

### train_plan_course 培训计划课程规则快照

所属数据库：`road_training_training`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT | PK；无FK | 否 | 无 | 计划课程ID |
| enterprise_id | BIGINT | 无 | 否 | 无 | 所属组织ID；逻辑关联 train_org.id |
| plan_id | BIGINT | 无 | 否 | 无 | 培训计划ID；逻辑关联 train_plan.id |
| course_id | BIGINT | 无 | 否 | 无 | 来源课程ID；逻辑关联 train_course.id |
| course_name | VARCHAR(128) | 无 | 否 | 无 | 发布时课程名称快照 |
| required_duration_seconds | INT | 无 | 否 | 无 | 发布时规定时长快照 |
| allow_seek | TINYINT(1) | 无 | 否 | 无 | 发布时允许拖动规则快照 |
| progress_report_interval_seconds | INT | 无 | 否 | 无 | 发布时进度上报间隔快照 |
| study_tolerance_seconds | INT | 无 | 否 | 无 | 发布时学时误差快照 |
| sort_order | INT | 无 | 否 | 0 | 课程顺序 |
| created_by | BIGINT | 无 | 否 | 无 | 创建人；逻辑关联 sys_user.id（操作人标识） |
| updated_by | BIGINT | 无 | 否 | 无 | 更新人；逻辑关联 sys_user.id（操作人标识） |
| created_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |
| updated_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 更新时间；ON UPDATE CURRENT_TIMESTAMP(3) |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | id | 唯一标识记录 |
| uk_plan_course | UNIQUE | enterprise_id, plan_id, course_id | 约束字段组合唯一 |
| idx_plan_course_order | INDEX | enterprise_id, plan_id, sort_order | 支持所列字段组合的检索 |

结构依据：`backend/train-training-service/src/main/resources/db/migration/V2__training_plan_schema.sql`。

### train_plan_courseware_snapshot 培训计划课件清单快照

所属数据库：`road_training_training`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT | PK；无FK | 否 | 无 | 计划课件快照ID |
| enterprise_id | BIGINT | 无 | 否 | 无 | 所属组织ID；逻辑关联 train_org.id |
| plan_id | BIGINT | 无 | 否 | 无 | 培训计划ID；逻辑关联 train_plan.id |
| plan_course_id | BIGINT | 无 | 否 | 无 | 计划课程ID；逻辑关联 train_plan_course.id |
| course_id | BIGINT | 无 | 否 | 无 | 来源课程ID；逻辑关联 train_course.id |
| source_courseware_id | BIGINT | 无 | 否 | 无 | 来源课件ID；逻辑关联 train_courseware.id |
| storage_object_id | BIGINT | 无 | 否 | 无 | 历史OSS对象元数据ID；逻辑关联 train_storage_object.id |
| courseware_title | VARCHAR(128) | 无 | 否 | 无 | 发布时课件标题快照 |
| duration_seconds | INT | 无 | 否 | 无 | 发布时视频时长快照 |
| sort_order | INT | 无 | 否 | 0 | 发布时课件顺序快照 |
| created_by | BIGINT | 无 | 否 | 无 | 创建人；逻辑关联 sys_user.id（操作人标识） |
| updated_by | BIGINT | 无 | 否 | 无 | 更新人；逻辑关联 sys_user.id（操作人标识） |
| created_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |
| updated_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 更新时间；ON UPDATE CURRENT_TIMESTAMP(3) |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | id | 唯一标识记录 |
| uk_plan_courseware | UNIQUE | enterprise_id, plan_id, source_courseware_id | 约束字段组合唯一 |
| idx_plan_courseware_order | INDEX | enterprise_id, plan_course_id, sort_order | 支持所列字段组合的检索 |
| idx_plan_courseware_storage | INDEX | storage_object_id | 支持所列字段组合的检索 |

结构依据：`backend/train-training-service/src/main/resources/db/migration/V2__training_plan_schema.sql`。

### train_plan_user 培训计划学员任务

所属数据库：`road_training_training`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT | PK；无FK | 否 | 无 | 计划学员任务ID |
| enterprise_id | BIGINT | 无 | 否 | 无 | 所属组织ID；逻辑关联 train_org.id |
| plan_id | BIGINT | 无 | 否 | 无 | 培训计划ID；逻辑关联 train_plan.id |
| user_id | BIGINT | 无 | 否 | 无 | 学员用户ID；逻辑关联 sys_user.id |
| org_id | BIGINT | 无 | 是 | 隐式 NULL | 分配时部门ID快照；逻辑关联 train_org.id |
| org_name | VARCHAR(128) | 无 | 是 | 隐式 NULL | 分配时部门名称快照 |
| username | VARCHAR(64) | 无 | 否 | 无 | 分配时用户名快照 |
| display_name | VARCHAR(64) | 无 | 否 | 无 | 分配时姓名快照 |
| assignment_status | VARCHAR(16) | 无 | 否 | 'ASSIGNED' | ASSIGNED或CANCELLED |
| study_status | VARCHAR(24) | 无 | 否 | 'NOT_STARTED' | 学习状态 |
| exam_status | VARCHAR(24) | 无 | 否 | 'NOT_REQUIRED' | 考试状态 |
| completion_status | VARCHAR(24) | 无 | 否 | 'NOT_COMPLETED' | 完成状态 |
| completed_at | DATETIME(3) | 无 | 是 | 隐式 NULL | 完成时间 |
| created_by | BIGINT | 无 | 否 | 无 | 创建人；逻辑关联 sys_user.id（操作人标识） |
| updated_by | BIGINT | 无 | 否 | 无 | 更新人；逻辑关联 sys_user.id（操作人标识） |
| created_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |
| updated_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 更新时间；ON UPDATE CURRENT_TIMESTAMP(3) |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | id | 唯一标识记录 |
| uk_plan_user | UNIQUE | enterprise_id, plan_id, user_id | 约束字段组合唯一 |
| idx_plan_user_student | INDEX | enterprise_id, user_id, assignment_status | 支持所列字段组合的检索 |
| idx_plan_user_status | INDEX | enterprise_id, plan_id, completion_status | 支持所列字段组合的检索 |

结构依据：`backend/train-training-service/src/main/resources/db/migration/V2__training_plan_schema.sql`。

### mq_consume_log RabbitMQ消费幂等日志

所属数据库：`road_training_training`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT | PK；无FK | 否 | 无 | 消费记录ID |
| consumer_name | VARCHAR(64) | 无 | 否 | 无 | 消费者名称 |
| event_id | VARCHAR(64) | 无 | 否 | 无 | 事件ID |
| event_type | VARCHAR(64) | 无 | 否 | 无 | 事件类型 |
| processed_at | DATETIME(3) | 无 | 否 | 无 | 处理时间 |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | id | 唯一标识记录 |
| uk_consume_event | UNIQUE | consumer_name, event_id | 约束字段组合唯一 |
| idx_consume_processed | INDEX | processed_at | 支持所列字段组合的检索 |

结构依据：`backend/train-training-service/src/main/resources/db/migration/V3__learning_task_projection.sql`。

### train_private_image_upload_session 私有图片浏览器直传会话

所属数据库：`road_training_training`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT | PK；无FK | 否 | 无 | 私有图片上传会话ID |
| enterprise_id | BIGINT | 无 | 是 | 隐式 NULL | 所属组织ID，平台个人图片为空；逻辑关联 train_org.id |
| owner_user_id | BIGINT | 无 | 否 | 无 | 私有图片所属用户ID；逻辑关联 sys_user.id |
| storage_object_id | BIGINT | 无 | 否 | 无 | 预分配存储对象ID；逻辑关联 train_storage_object.id |
| upload_type | VARCHAR(32) | 无 | 否 | 无 | FACE_REFERENCE；当前实现亦支持 LEARNING_PHOTO 学习照片 |
| bucket_name | VARCHAR(128) | 无 | 否 | 无 | Bucket名称 |
| object_key | VARCHAR(512) | 无 | 否 | 无 | 对象Key |
| original_filename | VARCHAR(255) | 无 | 否 | 无 | 原文件名 |
| expected_content_type | VARCHAR(128) | 无 | 否 | 无 | 预期内容类型 |
| expected_file_size | BIGINT | 无 | 否 | 无 | 预期文件大小 |
| client_last_modified | BIGINT | 无 | 是 | 隐式 NULL | 浏览器文件最后修改时间 |
| status | VARCHAR(24) | 无 | 否 | 'INITIATED' | INITIATED、COMPLETED、CANCELLED或EXPIRED |
| expires_at | DATETIME(3) | 无 | 否 | 无 | 过期时间 |
| completed_at | DATETIME(3) | 无 | 是 | 隐式 NULL | 完成时间 |
| created_by | BIGINT | 无 | 否 | 无 | 创建人；逻辑关联 sys_user.id（操作人标识） |
| updated_by | BIGINT | 无 | 否 | 无 | 更新人；逻辑关联 sys_user.id（操作人标识） |
| created_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |
| updated_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 更新时间；ON UPDATE CURRENT_TIMESTAMP(3) |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | id | 唯一标识记录 |
| idx_private_upload_owner | INDEX | enterprise_id, owner_user_id, status | 支持所列字段组合的检索 |
| idx_private_upload_expiry | INDEX | status, expires_at | 支持所列字段组合的检索 |
| idx_private_upload_storage | INDEX | storage_object_id | 支持所列字段组合的检索 |

结构依据：`backend/train-training-service/src/main/resources/db/migration/V4__face_check_plan_and_private_image.sql`、`backend/train-training-service/src/main/resources/db/migration/V6__platform_profile_images.sql`。

### exam_question 考试题库

所属数据库：`road_training_training`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT | PK；无FK | 否 | 无 | 题目ID |
| enterprise_id | BIGINT | 无 | 否 | 无 | 所属组织ID；逻辑关联 train_org.id |
| question_type | VARCHAR(24) | 无 | 否 | 无 | SINGLE_CHOICE或JUDGMENT |
| content | VARCHAR(1000) | 无 | 否 | 无 | 题干 |
| options_json | LONGTEXT | 无 | 否 | 无 | 选项JSON |
| correct_answer | VARCHAR(16) | 无 | 否 | 无 | 标准答案 |
| analysis | VARCHAR(1000) | 无 | 是 | 隐式 NULL | 答案解析 |
| status | VARCHAR(16) | 无 | 否 | 'ENABLED' | ENABLED或DISABLED |
| created_by | BIGINT | 无 | 否 | 无 | 创建人；逻辑关联 sys_user.id（操作人标识） |
| updated_by | BIGINT | 无 | 否 | 无 | 更新人；逻辑关联 sys_user.id（操作人标识） |
| deleted_by | BIGINT | 无 | 是 | 隐式 NULL | 删除人；逻辑关联 sys_user.id（操作人标识） |
| created_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |
| updated_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 更新时间；ON UPDATE CURRENT_TIMESTAMP(3) |
| deleted_at | DATETIME(3) | 无 | 是 | 隐式 NULL | 删除时间 |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | id | 唯一标识记录 |
| idx_exam_question_enterprise | INDEX | enterprise_id, status, question_type, deleted_at | 支持所列字段组合的检索 |

结构依据：`backend/train-training-service/src/main/resources/db/migration/V5__exam_schema.sql`。

### exam_paper 考试试卷

所属数据库：`road_training_training`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT | PK；无FK | 否 | 无 | 试卷ID |
| enterprise_id | BIGINT | 无 | 否 | 无 | 所属组织ID；逻辑关联 train_org.id |
| paper_name | VARCHAR(128) | 无 | 否 | 无 | 试卷名称 |
| description | VARCHAR(1000) | 无 | 是 | 隐式 NULL | 试卷说明 |
| duration_minutes | INT | 无 | 否 | 无 | 考试时长（分钟） |
| pass_score | INT | 无 | 否 | 无 | 默认及格分 |
| question_score | INT | 无 | 否 | 无 | 每题分值 |
| manual_question_count | INT | 无 | 否 | 0 | 手工选题数 |
| random_question_count | INT | 无 | 否 | 0 | 随机补齐数 |
| total_score | INT | 无 | 否 | 0 | 总分 |
| status | VARCHAR(16) | 无 | 否 | 'DRAFT' | DRAFT或ENABLED |
| enabled_by | BIGINT | 无 | 是 | 隐式 NULL | 启用人；逻辑关联 sys_user.id（操作人标识） |
| enabled_at | DATETIME(3) | 无 | 是 | 隐式 NULL | 启用时间 |
| created_by | BIGINT | 无 | 否 | 无 | 创建人；逻辑关联 sys_user.id（操作人标识） |
| updated_by | BIGINT | 无 | 否 | 无 | 更新人；逻辑关联 sys_user.id（操作人标识） |
| deleted_by | BIGINT | 无 | 是 | 隐式 NULL | 删除人；逻辑关联 sys_user.id（操作人标识） |
| created_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |
| updated_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 更新时间；ON UPDATE CURRENT_TIMESTAMP(3) |
| deleted_at | DATETIME(3) | 无 | 是 | 隐式 NULL | 删除时间 |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | id | 唯一标识记录 |
| idx_exam_paper_enterprise | INDEX | enterprise_id, status, deleted_at | 支持所列字段组合的检索 |

结构依据：`backend/train-training-service/src/main/resources/db/migration/V5__exam_schema.sql`。

### exam_paper_question 试卷固化题目

所属数据库：`road_training_training`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT | PK；无FK | 否 | 无 | 试卷题目ID |
| enterprise_id | BIGINT | 无 | 否 | 无 | 所属组织ID；逻辑关联 train_org.id |
| paper_id | BIGINT | 无 | 否 | 无 | 试卷ID；逻辑关联 exam_paper.id |
| source_question_id | BIGINT | 无 | 否 | 无 | 来源题目ID；逻辑关联 exam_question.id |
| selection_type | VARCHAR(16) | 无 | 否 | 无 | MANUAL或RANDOM |
| question_type | VARCHAR(24) | 无 | 否 | 无 | 题型快照 |
| content | VARCHAR(1000) | 无 | 否 | 无 | 题干快照 |
| options_json | LONGTEXT | 无 | 否 | 无 | 选项快照JSON |
| correct_answer | VARCHAR(16) | 无 | 否 | 无 | 标准答案快照 |
| analysis | VARCHAR(1000) | 无 | 是 | 隐式 NULL | 答案解析快照 |
| score | INT | 无 | 否 | 无 | 题目分值快照 |
| sort_order | INT | 无 | 否 | 无 | 题目顺序 |
| created_by | BIGINT | 无 | 否 | 无 | 创建人；逻辑关联 sys_user.id（操作人标识） |
| updated_by | BIGINT | 无 | 否 | 无 | 更新人；逻辑关联 sys_user.id（操作人标识） |
| created_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |
| updated_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 更新时间；ON UPDATE CURRENT_TIMESTAMP(3) |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | id | 唯一标识记录 |
| uk_exam_paper_question | UNIQUE | enterprise_id, paper_id, source_question_id | 约束字段组合唯一 |
| idx_exam_paper_question_order | INDEX | enterprise_id, paper_id, sort_order | 支持所列字段组合的检索 |

结构依据：`backend/train-training-service/src/main/resources/db/migration/V5__exam_schema.sql`。

### exam_record 学员考试记录

所属数据库：`road_training_training`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT | PK；无FK | 否 | 无 | 考试记录ID |
| enterprise_id | BIGINT | 无 | 否 | 无 | 所属组织ID；逻辑关联 train_org.id |
| task_id | BIGINT | 无 | 否 | 无 | 培训任务ID；逻辑关联 train_plan_user.id |
| plan_id | BIGINT | 无 | 否 | 无 | 培训计划ID；逻辑关联 train_plan.id |
| user_id | BIGINT | 无 | 否 | 无 | 学员ID；逻辑关联 sys_user.id |
| paper_id | BIGINT | 无 | 否 | 无 | 试卷ID；逻辑关联 exam_paper.id |
| paper_name | VARCHAR(128) | 无 | 否 | 无 | 试卷名称快照 |
| duration_minutes | INT | 无 | 否 | 无 | 考试时长快照 |
| pass_score | INT | 无 | 否 | 无 | 计划及格分快照 |
| total_score | INT | 无 | 否 | 无 | 试卷总分快照 |
| status | VARCHAR(20) | 无 | 否 | 无 | IN_PROGRESS、SUBMITTED或TIMEOUT |
| started_at | DATETIME(3) | 无 | 否 | 无 | 开始时间 |
| deadline_at | DATETIME(3) | 无 | 否 | 无 | 截止时间 |
| submitted_at | DATETIME(3) | 无 | 是 | 隐式 NULL | 提交或超时时间 |
| score | INT | 无 | 是 | 隐式 NULL | 考试得分 |
| passed | TINYINT(1) | 无 | 是 | 隐式 NULL | 是否及格 |
| created_by | BIGINT | 无 | 否 | 无 | 创建人；逻辑关联 sys_user.id（操作人标识） |
| updated_by | BIGINT | 无 | 否 | 无 | 更新人；逻辑关联 sys_user.id（操作人标识） |
| created_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |
| updated_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 更新时间；ON UPDATE CURRENT_TIMESTAMP(3) |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | id | 唯一标识记录 |
| uk_exam_record_task | UNIQUE | enterprise_id, task_id | 约束字段组合唯一 |
| idx_exam_record_owner | INDEX | enterprise_id, user_id, plan_id | 支持所列字段组合的检索 |
| idx_exam_record_timeout | INDEX | status, deadline_at | 支持所列字段组合的检索 |

结构依据：`backend/train-training-service/src/main/resources/db/migration/V5__exam_schema.sql`。

### exam_answer 学员考试答案

所属数据库：`road_training_training`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT | PK；无FK | 否 | 无 | 考试答案ID |
| enterprise_id | BIGINT | 无 | 否 | 无 | 所属组织ID；逻辑关联 train_org.id |
| record_id | BIGINT | 无 | 否 | 无 | 考试记录ID；逻辑关联 exam_record.id |
| paper_question_id | BIGINT | 无 | 否 | 无 | 试卷题目ID；逻辑关联 exam_paper_question.id |
| answer | VARCHAR(16) | 无 | 否 | 无 | 学员答案 |
| correct | TINYINT(1) | 无 | 是 | 隐式 NULL | 是否正确，交卷后写入 |
| score | INT | 无 | 是 | 隐式 NULL | 本题得分，交卷后写入 |
| answered_at | DATETIME(3) | 无 | 否 | 无 | 最后答题时间 |
| created_by | BIGINT | 无 | 否 | 无 | 创建人；逻辑关联 sys_user.id（操作人标识） |
| updated_by | BIGINT | 无 | 否 | 无 | 更新人；逻辑关联 sys_user.id（操作人标识） |
| created_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |
| updated_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 更新时间；ON UPDATE CURRENT_TIMESTAMP(3) |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | id | 唯一标识记录 |
| uk_exam_answer_question | UNIQUE | enterprise_id, record_id, paper_question_id | 约束字段组合唯一 |
| idx_exam_answer_record | INDEX | enterprise_id, record_id | 支持所列字段组合的检索 |

结构依据：`backend/train-training-service/src/main/resources/db/migration/V5__exam_schema.sql`。

### study_session 在线学习会话

所属数据库：`road_training_learning`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT | PK；无FK | 否 | 无 | 学习会话ID |
| enterprise_id | BIGINT | 无 | 否 | 无 | 所属组织ID；逻辑关联 train_org.id |
| user_id | BIGINT | 无 | 否 | 无 | 学员ID；逻辑关联 sys_user.id |
| task_id | BIGINT | 无 | 否 | 无 | 培训任务ID；逻辑关联 train_plan_user.id |
| plan_id | BIGINT | 无 | 否 | 无 | 培训计划ID；逻辑关联 train_plan.id |
| plan_course_id | BIGINT | 无 | 否 | 无 | 计划课程ID；逻辑关联 train_plan_course.id |
| client_instance_id | VARCHAR(64) | 无 | 否 | 无 | 浏览器实例ID |
| course_name | VARCHAR(128) | 无 | 否 | 无 | 课程名称快照 |
| sort_order | INT | 无 | 否 | 无 | 课程顺序快照 |
| plan_end_at | DATETIME(3) | 无 | 否 | 无 | 计划结束时间快照 |
| status | VARCHAR(20) | 无 | 否 | 无 | 会话状态 |
| current_courseware_snapshot_id | BIGINT | 无 | 是 | 隐式 NULL | 当前课件快照ID；逻辑关联 train_plan_courseware_snapshot.id |
| last_sequence | BIGINT | 无 | 否 | 0 | 最后接受事件序号 |
| last_confirmed_position_ms | BIGINT | 无 | 否 | 0 | 当前课件确认位置 |
| last_event_at | DATETIME(3) | 无 | 是 | 隐式 NULL | 最后事件时间 |
| signed_in_at | DATETIME(3) | 无 | 是 | 隐式 NULL | 签到时间 |
| started_at | DATETIME(3) | 无 | 是 | 隐式 NULL | 首次学习时间 |
| paused_at | DATETIME(3) | 无 | 是 | 隐式 NULL | 暂停时间 |
| completed_at | DATETIME(3) | 无 | 是 | 隐式 NULL | 课程完成时间 |
| signed_out_at | DATETIME(3) | 无 | 是 | 隐式 NULL | 签退时间 |
| terminated_at | DATETIME(3) | 无 | 是 | 隐式 NULL | 终止时间 |
| termination_reason | VARCHAR(64) | 无 | 是 | 隐式 NULL | 终止原因 |
| face_check_enabled | TINYINT(1) | 无 | 否 | 0 | 是否启用人脸抽验 |
| face_check_min_interval_seconds | INT | 无 | 否 | 300 | 最小抽验间隔秒数快照 |
| face_check_max_interval_seconds | INT | 无 | 否 | 600 | 最大抽验间隔秒数快照 |
| face_check_timeout_seconds | INT | 无 | 否 | 60 | 单次抽验响应超时秒数快照 |
| face_check_max_attempts | INT | 无 | 否 | 3 | 单次抽验最多提交次数快照 |
| next_face_check_effective_duration_ms | BIGINT | 无 | 是 | 隐式 NULL | 下次抽验对应的累计有效学时阈值 |
| version | INT | 无 | 否 | 0 | 乐观锁版本 |
| created_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |
| updated_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 更新时间；ON UPDATE CURRENT_TIMESTAMP(3) |
| active_user_id | BIGINT | 无 | 是 | 生成列 | 活动会话唯一约束生成列 |
| attendance_action | VARCHAR(16) | 无 | 是 | 隐式 NULL | 当次人脸核验对应动作 |
| attendance_sequence | BIGINT | 无 | 是 | 隐式 NULL | 核验凭据绑定的下一事件序号 |
| attendance_verified_at | DATETIME(3) | 无 | 是 | 隐式 NULL | 当次人脸核验通过时间 |
| attendance_photo_object_id | BIGINT | 无 | 是 | 隐式 NULL | 待消费的人脸验证照片对象ID；逻辑关联 train_storage_object.id |
| sign_in_photo_object_id | BIGINT | 无 | 是 | 隐式 NULL | 签到照片对象ID；逻辑关联 train_storage_object.id |
| sign_out_photo_object_id | BIGINT | 无 | 是 | 隐式 NULL | 签退照片对象ID；逻辑关联 train_storage_object.id |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | id | 唯一标识记录 |
| idx_study_session_owner | INDEX | enterprise_id, user_id, created_at | 支持所列字段组合的检索 |
| idx_study_session_course | INDEX | enterprise_id, plan_id, plan_course_id, user_id | 支持所列字段组合的检索 |
| idx_study_session_timeout | INDEX | status, last_event_at | 支持所列字段组合的检索 |
| uk_study_session_active_user | UNIQUE | enterprise_id, active_user_id | 约束字段组合唯一 |

生成列 `active_user_id`：`CASE WHEN status IN ('CREATED', 'SIGNED_IN', 'STUDYING', 'PAUSED', 'FACE_PENDING') THEN user_id ELSE NULL END`，采用 STORED 存储。

结构依据：`backend/train-learning-service/src/main/resources/db/migration/V1__learning_schema.sql`、`backend/train-learning-service/src/main/resources/db/migration/V3__face_check_schema.sql`、`backend/train-learning-service/src/main/resources/db/migration/V4__attendance_face_verification.sql`、`backend/train-learning-service/src/main/resources/db/migration/V5__learning_record_photos.sql`。

### study_progress 计划课程学习进度

所属数据库：`road_training_learning`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT | PK；无FK | 否 | 无 | 课程学习进度ID |
| enterprise_id | BIGINT | 无 | 否 | 无 | 所属组织ID；逻辑关联 train_org.id |
| user_id | BIGINT | 无 | 否 | 无 | 学员ID；逻辑关联 sys_user.id |
| task_id | BIGINT | 无 | 否 | 无 | 培训任务ID；逻辑关联 train_plan_user.id |
| plan_id | BIGINT | 无 | 否 | 无 | 培训计划ID；逻辑关联 train_plan.id |
| plan_course_id | BIGINT | 无 | 否 | 无 | 计划课程ID；逻辑关联 train_plan_course.id |
| course_name | VARCHAR(128) | 无 | 否 | 无 | 课程名称快照 |
| sort_order | INT | 无 | 否 | 无 | 课程顺序快照 |
| required_duration_ms | BIGINT | 无 | 否 | 无 | 规定学时毫秒 |
| effective_duration_ms | BIGINT | 无 | 否 | 0 | 有效学时毫秒 |
| allow_seek | TINYINT(1) | 无 | 否 | 无 | 是否允许拖动 |
| progress_report_interval_seconds | INT | 无 | 否 | 无 | 上报间隔快照 |
| study_tolerance_seconds | INT | 无 | 否 | 无 | 学时误差快照 |
| status | VARCHAR(20) | 无 | 否 | 'NOT_STARTED' | 学习状态 |
| completed_at | DATETIME(3) | 无 | 是 | 隐式 NULL | 完成时间 |
| version | INT | 无 | 否 | 0 | 乐观锁版本 |
| created_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |
| updated_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 更新时间；ON UPDATE CURRENT_TIMESTAMP(3) |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | id | 唯一标识记录 |
| uk_study_progress_course | UNIQUE | enterprise_id, user_id, plan_id, plan_course_id | 约束字段组合唯一 |
| idx_study_progress_task | INDEX | enterprise_id, task_id, status | 支持所列字段组合的检索 |

结构依据：`backend/train-learning-service/src/main/resources/db/migration/V1__learning_schema.sql`。

### study_courseware_progress 计划课件学习进度

所属数据库：`road_training_learning`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT | PK；无FK | 否 | 无 | 课件学习进度ID |
| enterprise_id | BIGINT | 无 | 否 | 无 | 所属组织ID；逻辑关联 train_org.id |
| user_id | BIGINT | 无 | 否 | 无 | 学员ID；逻辑关联 sys_user.id |
| task_id | BIGINT | 无 | 否 | 无 | 培训任务ID；逻辑关联 train_plan_user.id |
| plan_id | BIGINT | 无 | 否 | 无 | 培训计划ID；逻辑关联 train_plan.id |
| plan_course_id | BIGINT | 无 | 否 | 无 | 计划课程ID；逻辑关联 train_plan_course.id |
| courseware_snapshot_id | BIGINT | 无 | 否 | 无 | 课件快照ID；逻辑关联 train_plan_courseware_snapshot.id |
| courseware_title | VARCHAR(128) | 无 | 否 | 无 | 课件标题快照 |
| sort_order | INT | 无 | 否 | 无 | 课件顺序快照 |
| duration_ms | BIGINT | 无 | 否 | 无 | 视频时长毫秒 |
| confirmed_position_ms | BIGINT | 无 | 否 | 0 | 最近确认位置 |
| max_confirmed_position_ms | BIGINT | 无 | 否 | 0 | 最大确认位置 |
| status | VARCHAR(20) | 无 | 否 | 'NOT_STARTED' | 学习状态 |
| completed_at | DATETIME(3) | 无 | 是 | 隐式 NULL | 完成时间 |
| version | INT | 无 | 否 | 0 | 乐观锁版本 |
| created_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |
| updated_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 更新时间；ON UPDATE CURRENT_TIMESTAMP(3) |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | id | 唯一标识记录 |
| uk_courseware_progress | UNIQUE | enterprise_id, user_id, plan_course_id, courseware_snapshot_id | 约束字段组合唯一 |
| idx_courseware_progress_order | INDEX | enterprise_id, user_id, plan_course_id, sort_order | 支持所列字段组合的检索 |

结构依据：`backend/train-learning-service/src/main/resources/db/migration/V1__learning_schema.sql`。

### study_event_log 关键学习事件日志

所属数据库：`road_training_learning`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT | PK；无FK | 否 | 无 | 学习事件ID |
| enterprise_id | BIGINT | 无 | 否 | 无 | 所属组织ID；逻辑关联 train_org.id |
| user_id | BIGINT | 无 | 否 | 无 | 学员ID；逻辑关联 sys_user.id |
| session_id | BIGINT | 无 | 否 | 无 | 学习会话ID；逻辑关联 study_session.id |
| request_id | VARCHAR(64) | 无 | 否 | 无 | 幂等请求ID |
| sequence_no | BIGINT | 无 | 否 | 无 | 事件序号 |
| event_type | VARCHAR(20) | 无 | 否 | 无 | 事件类型 |
| from_status | VARCHAR(20) | 无 | 否 | 无 | 转换前状态 |
| to_status | VARCHAR(20) | 无 | 否 | 无 | 转换后状态 |
| courseware_snapshot_id | BIGINT | 无 | 是 | 隐式 NULL | 课件快照ID；逻辑关联 train_plan_courseware_snapshot.id |
| reported_position_ms | BIGINT | 无 | 否 | 0 | 前端上报位置 |
| confirmed_position_ms | BIGINT | 无 | 否 | 0 | 服务端确认位置 |
| credited_duration_ms | BIGINT | 无 | 否 | 0 | 本次有效学时 |
| result_code | VARCHAR(16) | 无 | 否 | 无 | 处理结果码 |
| response_payload | LONGTEXT | 无 | 否 | 无 | 幂等响应JSON |
| server_time | DATETIME(3) | 无 | 否 | 无 | 服务端处理时间 |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | id | 唯一标识记录 |
| uk_study_event_request | UNIQUE | session_id, request_id | 约束字段组合唯一 |
| uk_study_event_sequence | UNIQUE | session_id, sequence_no | 约束字段组合唯一 |
| idx_study_event_time | INDEX | enterprise_id, session_id, server_time | 支持所列字段组合的检索 |

结构依据：`backend/train-learning-service/src/main/resources/db/migration/V1__learning_schema.sql`。

### mq_outbox 学习事件可靠消息Outbox

所属数据库：`road_training_learning`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT | PK；无FK | 否 | 无 | Outbox记录ID |
| event_id | VARCHAR(64) | 无 | 否 | 无 | 全局事件ID |
| business_key | VARCHAR(128) | 无 | 否 | 无 | 业务幂等键 |
| aggregate_type | VARCHAR(32) | 无 | 否 | 无 | 聚合类型 |
| aggregate_id | BIGINT | 无 | 否 | 无 | 聚合ID |
| routing_key | VARCHAR(128) | 无 | 否 | 无 | RabbitMQ路由键 |
| payload | LONGTEXT | 无 | 否 | 无 | 事件JSON |
| status | VARCHAR(16) | 无 | 否 | 'PENDING' | PENDING、SENT或FAILED |
| retry_count | INT | 无 | 否 | 0 | 重试次数 |
| next_retry_at | DATETIME(3) | 无 | 否 | 无 | 下次重试时间 |
| last_error | VARCHAR(500) | 无 | 是 | 隐式 NULL | 最后错误摘要 |
| created_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |
| sent_at | DATETIME(3) | 无 | 是 | 隐式 NULL | 发送时间 |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | id | 唯一标识记录 |
| uk_outbox_event | UNIQUE | event_id | 约束字段组合唯一 |
| uk_outbox_business | UNIQUE | business_key | 约束字段组合唯一 |
| idx_outbox_pending | INDEX | status, next_retry_at, id | 支持所列字段组合的检索 |

结构依据：`backend/train-learning-service/src/main/resources/db/migration/V2__learning_outbox.sql`。

### face_check_task 学习人脸抽验任务

所属数据库：`road_training_learning`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT | PK；无FK | 否 | 无 | 人脸抽验任务ID |
| enterprise_id | BIGINT | 无 | 否 | 无 | 所属组织ID；逻辑关联 train_org.id |
| user_id | BIGINT | 无 | 否 | 无 | 学员ID；逻辑关联 sys_user.id |
| session_id | BIGINT | 无 | 否 | 无 | 学习会话ID；逻辑关联 study_session.id |
| training_task_id | BIGINT | 无 | 否 | 无 | 培训任务ID；逻辑关联 train_plan_user.id |
| plan_id | BIGINT | 无 | 否 | 无 | 培训计划ID；逻辑关联 train_plan.id |
| plan_course_id | BIGINT | 无 | 否 | 无 | 计划课程ID；逻辑关联 train_plan_course.id |
| status | VARCHAR(20) | 无 | 否 | 无 | PENDING、PASSED、FAILED或TIMED_OUT |
| triggered_at | DATETIME(3) | 无 | 否 | 无 | 触发时间 |
| deadline_at | DATETIME(3) | 无 | 否 | 无 | 提交截止时间 |
| attempt_count | INT | 无 | 否 | 0 | 已提交次数 |
| max_attempts | INT | 无 | 否 | 无 | 最多提交次数 |
| result | VARCHAR(32) | 无 | 是 | 隐式 NULL | 最终或最近一次核验结果 |
| failure_reason | VARCHAR(64) | 无 | 是 | 隐式 NULL | 失败原因 |
| similarity | DECIMAL(9,6) | 无 | 是 | 隐式 NULL | SFace余弦相似度 |
| completed_at | DATETIME(3) | 无 | 是 | 隐式 NULL | 终态时间 |
| version | INT | 无 | 否 | 0 | 乐观锁版本 |
| created_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |
| updated_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 更新时间；ON UPDATE CURRENT_TIMESTAMP(3) |
| pending_session_id | BIGINT | 无 | 是 | 生成列 | 待处理抽验任务唯一约束生成列 |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | id | 唯一标识记录 |
| uk_face_check_pending_session | UNIQUE | pending_session_id | 约束字段组合唯一 |
| idx_face_check_owner | INDEX | enterprise_id, user_id, session_id, created_at | 支持所列字段组合的检索 |
| idx_face_check_timeout | INDEX | status, deadline_at, id | 支持所列字段组合的检索 |

生成列 `pending_session_id`：`CASE WHEN status = 'PENDING' THEN session_id ELSE NULL END`，采用 STORED 存储。

结构依据：`backend/train-learning-service/src/main/resources/db/migration/V3__face_check_schema.sql`。

### face_check_log 学习人脸抽验尝试日志

所属数据库：`road_training_learning`。

存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT | PK；无FK | 否 | 无 | 人脸抽验尝试日志ID |
| enterprise_id | BIGINT | 无 | 否 | 无 | 所属组织ID；逻辑关联 train_org.id |
| user_id | BIGINT | 无 | 否 | 无 | 学员ID；逻辑关联 sys_user.id |
| task_id | BIGINT | 无 | 否 | 无 | 人脸抽验任务ID；逻辑关联 face_check_task.id |
| session_id | BIGINT | 无 | 否 | 无 | 学习会话ID；逻辑关联 study_session.id |
| request_id | VARCHAR(64) | 无 | 否 | 无 | 幂等请求ID |
| attempt_no | INT | 无 | 否 | 无 | 提交序号 |
| result | VARCHAR(32) | 无 | 否 | 无 | 核验结果 |
| failure_reason | VARCHAR(64) | 无 | 是 | 隐式 NULL | 失败原因 |
| similarity | DECIMAL(9,6) | 无 | 是 | 隐式 NULL | SFace余弦相似度 |
| elapsed_ms | BIGINT | 无 | 否 | 0 | 核验处理耗时 |
| image_sha256 | CHAR(64) | 无 | 否 | 无 | 抽验照片SHA-256摘要 |
| response_payload | LONGTEXT | 无 | 否 | 无 | 幂等响应JSON |
| created_at | DATETIME(3) | 无 | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |
| photo_object_id | BIGINT | 无 | 是 | 隐式 NULL | 本次抽验提交照片对象ID；逻辑关联 train_storage_object.id |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | id | 唯一标识记录 |
| uk_face_check_log_request | UNIQUE | task_id, request_id | 约束字段组合唯一 |
| uk_face_check_log_attempt | UNIQUE | task_id, attempt_no | 约束字段组合唯一 |
| idx_face_check_log_owner | INDEX | enterprise_id, user_id, session_id, created_at | 支持所列字段组合的检索 |

结构依据：`backend/train-learning-service/src/main/resources/db/migration/V3__face_check_schema.sql`、`backend/train-learning-service/src/main/resources/db/migration/V5__learning_record_photos.sql`。

### sys_address 中国行政地区表

所属数据库：`road_training_admin`。

存储引擎：MyISAM；字符集：utf8mb3；排序规则：DDL未显式声明表级值。

外部行政区域表不由Flyway创建；表级排序规则未显式声明。AUTO_INCREMENT=4063是DDL导出时的自增计数器值，不是记录数或字段默认值。

| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PK；无FK | 否 | 无 | 地址ID；AUTO_INCREMENT自增 |
| level | TINYINT UNSIGNED | 无 | 否 | 无 | 层级 |
| parent_code | VARCHAR(64) | 无 | 否 | '0' | 父级行政代码；逻辑关联 sys_address.area_code（根级使用代码0）；CHARACTER SET utf8mb3；COLLATE utf8mb3_general_ci |
| area_code | VARCHAR(64) | 无 | 否 | '0' | 行政代码；CHARACTER SET utf8mb3；COLLATE utf8mb3_general_ci |
| zip_code | VARCHAR(64) | 无 | 否 | '0' | 邮政编码；CHARACTER SET utf8mb3；COLLATE utf8mb3_general_ci |
| city_code | VARCHAR(64) | 无 | 否 | '' | 区号；CHARACTER SET utf8mb3；COLLATE utf8mb3_general_ci |
| name | VARCHAR(50) | 无 | 否 | '' | 名称 |
| short_name | VARCHAR(50) | 无 | 否 | '' | 简称 |
| merger_name | VARCHAR(50) | 无 | 否 | '' | 组合名 |
| pinyin | VARCHAR(30) | 无 | 否 | '' | 拼音 |
| lng | DECIMAL(10,6) | 无 | 否 | '0.000000' | 经度 |
| lat | DECIMAL(10,6) | 无 | 否 | '0.000000' | 纬度 |

索引：

| 索引名 | 类型 | 有序字段 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | PRIMARY | id | 唯一标识记录 |
| uk_code | UNIQUE / BTREE | area_code | 约束字段组合唯一 |
| idx_parent_code | INDEX / BTREE | parent_code | 支持所列字段组合的检索 |

结构依据：`database/design/sys_address.source.sql`。
