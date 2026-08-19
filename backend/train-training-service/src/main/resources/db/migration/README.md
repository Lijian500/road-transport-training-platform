# 培训服务数据库迁移

本目录使用Flyway维护`road_training_training`：

- `V1__training_course_schema.sql`：课程、视频课件、OSS对象元数据和浏览器直传会话。
- `V2__training_plan_schema.sql`：培训计划、课程规则与课件清单快照、学员任务。
- `V3__learning_task_projection.sql`：学习任务消息消费幂等日志。

培训库不建立跨服务外键，所有业务查询与写入必须携带`enterprise_id`范围。数据库结构
变更只能追加新版本迁移，不修改已在共享环境执行过的迁移文件。
