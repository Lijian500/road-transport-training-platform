# 数据设计说明

当前论文使用的[数据字典](data-dictionary.md)由[单一 JSON 结构源](data-dictionary.json)生成，覆盖31张表、476个字段和113项索引。其中30张迁移表（464个字段、110项索引）按管理V1～V14、培训V1～V6、学习V1～V5顺序归并；外部 `sys_address` 按用户提供的[真实DDL](sys_address.source.sql)核验。它描述仓库及所提供DDL对应的结构，不证明运行库已应用全部迁移；31张表均未声明物理外键。

[概念 ER 图及可编辑图源](../../docs/thesis/figures/advisor-20260930/README.md)与论文第4章对应。正文八张核心表和附录B使用同一数据字典，覆盖范围与验证依据统一见[2026-09-30修订记录](../../docs/changes/2026-09-30-thesis-analysis-design.md)。`sys_address` 的12个字段均非空，id为自增主键，另有行政代码唯一索引及父级代码普通索引；引擎为MyISAM，字符集为utf8mb3。四个代码列显式指定utf8mb3_general_ci，表级排序规则未显式声明。DDL中的AUTO_INCREMENT=4063保留为导出时的自增计数器值，不作为记录数或字段默认值。

## 培训计划快照

- `train_plan`保存计划基础信息、起止时间、状态、考试规则快照及发布/取消审计；
- `train_plan_course`保存所选课程，并在发布时冻结名称、规定时长、拖动规则、上报间隔和学时误差；
- `train_plan_courseware_snapshot`冻结课件标题、顺序、时长和OSS对象元数据引用，不复制视频文件；
- `train_plan_user`保存计划学员和分配、学习、考试、完成状态，并冻结姓名与部门展示信息；
- 四张表均包含`enterprise_id`，不建立跨服务外键；`(enterprise_id, plan_id, user_id)`唯一约束防止重复分配。

草稿关系可重建；发布时重新校验并在同一事务中生成最终快照。发布后计划、课程、学员和
规则不可修改，基础课程后续变化不影响历史任务。

## 学习会话与有效学时

- `study_session`保存活动客户端、状态、当前课件和严格事件序号；生成列唯一索引保证同一学员最多一个活动会话；
- `study_progress`保存计划课程的规定学时快照、累计有效毫秒和完成状态；
- `study_courseware_progress`保存逐课件确认位置、最大确认位置、顺序和完成状态；
- `study_event_log`以`(session_id, request_id)`和`(session_id, sequence_no)`双重唯一约束保证重试幂等和顺序；
- `mq_outbox`与学习进度同库提交，RabbitMQ故障不会回滚已确认进度；
- 培训库`mq_consume_log`和任务状态更新同事务提交，重复投递不会让状态倒退。

有效学时只在`STUDYING`状态累计，单次增量取服务端时间差、确认位置正向差、上报超时上限
和课程剩余规定学时的最小值。课件需到达允许误差范围，课程同时满足全部课件完成和规定
有效学时后才完成。

## 结构资料维护

在项目根目录执行：

```text
python docs/thesis/tools/build_data_dictionary.py
python docs/thesis/tools/verify_advisor_revision.py
```

第一条命令解析25个迁移文件及外部表DDL，输出JSON、Markdown和[归并DDL快照](schema-snapshot.sql)，不连接数据库；第二条核对论文表格、数据字典和来源指纹。外部DDL的来源与指纹在JSON的 `external_sources` 中独立登记，迁移来源仍为 `sources`。验证脚本固定本次已审查的表数量和关键约束，结构变动后应重新审查并更新基线。

30张迁移管理表的正式DDL仍由各服务Flyway维护，外部行政区域表保留独立来源。归并快照和外部DDL资料均不是迁移文件，不用于对共享数据库执行建表。照片保存期限、归档清理及生产容量仍按原实现／实验边界记录，本次没有新增规则或运行结论。
