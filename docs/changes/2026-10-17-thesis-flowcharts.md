# 2026-10-17 流程图规范与章节归属修订

## 1. 来源与版本

- 来源：导师最新整改要求“流程图属于系统设计部分，放入系统设计章节”，并要求开始、判断、结束采用规定图形；用户明确现稿只有图4-7“学习进度受理与结果同步流程”和图5-1“核心业务链实现流程”两幅流程图，后者须移入第4章。
- 画法参考：[导师指定的流程图绘制说明](https://blog.csdn.net/weixin_44917390/article/details/121357607)。本次采用开始／结束椭圆、处理矩形、判断菱形及带方向箭头的流线；判断出口标注“是／否”，封装的子流程采用预定义处理符号。
- 审查时间：2026-10-17，Asia/Shanghai；基准提交：`87e1af622e27a6fd8cdea32689ca46d4b84eacf9`。本批为该基准上的未提交文档与图源修改，不推断业务源码修改日期。
- 输入与输出均为[唯一论文主文件](../thesis/李剑_计算机科学与技术_基于Dubbo的道路运输企业在线培训系统设计与实现.docx)，直接原地更新，不另存论文。输入SHA-256：`6191a3fc9daa2470ae5878cd4b14d2ce8cde6e5b6d1f461189bf4dfb20a9c816`；输出SHA-256：`bc11e4cf887c3b6023c9d51841feddad0098cc094b036fc7469bc248efb39c7b`，主文件与已通过验证的修订稿指纹一致。
- 图源：[本批图库](../thesis/figures/flowcharts-20261017/README.md)；验证登记：[本批JSON](../test/thesis-flowcharts-2026-10-17.json)。Word结构、68项目录页码、21图比例和图题同页检查及70页逐页目视复核通过；CHG-20261017-01～02均已同步。

## 2. 变更条目

| 编号 | 旧表达 → 新表达 | 论文影响与当前状态 |
| --- | --- | --- |
| CHG-20261017-01 流程图符号规范 | 两幅流程图的步骤、判断与端点表达未统一按标准符号区分 → 两图重绘，显式区分开始／结束、处理、判断、分支及子流程，并保留现有业务含义 | 已同步。图4-7保留位置；原图5-1重绘后作为图4-8；结构与70页逐页目视复核通过 |
| CHG-20261017-02 核心流程迁移与图号同步 | 核心业务流程位于第5章实现部分，编号图5-1 → 移入第4章新增4.7“核心业务流程设计”，改为图4-8“核心业务流程设计”；原图5-2～图5-11连续改为图5-1～图5-10 | 已同步。第4章新节及图题、第5章引导与10幅图号、第6章相关引用、自动目录、图像替代文字和当前材料已更新，目录及版式复核通过 |

本轮仅调整流程图表达、章节归属及关联文字，不改变业务规则、数据模型、系统功能、实验数据或既有验收结论。核心业务图采用分层子流程表达，学习事件受理与异步投影细节由图4-7承接，避免在总流程中重复展开而压缩字号。档案为动态查询，未结业任务也可查询，不将“档案形成”解释为结业后才可访问。

## 3. 代码依据与范围核查

- 对照论文上次同步基准`5011d2354e5eb6dbf589534077cec3926aa8e2e5..87e1af6`，并检查暂存、未暂存与未跟踪源码，无新增业务实现差异。补查`13d64637..87e1af6`，后端、前端及数据库迁移无新增差异，`database/design`为此前已登记的历史说明／字典变化；任务开始时未跟踪项仅为`__pycache__`。
- 学习事件：`LearningSessionServiceImpl.processEvent/submitEvent/settleProgress`对应身份及绑定校验、历史请求幂等、序号与状态校验、事务计时／事件日志及按需Outbox；`LearningWebSocketHandler.emitLearningResult`对应ACK与PROGRESS_CONFIRMED，实时结果不等待MQ投影完成。
- 异步同步：`LearningOutboxPublisher.publishPending`对应退避补偿，`LearningTaskProjectionConsumer.consume/apply`对应eventId去重与任务投影。
- 恢复与核验：`StudyView.handleRealtimeDisconnected`、`useLearningRealtime.establish`对应断线暂停、重绑同步及手动继续；`FaceCheckServiceImpl.recordComparison/expire`对应通过后PAUSED、允许次数内重试及耗尽／超时终止。
- 考试与档案：`ExamServiceImpl.openExam/grade`、`TrainingCompletionService.recalculate`对应先学后考及仅学习模式跳过考试；`TrainingRecordController.studentDetail/merge`对应动态聚合，未完成任务也能查询。
- 上述为只读实现核查，本次没有修改业务源码、数据库迁移或测试。本轮不编译项目、不执行业务测试；已有测试代码与历史执行结果不能视为本次通过。

上述Java文件位于`backend/<模块>/src/main/java/me/lj/train/`：学习侧为`train-learning-service`的`learning/service/`，实时侧为`train-realtime-service`的`realtime/websocket/`，培训侧为`train-training-service`的`training/service/`，档案入口为`train-web-api`的`webapi/controller/`。前端对应`frontend/src/views/student/StudyView.vue`及`frontend/src/composables/useLearningRealtime.ts`。

## 4. 文档与版式验证

- 修订稿为70页；目录因新增4.7节由67项变为68项，更新27处页码缓存，全部68项目录页码检查通过。21幅正文图、36个表格元素、21条文献及1个原生公式保留，21图比例及图题同页检查通过。
- OOXML仅`word/document.xml`、`word/media/image12.png`及`word/media/advisor-two-core-business-flow.png`三个部件变化，其余包部件字节相同；表格、原生公式、分节及参考文献XML完全相同。
- 正文逐段限定检查通过：除既定图号与引用、新增4.7标题及档案说明外，其余正文文字保持；档案说明明确未结业任务与终止会话过程记录也可查询。
- 标准`render_docx.py`已尝试，因缺少`soffice`并发生沙盒临时目录清理权限错误而失败，不记为通过。改用独立隐藏WPS副本导出及捆绑Poppler渲染，全部70页按原尺寸逐张目视检查通过，未发现缺字、裁切、重叠、表格越界或图题分离。
- 图4-7位于物理第42页（正文第35页），4.7节始于物理第44页，图4-8位于物理第45页（正文第38页）；第5章在物理第46页正常起章。主文件已写回并与已验证修订稿指纹一致，结构及视觉证据见[验证JSON](../test/thesis-flowcharts-2026-10-17.json)。历史批次保留原始日期、页数、图号与结论，不因本次重编号改写旧版记录。

## 5. 文件覆盖与同步状态

| 路径（相对仓库根目录） | 来源与范围 | 关联条目 |
| --- | --- | --- |
| `docs/thesis/李剑_计算机科学与技术_基于Dubbo的道路运输企业在线培训系统设计与实现.docx` | 未提交修订；两图、第4～6章关联内容及目录 | 01、02，已同步 |
| `docs/thesis/figures/flowcharts-20261017/` | 新增SVG、drawio、PNG及来源说明 | 01、02 |
| `docs/changes/2026-10-17-thesis-flowcharts.md`、`docs/changes/README.md` | 新批次与统一索引 | 01、02 |
| `docs/thesis/README.md`、`docs/thesis/evidence-index.md`、`docs/thesis/design-and-defense.md` | 当前版本入口、证据边界及现行图号 | 01、02 |
| `docs/thesis/论文写作与格式规范.md`、`IMPLEMENTATION_STATUS.md` | 导师补充规则与阶段进度 | 01、02 |
| `docs/test/thesis-flowcharts-2026-10-17.json` | 新增文档结构、版式与版本验证记录 | 01、02 |

本批01、02已全部同步，覆盖第4章两幅流程图及新增4.7节、第5章图号与引导、第6章引用、目录与现行材料，业务代码覆盖范围见第3节。本人签名、阅读模式实际显示与既有专项实验等剩余事项沿用原记录，本次打印版式复核不改变其状态。
