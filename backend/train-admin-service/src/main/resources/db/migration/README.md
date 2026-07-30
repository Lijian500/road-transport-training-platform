# 管理服务数据库迁移

本目录使用Flyway维护`road_training_admin`：

- `V1__admin_schema.sql`：企业/部门、用户、角色、权限及关联表；
- `V2__admin_permission.sql`：平台级、企业级固定权限目录。

数据库结构变更只能追加新版本迁移，不修改已在共享环境执行过的迁移文件。
