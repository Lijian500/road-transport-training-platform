# 论文与演示证据索引

2026-10-16用户确认已在本地跑通“课程发布—账号登录—真实视频播放—断线恢复—人脸抽验—考试—档案形成”的核心业务链。本次按[导师反馈二批次](../changes/2026-10-16-thesis-advisor-feedback-two.md)记录这一确认并补齐论文展示；确认日期不是执行日期，未提供具体日志、样本数量、耗时或准确率，本轮代理未重跑业务或扩展自动化测试数量。现有接口／领域报告与这一用户确认分别解释。论文70页、67项目录、21幅正文图及全页版式复核通过，结果与最终指纹见[正式验证JSON](../test/thesis-advisor-feedback-two-2026-10-16.json)。

2026-10-09在当前提交 `13d64637efc3727b42d9e871b74aa92bfa4fb454` 完成后端全量测试：61类266项全部通过，0失败、0错误、0跳过；真实OSS、授权照片模型、18项MySQL及RabbitMQ均已执行。执行时间、环境、逐项状态、源码摘要与清理记录见[本轮报告](../test/acceptance-2026-10-09.md)及[后端JSON](../test/backend-2026-10-09.json)。Word结果及目录已同步，98页版式复核通过，状态与版本统一见[本次批次](../changes/2026-10-09-backend-full-verification.md)。本轮未执行前端或浏览器完整媒体流程。

2026-09-14 已完成[Word 全文验证表述清理](../changes/2026-09-14-thesis-verification-cleanup.md)。清单区分源码核查、已有归档结果和实验范围限制；本次没有新业务测试执行，以下历史证据状态不因论文措辞修改而变化。

本表记录证据来自哪里及能支持什么结论。历史验证结果见[09-09阶段报告](../test/acceptance-2026-09-09.md)，当前实现增量见[变更总索引](../changes/README.md)和[首次回溯记录](../changes/2026-09-13-since-09-07.md)；操作见[本机启动](../deployment/local-demo.md)和[真实媒体验收](../test/media-acceptance.md)。路径相对仓库根目录，tmp和output为本机忽略目录。

