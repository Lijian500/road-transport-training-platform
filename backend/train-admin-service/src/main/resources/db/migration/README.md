# 管理服务数据库迁移

本目录使用Flyway维护`road_training_admin`：

- `V1__admin_schema.sql`：企业/部门、用户、角色、权限及关联表；
- `V2__admin_permission.sql`：平台级、企业级固定权限目录。
- `V3__admin_soft_delete.sql`：组织和角色软删除审计字段。
- `V4__address_permission.sql`：省、市、区三级地址管理权限。
- `V5__organization_nature_and_area.sql`：企业/行管组织性质与行政辖区关联。
- `V6__course_permission.sql`：课程及课件管理权限，并补发给内置企业管理员角色。
- `V7__training_plan_permission.sql`：培训计划与学员任务权限，并补发给内置企业管理员和学员角色。
- `V8__student_learning_permission.sql`：视频学习权限，并补发给已有内置学员角色。

数据库结构变更只能追加新版本迁移，不修改已在共享环境执行过的迁移文件。
