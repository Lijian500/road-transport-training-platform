# 2026-09-30 导师意见需求分析与系统设计修订

本批次归入[变更总索引](README.md)，按用户确认的六阶段、八批次计划实施。只修改论文及配套设计资料，不修改业务代码、RPC、迁移和历史验收报告。

## 1. 来源与基线

- 来源：导师要求补充可行性分析、角色与用例图、功能结构图、ER 图和逐字段数据表结构；用户于本任务确认完整实施，并于2026-09-30补充外部表sys_address的真实DDL。
- 审查时间：2026-09-30；代码基准：`d273198f78ceaf02f2fbd8b7a5d3dc1efdaf14c4`。
- 输入论文：`docs/thesis/李剑_计算机科学与技术_基于Dubbo的道路运输企业在线培训系统设计与实现.docx`，40 页；SHA-256：`3a732944ff43e24f7b2a85b6a63e8c2cad5bab66805aa33ea53eb7e46b87a429`，与上次登记相符。
- 编辑前工作区：实施进度、变更索引、论文入口已有修改；9 月 28 日批次与当前 Word 未跟踪，旧论文路径已有删除。全部保留，不推断这些改动的实际发生日期。
- 已核对暂存、未暂存及未跟踪文件；未发现前次登记以后的源码差异。源代码与 `96a8d84` 中已登记业务实现一致，历史验收仍限定于原版本、环境和场景。
- 备份及过程检查材料：被 Git 忽略的 `tmp/thesis-advisor-20260930`；它仅保存操作材料，长期状态以本批次为准。
- 外部表续补基线：首轮修订为95页，SHA-256为 `86c4432a07db665b5e79410778f093ab173c91406b108553b7b206eea1b3b4ad`。收到DDL后复核指纹相符，HEAD仍为d273198，已提交、暂存、未暂存及未跟踪业务源码均无新增差异；续补备份保存在上述目录的 `address-before`。

## 2. 修改条目及阶段状态

| 编号 | 旧内容与修改目标 | 代码及内容依据 | 当前论文状态 |
| --- | --- | --- | --- |
| CHG-20260930-01 | 3.4 节简述可行性 → 独立论证技术、经济、操作及方案取舍 | 现有部署、技术栈、页面和归档证据 | 已同步：3.4.1～3.4.4，保留表3-2，不新增成本数字或实验结论 |
| CHG-20260930-02 | 三类角色仅有文字和表 → 补三幅标准用例图及权限说明 | 权限迁移、前端路由、EnterpriseServiceImpl、学习考试准入 | 已同步：3.1.1～3.1.2、图3-1～3-3；保留表3-1 |
| CHG-20260930-03 | 仅总体架构图 → 增加业务功能树及模块说明 | 管理与学员路由、个人资料入口 | 已同步：4.2、图4-2；原后续章节顺延至4.3～4.6 |
| CHG-20260930-04 | 核心逻辑关系示意 → ER 总览及三个分域图 | 三库迁移、实体和必要调用链 | 已同步：4.3.1、图4-3～4-6及实体、物理表映射说明 |
| CHG-20260930-05 | 表用途概述 → 正文八张核心表和附录完整结构字典 | 管理V1～V14、培训V1～V6、学习V1～V5及用户提供的sys_address真实DDL | 已同步：4.3.4及附录B，正文8表与附录31表完成；476个字段、113项索引逐项核对，表B-9不再保留待核验占位 |
| CHG-20260930-06 | 原目录及图表编号 → 完成新增内容交叉引用与版式检查 | 实际 Word、图源、数据字典及渲染结果 | 已同步：1.4、自动目录、图表编号与引用，95页检查完成；外部表续补后重新导出全文，复查8个变化页，其余87页逐像素一致 |

## 3. 数据与图形约定