2026-09-13按工作区（基准`374d762`及未提交改动）静态核对，该次未执行测试或更新Word论文；随后完成的Word增量同步见[当次登记](../changes/2026-09-13-since-09-07.md#word-sync-20260913)。后续按[变更总索引](../changes/README.md)维护增量依据。本轮未确认早期本机忽略目录中的证据仍可复现；下表标注“历史”的结果仍来自09-09及其引用报告，后端CI适用`fcbb50688adcf1ee1130e6c27af977f69f645d1f`、前端CI适用`7ca2f9413edda1f6c7159c6f7492d1d152a8eba9`（[CI记录](../test/ci-2026-09-09.json)），不与本轮结果合并计数。

| 论文论点或演示内容 | 证据入口 | 当前边界 |
| --- | --- | --- |
| 本地核心业务链验收 | [2026-10-16用户确认登记](../changes/2026-10-16-thesis-advisor-feedback-two.md) | 用户确认已本地跑通七环节；没有新增独立运行日志、数量、性能或识别准确率数据；本轮代理未重跑，不替代下列专项测量 |
| 第5章核心流程及界面展示（图5-1～图5-11） | [本批图库与来源说明](figures/advisor-two-20261016/README.md)、[导师反馈二批次](../changes/2026-10-16-thesis-advisor-feedback-two.md)及[正式验证JSON](../test/thesis-advisor-feedback-two-2026-10-16.json)；原8图见[反馈一图库](figures/advisor-20261016/README.md)，历史采集见[10月9日批次](../changes/2026-10-09-thesis-ui-screenshots.md) | 1幅新增流程图、8幅沿用界面及2幅新增组合界面；Vue组件＋纯虚构API／WebSocket夹具，照片遮挡，仅说明功能与操作状态；不作为真实人脸、考试成绩或本地闭环的执行证据；70页、67项目录、21图比例与图题同页及全页版式复核通过 |
| 当前后端全量自动化 | docs/test/acceptance-2026-10-09.md、backend-2026-10-09.json、backend-2026-10-09-build.txt、backend-source-manifest-2026-10-09.json | 10-09提交13d64637，61类266项全部通过，六个条件测试类均实际执行；不替代前端及完整业务流程 |
| 私有OSS分片、签名Range与删除 | AliyunOssIntegrationTest、本轮后端JSON | 1项通过、0跳过；196608字节合成输入，未验证浏览器解码、CORS或续签 |
| 自制100秒MP4可解码 | tmp/media/training-demo.json、tmp/media/video-browser-check.json、tmp/media-video-full-decode.log | 本机视频解码通过，未经过OSS或学习计时 |
| 企业车辆、档案权限与认证心跳 | tmp/media-live-regression.log | 3项真实接口回归通过；不是完整培训闭环 |
| 全部视频播完但学时不足可补学 | tmp/media-learning-test.log、tmp/media-ui-test.log | 后端规则和UI夹具通过；真实视频差额未测 |
| 合成抖动下100秒计入95秒 | LearningTimeCalculatorTest、tmp/media-domain-regression.log | 合成输入结果，不代表现实误差分布 |
| 抽验、考试、结业与乱序消费规则 | tmp/media-domain-regression.log | 领域定向测试，数据库Mapper为替身 |
| 无路由消息不丢弃及路由恢复 | tmp/media-rabbit-test.log | 真实Broker；不是完整服务和MySQL重启 |
| 正常完成、考试与统计一致 | [用户确认的本地核心链](../changes/2026-10-16-thesis-advisor-feedback-two.md)；独立media结果文件未新增归档 | 本地核心链已获用户确认；不补写脚本执行状态、任务ID、时间或两种计划模式覆盖结果 |
| 同人不同照、不同人、多人照片 | OpenCvFaceVerifierIntegrationTest、10月9日后端JSON | 真实模型与4张授权照片测试通过，默认阈值0.363、登记照与同人照内容不同；不估计准确率；本地抽验链另据用户确认 |
| 真实摄像头采集 | [用户确认的本地人脸抽验链](../changes/2026-10-16-thesis-advisor-feedback-two.md) | 本地抽验已确认完成；本轮截图用合成预览并遮挡照片，不能充当摄像头执行证据；单帧照片核验不等于活体检测 |
| 断网恢复、多标签、乱序和跨企业记录访问 | [用户确认的断线恢复](../changes/2026-10-16-thesis-advisor-feedback-two.md)、既有media恢复测试入口 | 本地断线恢复属于已确认核心链；多标签、乱序及跨企业访问等独立脚本本次未重跑，不从核心链确认推导全场景通过 |
| 1/5/10学员学习60秒 | tmp/experiments/study-人数-时间戳.json | 尚无数值；记录学时差额、解码帧数、P50/P95及状态可见时间 |
| MySQL持久化与服务重启 | 手册消息恢复步骤、事件ID关联的Outbox与消费日志 | 未执行；只读采集，不直接改完成状态 |
| 当前18项数据库条件测试 | 三组MapperIntegrationTest、docs/test/backend-2026-10-09.json | 管理8、培训9、学习1项全部通过，0跳过；使用隔离临时MySQL，不证明业务环境已迁移 |
| 历史11项数据库条件测试 | [Backend CI](https://github.com/Lijian500/road-transport-training-platform/actions/runs/34250552187)、docs/test/ci-2026-09-09.json | 09-09管理8、培训2、学习1项通过，原门禁和适用版本保留 |
| 自然到期计划历史档案 | 学员与管理档案截图、结束时间及任务ID | 待真实完成及自然到期，禁止改库制造历史成绩 |
| 人员车辆绑定、个人资料与登记照 | UserServiceImpl、VehicleServiceImpl、FaceReferenceServiceImpl及本轮相关测试 | 后端测试本轮通过；新增页面交互与真实登记流程未验收 |
| 签到签退摄像头与60秒凭据 | FaceCheckServiceImpl.verifyAttendance/requireAttendance、CameraCapture、StudyView及10月9日相关测试 | 后端凭据、过期及序号约束测试通过；本地核心链已获用户确认，但没有新增独立签到签退证据或凭据时长测量 |
| 核验照片留存与受控预览 | 学习库V4/V5、PrivateImageStorageServiceImpl、LearningRecordServiceImpl及10月9日相关测试 | 后端访问控制测试与临时库迁移通过；本地抽验与档案链已获用户确认，不扩大为历史照片补全或独立安全测量，历史照片仍可空 |
| 自然日、先学后考、全员结业及周期内回看 | PlanServiceImpl、ExamServiceImpl、PlanLifecycleService、LearningAccessServiceImpl及本轮相关测试 | 后端规则与真实SQL边界测试通过；TO_EXAM统计仍未过滤学习完成，不代表可立即开考的任务数 |
| 规定学时、播放进度及切换续播 | LearningSessionServiceImpl、StudentPlanDetailView、StudyView、实时协议及10月9日相关测试；[用户确认登记](../changes/2026-10-16-thesis-advisor-feedback-two.md) | 后端规则测试通过，本地播放与恢复链已获用户确认；仍区分课件位置、有效学时与结业；实际学时误差未新增测量，界面图中的学时及会话仍为夹具数据 |

## 每次实验的记录字段

关联变更编号及拟支持的论文章节，再归档代码版本或未提交差异、时间和时区、JDK/Node/浏览器版本、单实例服务与基础设施位置、计划/任务/会话ID、操作步骤、预期、实测、结论以及证据路径。失败也保留记录，并说明重跑是否使用了新的独立数据集。照片授权与原图仅在本机保存；公开报告不包含凭据、Cookie、签名URL和原始照片。

截图按真实接口页面、UI契约夹具和真实媒体区分，出处保存在图片内注记或随图来源记录中；本组8图按[10月10日批次](../changes/2026-10-10-thesis-screenshot-annotations.md)清理底部重复标注，原来源与边界继续见[图库说明](figures/screenshots-20261009/README.md)及[10月9日采集批次](../changes/2026-10-09-thesis-ui-screenshots.md)。不要将带ui-fixture文件名的图片改名为真实成绩。当前讲稿已依据10月16日用户确认修正核心链完成情况；后续新增数量、耗时、误差和容量等结果，仍须依据对应真实报告填写，不能从截图或用户确认推算。

论文更新完成后在变更记录回填论文版本、章节和覆盖代码范围；不能仅因底稿或索引已改就标记Word已同步。
