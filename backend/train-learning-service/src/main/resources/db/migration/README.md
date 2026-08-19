# 学习服务数据库迁移

本目录使用Flyway维护`road_training_learning`：

- `V1__learning_schema.sql`：学习会话、课程进度、逐课件进度和幂等事件日志；
- `V2__learning_outbox.sql`：学习开始/完成事件的可靠消息Outbox。

学习库不建立跨服务外键。同一学员活动会话、课程进度、课件进度、请求ID、事件序号
和Outbox业务键均由数据库唯一约束兜底。数据库结构只能追加新版本迁移。