- 正文核心表固定为 sys_user、train_course、train_plan、train_plan_user、study_session、study_progress、face_check_task、exam_record；完整附录包含30张迁移表和按真实DDL补齐的sys_address。
- 管理库 8 张迁移表加 1 张外部字典表，培训库 15 张，学习库 7 张。字段、生成表达式、默认值及索引按迁移版本归并，不按 Java 类型推断 SQL 类型。
- 迁移与外部表DDL均没有声明物理外键；图中的跨服务关联和表中引用均为逻辑关系，不新增数据库约束。行管跨企业数据查询仍属未实现范围。
- 用例图、功能结构图与 ER 图均保留可编辑 drawio 源文件和同源 SVG／PNG。教程只作为绘图样式参考，系统内容依据当前实现。
- [用例图参考](https://www.cnblogs.com/lcword/p/10472040.html)、[功能结构图参考](https://blog.csdn.net/Tir_zhang/article/details/135428037)、[ER 图参考](https://blog.csdn.net/William0318/article/details/104348102)。

## 4. 八批次交付与覆盖清单

| 执行批次 | 已保存成果 | 检查及续接状态 |
| --- | --- | --- |
| 1：修订基线 | 输入 Word 备份、源码差异核对、本批次及总索引 | 完成；沿用 d273198，未发现漏记源码变更 |
| 2：需求与可行性 | 第3章及三类角色用例图 | 完成；检查角色权限、前置条件及可行性结论范围 |
| 3：功能结构 | 4.2及七模块功能树 | 完成；与管理／学员路由及个人资料入口对应 |
| 4：概念 ER | 4.3.1及总览、管理、培训考试、学习监管四图 | 完成；可选车辆、计划快照、零次抽验提交、任务考试关系已核对 |
| 5：管理库 | 8张迁移表＋外部sys_address，正文sys_user | 9张完成；外部表12个字段、3项索引、自增、MyISAM及字符集按用户提供的DDL补齐 |
| 6：培训库 | 15张表，正文课程、计划、参训任务、考试记录 | 完成；包含V4～V6的抽验、考试时长和平台图片可空变更 |
| 7：学习库 | 7张表，正文会话、课程进度、抽验任务 | 完成；包含V3生成列更新、V4核验凭据及V5照片关联 |
| 8：整合检查 | 目录、图表、统一字典、95页Word与同步入口 | 完成；修复原架构／流程图压线、末表孤页和地址索引类型拆行；外部表续补与页面复查完成 |

附录 B 覆盖清单：

- B.1／表B-1～B-9：train_org、sys_user、sys_role、sys_permission、sys_user_role、sys_role_permission、train_org_user、train_vehicle、sys_address。
- B.2／表B-10～B-24：train_course、train_storage_object、train_courseware、train_upload_session、train_plan、train_plan_course、train_plan_courseware_snapshot、train_plan_user、mq_consume_log、train_private_image_upload_session、exam_question、exam_paper、exam_paper_question、exam_record、exam_answer。
- B.3／表B-25～B-31：study_session、study_progress、study_courseware_progress、study_event_log、mq_outbox、face_check_task、face_check_log。
- 正文表4-2～4-9按顺序对应：sys_user、train_course、train_plan、train_plan_user、study_session、study_progress、face_check_task、exam_record。

配套资料：

- [数据字典](../../database/design/data-dictionary.md)、[JSON结构源](../../database/design/data-dictionary.json)及[只读DDL归并快照](../../database/design/schema-snapshot.sql)。已核验31张表、476个字段、113项索引：迁移管理部分为30／464／110，外部表为1／12／3。外部表[原始DDL](../../database/design/sys_address.source.sql)单独归档，不放入迁移目录。
- [全部图源及编号](../thesis/figures/advisor-20260930/README.md)：8幅本轮设计图与2幅原图重排，共10组drawio／SVG／PNG。
- [字段字典生成器](../thesis/tools/build_data_dictionary.py)、[图形生成器](../thesis/tools/build_advisor_figures.py)、[一致性核对脚本](../thesis/tools/verify_advisor_revision.py)。

业务规则依据的代码定位：`frontend/src/router/admin.ts`、`student.ts`、`index.ts`；管理服务的 `EnterpriseServiceImpl`、`VehicleServiceImpl`、`AddressServiceImpl` 及用户服务；培训服务的 `ExamServiceImpl`、`PrivateImageStorageServiceImpl`；学习服务的 `FaceCheckServiceImpl`、会话服务及计时组件；Web API的 `TrainingRecordController`。25个迁移文件路径及SHA-256逐项保存在JSON结构源的 `sources` 中，外部表的用户提供来源及指纹独立保存在 `external_sources` 中。代码实现状态沿用既有登记，本次未执行相关业务测试。

## 5. 最终版本与验证

- 论文：[当前Word论文](../thesis/李剑_计算机科学与技术_基于Dubbo的道路运输企业在线培训系统设计与实现.docx)。版本：2026-09-30导师意见修订（外部地址表已补齐，六阶段八批次完成）。
- 最终SHA-256：`c848cbba977a5d8e6d333ba54b3b6ff9196e440e654901b0dde4188c3fb3d3d2`；95页，10幅行内图，91个Word表格（原有13个＋39组字段表与索引表）。原有四个分节保留。
- 实际修改：1.4、3.1、3.4、4.2～4.6、附录B与自动目录；第4章后续正文只调整编号或插图排版。第5～7章、致谢、21条参考文献及附录A正文逐段与输入备份相同；中英文摘要与第2章沿用上次修订。
- 覆盖代码：d273198下与96a8d84一致的业务实现。编辑前后核对已提交、暂存、未暂存及未跟踪源码，业务源码、接口、部署脚本、迁移和测试均无新增差异；工作区既有文档修改及旧Word删除状态保留。
- 结构核对：逐单元格检查正文8表、附录31表的字段名、完整类型、主键标识、可空、默认值和说明；逐表检查索引类型及有序字段组合，包含复合主键、唯一键、生成表达式与自动更新时间。25个迁移及1份外部DDL的来源指纹一致，外部表按用户提供的DDL核验，不等同于已连接运行库验证。
- 图文一致性：三类角色和行管账号边界、先学后考、人员可选单车绑定、发布快照、抽验零次提交、任务0..1考试记录及照片可空逻辑关系均按当前实现表达；没有新增物理外键或跨企业监管能力。drawio／SVG XML及对应PNG文件检查通过。
- 渲染检查：标准 `render_docx.py` 因缺少LibreOffice未能运行，沿用独立隐藏WPS实例更新并保存域、自动目录后导出PDF，使用随附Poppler逐页渲染并查看。首轮完成95页中文字体、图内文字、表格跨页、重复表头、编号与引用检查。外部表续补后再次渲染全文，物理第62～65、69～72页共8页发生变化并重新目视检查，其余87页与首轮已检查版本逐像素一致；地址索引类型栏加宽后BTREE完整显示，最终仍为95页。
- 原有格式说明：四个分节的“首页不同”设置保留，中文摘要与第一章首页的页眉／页码行为沿用原稿；两幅原图的注释压线本轮已修复。全文没有新增业务实验、成本数值、识别准确率或生产容量结论；本轮未编译项目或重跑业务测试。

## 6. 外部地址表续补与后续续接

首轮只登记外部表的应用映射字段。用户提供真实DDL后，已在同批次关闭CHG-20260930-05的最后一项，CHG-20260930-01～06全部已同步，无需再等待外部表定义。

- 原始DDL文件SHA-256：`8964b80922fe9d9942887332f7dedb1be63934e9238dcef0aeffcb4ef5391807`。只作为设计资料读取，没有执行DDL或新增Flyway迁移。
- 已补齐12个非空字段、BIGINT／TINYINT的UNSIGNED、自增主键、各项显式默认值、PRIMARY及两个BTREE索引。表引擎为MyISAM，字符集为utf8mb3，四个代码列显式使用utf8mb3_general_ci；未推断未声明的表级排序规则。AUTO_INCREMENT=4063保留为导出时的自增计数器值，不作为行数或字段默认值。
- 依据 `AddressServiceImpl.children/create/findByAreaCode` 说明parent_code逻辑关联area_code、根级使用代码0；无物理外键。现有ER图的地址id标识与DDL自增主键相符，图源无需重画。
- Word续补范围仅为附录B引言、sys_address名称、表B-9及表后说明和索引表；表B-9字段在物理第69页，说明与索引在第70页。其余30张表的JSON结构逐项不变，第1～7章、致谢、参考文献、附录A及其余89个Word表格内容与首轮修订版一致。
- 最终核验脚本检查通过；续补备份、前后差异、最终PDF与页图位于 `tmp/thesis-advisor-20260930/address-before`、`address-final-verification.json`、`address-final.pdf` 和 `qa-address-final`。后续从本节最终指纹继续，只复核新增代码、DDL或论文差异。

历史真实媒体、人脸照片、容量与生产条件等剩余实现／实验事项继续沿用原记录，不能因本轮文档完成而关闭。操作备份、分批PDF及最终页图位于被Git忽略的 `tmp/thesis-advisor-20260930`，长期续接依据以本批次与总索引为准。
