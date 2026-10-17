# 论文与演示证据索引

当前论文已完成CHG-20261017-06：图4-7、图4-8的开始／结束统一为胶囊形终止符，异步流程及注释连接线均改为实线，业务箭头与无箭头注释保持区分。已使用drawio插件原生XML路线生成[新图源](figures/flowcharts-drawio-20261017/README.md)，并写回唯一主文件。全文仍为70页、68项目录、22图；仅物理第41、44页变化并通过目视复核，其余68页PNG与05校样逐字节一致，所有正文XML及其他包部件不变。见[本次记录](../changes/2026-10-17-thesis-flowchart-symbols.md)及[验证JSON](../test/thesis-flowchart-symbols-2026-10-17.json)。当前SHA-256：`29a770571b0bc362c92959350882b001cf4a6a6e72cf2e4c040bcecb02295aaf`。

此前CHG-20261017-04～05修订的论文为[Word主文件](李剑_计算机科学与技术_基于Dubbo的道路运输企业在线培训系统设计与实现.docx)。本轮整理9幅真实功能图，覆盖课程、计划、签到预览、视频学习、抽验预览、试卷、任务详情、档案及统计，另保留人员管理与考试作答2幅界面示例。图5-4、图5-5、图5-6沿用用户原图已有马赛克，无额外灰色覆盖；原裁剪、拼接、尺寸、图题及正文保持，图5-10及备用视频处理不变。CHG-20261017-04～05均已同步并写回唯一主文件，全文仍为70页、68项目录、22图；仅物理第48、49、51页变化并完成目视复核，其余67页PNG与04校样逐字节相同，见[本批记录](../changes/2026-10-17-thesis-real-scenes.md)及[05验证JSON](../test/thesis-original-mosaics-2026-10-17.json)。[04验证JSON](../test/thesis-real-scenes-2026-10-17.json)保留此前灰色覆盖版本的适用范围。此前流程图、正文留白、三线表和语言修订保留；未新增业务实验或量化结论。该版SHA-256：`df8f0afe3728759413c0ab864e685a41bfb10aaac8a012b3df2df78efba36c51`。

此前流程图修订的论文为[Word主文件](李剑_计算机科学与技术_基于Dubbo的道路运输企业在线培训系统设计与实现.docx)。本轮规范两幅流程图符号，学习流程保留图4-7，核心流程由图5-1移至第4章4.7节并改为图4-8，第5章界面图改为5-1～5-10；见[本轮修订批次](../changes/2026-10-17-thesis-flowcharts.md)及[本批图源](figures/flowcharts-20261017/README.md)。全文70页、68项目录、21图比例与图题同页及全页目视复核通过，已直接写回主文件，结果见[验证JSON](../test/thesis-flowcharts-2026-10-17.json)。本轮仅修改论文表达，不新增业务实验、性能数据或验收结论。

此前表6-3按条件与数值重新分配列宽，表头及数值居中、条件左对齐，三线表保留。该版69页、67项目录、21图；相较上一版，仅物理第58页变化，其余68页像素相同，见[该轮修订批次](../changes/2026-10-16-thesis-table63-main-file.md)及[验证JSON](../test/thesis-table63-main-file-2026-10-16.json)。其他6篇历史论文已按用户要求清理，以下指纹、页数和验证仍按原版本解释。该轮无新增业务实验或数据变化。

此前[三线表排版修正版及修订记录](../changes/2026-10-16-thesis-table-widths.md)统一28表的比例宽度及西文词内换行，修复阅读视图中的说明列挤压和异常大行高；截图对应WPS三栏阅读场景及69页打印版、67项目录、21图检查通过，见[列宽验证JSON](../test/thesis-table-widths-2026-10-16.json)。本次仅调整格式，无新增业务实验或数据变化。

此前[三线表版及修订记录](../changes/2026-10-16-thesis-three-line-tables.md)将28张数据表统一为三线表、表头居中及内容左对齐，保留8个布局表；该版69页打印版式、67项目录及21图检查通过，见[表格验证JSON](../test/thesis-three-line-tables-2026-10-16.json)。该轮打印检查未覆盖本次阅读视图问题。

此前[语言润色版及修订记录](../changes/2026-10-16-thesis-language-polish.md)已完成全文审读、116处语言调整和第6章日期清理，该版70页版式、67项目录及21图检查通过，见[语言验证JSON](../test/thesis-language-polish-2026-10-16.json)。该轮未新增业务实验，下列测试执行日期与适用版本继续保留在原始证据中。

2026-10-16用户确认已在本地跑通“课程发布—账号登录—真实视频播放—断线恢复—人脸抽验—考试—档案形成”的核心业务链。本次按[导师反馈二批次](../changes/2026-10-16-thesis-advisor-feedback-two.md)记录这一确认并补齐论文展示；确认日期不是执行日期，未提供具体日志、样本数量、耗时或准确率，本轮代理未重跑业务或扩展自动化测试数量。现有接口／领域报告与这一用户确认分别解释。论文70页、67项目录、21幅正文图及全页版式复核通过，结果与最终指纹见[正式验证JSON](../test/thesis-advisor-feedback-two-2026-10-16.json)。

2026-10-09在当前提交 `13d64637efc3727b42d9e871b74aa92bfa4fb454` 完成后端全量测试：61类266项全部通过，0失败、0错误、0跳过；真实OSS、授权照片模型、18项MySQL及RabbitMQ均已执行。执行时间、环境、逐项状态、源码摘要与清理记录见[本轮报告](../test/acceptance-2026-10-09.md)及[后端JSON](../test/backend-2026-10-09.json)。Word结果及目录已同步，98页版式复核通过，状态与版本统一见[本次批次](../changes/2026-10-09-backend-full-verification.md)。本轮未执行前端或浏览器完整媒体流程。

2026-09-14 已完成[Word 全文验证表述清理](../changes/2026-09-14-thesis-verification-cleanup.md)。清单区分源码核查、已有归档结果和实验范围限制；本次没有新业务测试执行，以下历史证据状态不因论文措辞修改而变化。

本表记录证据来自哪里及能支持什么结论。历史验证结果见[09-09阶段报告](../test/acceptance-2026-09-09.md)，当前实现增量见[变更总索引](../changes/README.md)和[首次回溯记录](../changes/2026-09-13-since-09-07.md)；操作见[本机启动](../deployment/local-demo.md)和[真实媒体验收](../test/media-acceptance.md)。路径相对仓库根目录，tmp和output为本机忽略目录。

2026-09-13按工作区（基准`374d762`及未提交改动）静态核对，该次未执行测试或更新Word论文；随后完成的Word增量同步见[当次登记](../changes/2026-09-13-since-09-07.md#word-sync-20260913)。后续按[变更总索引](../changes/README.md)维护增量依据。本轮未确认早期本机忽略目录中的证据仍可复现；下表标注“历史”的结果仍来自09-09及其引用报告，后端CI适用`fcbb50688adcf1ee1130e6c27af977f69f645d1f`、前端CI适用`7ca2f9413edda1f6c7159c6f7492d1d152a8eba9`（[CI记录](../test/ci-2026-09-09.json)），不与本轮结果合并计数。

| 论文论点或演示内容 | 证据入口 | 当前边界 |
| --- | --- | --- |
| 本地核心业务链验收 | [2026-10-16用户确认登记](../changes/2026-10-16-thesis-advisor-feedback-two.md) | 用户确认已本地跑通七环节；没有新增独立运行日志、数量、性能或识别准确率数据；本轮代理未重跑，不替代下列专项测量 |
| 第4章流程设计（图4-7、图4-8） | [当前图源](figures/flowcharts-drawio-20261017/README.md)、[CHG-20261017-06](../changes/2026-10-17-thesis-flowchart-symbols.md)及[验证JSON](../test/thesis-flowchart-symbols-2026-10-17.json) | 终止符与实线已同步；仅41、44页图形变化，其余68页同05版，不新增业务验证 |
| 第5章界面展示（图5-1～图5-11） | [真实场景图库与来源说明](figures/real-scenes-20261017/README.md)、[CHG-20261017-04～05](../changes/2026-10-17-thesis-real-scenes.md)及[最新验证JSON](../test/thesis-original-mosaics-2026-10-17.json)；历史夹具见[10月16日图库](figures/advisor-two-20261016/README.md)与[10月9日批次](../changes/2026-10-09-thesis-ui-screenshots.md) | 本批11幅界面图中9幅来自用户本人真实测试截图，图5-1人员管理、图5-8考试作答保留夹具并在图题标明“示例”，正文共22图。图5-4～图5-6沿用用户原图马赛克，无额外灰色覆盖；图5-10及备用视频处理不变。真实图覆盖课程、计划、签到预览、视频学习与播放控件（当前暂停）、抽验预览、试卷编辑、任务详情、学习过程及统计；裁剪、拼接与图像处理不改变画面状态，不将未展示的核验通过、恢复、考试通过或结业写成截图证据 |
| 当前后端全量自动化 | docs/test/acceptance-2026-10-09.md、backend-2026-10-09.json、backend-2026-10-09-build.txt、backend-source-manifest-2026-10-09.json | 10-09提交13d64637，61类266项全部通过，六个条件测试类均实际执行；不替代前端及完整业务流程 |
| 私有OSS分片、签名Range与删除 | AliyunOssIntegrationTest、本轮后端JSON | 1项通过、0跳过；196608字节合成输入，未验证浏览器解码、CORS或续签 |
| 自制100秒MP4可解码 | tmp/media/training-demo.json、tmp/media/video-browser-check.json、tmp/media-video-full-decode.log | 本机视频解码通过，未经过OSS或学习计时 |
| 企业车辆、档案权限与认证心跳 | tmp/media-live-regression.log | 3项真实接口回归通过；不是完整培训闭环 |
| 全部视频播完但学时不足可补学 | tmp/media-learning-test.log、tmp/media-ui-test.log | 后端规则和UI夹具通过；真实视频差额未测 |
| 合成抖动下100秒计入95秒 | LearningTimeCalculatorTest、tmp/media-domain-regression.log | 合成输入结果，不代表现实误差分布 |
| 抽验、考试、结业与乱序消费规则 | tmp/media-domain-regression.log | 领域定向测试，数据库Mapper为替身 |
| 无路由消息不丢弃及路由恢复 | tmp/media-rabbit-test.log | 真实Broker；不是完整服务和MySQL重启 |
| 正常完成、考试与统计一致 | [用户确认的本地核心链](../changes/2026-10-16-thesis-advisor-feedback-two.md)；[本批任务、过程与统计截图](figures/real-scenes-20261017/README.md)；独立media结果文件未新增归档 | 本地核心链已获用户确认；本批图5-9～图5-11只支持页面实际状态，其中过程记录为进行中、未考试任务，包含已签退及抽验超时会话，不新增正常结业、考试通过或全链一致性的独立证明；不补写脚本执行状态、测试时间或两种计划模式覆盖结果 |
| 同人不同照、不同人、多人照片 | OpenCvFaceVerifierIntegrationTest、10月9日后端JSON | 真实模型与4张授权照片测试通过，默认阈值0.363、登记照与同人照内容不同；不估计准确率；本地抽验链另据用户确认 |
| 真实摄像头采集 | [用户确认的本地人脸抽验链](../changes/2026-10-16-thesis-advisor-feedback-two.md)、[本批图5-4与图5-6](figures/real-scenes-20261017/README.md)及[CHG-20261017-04～05](../changes/2026-10-17-thesis-real-scenes.md) | 用户提供真实签到与抽验摄像头预览截图，沿用原图已有的人脸马赛克，无额外灰色覆盖；图5-6为启用摄像头前／预览，第二画面仍提供拍照按钮，尚未拍照或提交，不证明本次比对成功。此前合成预览属于历史夹具；摄像头预览或单帧比对均不等于活体检测 |
| 断网恢复、多标签、乱序和跨企业记录访问 | [用户确认的断线恢复](../changes/2026-10-16-thesis-advisor-feedback-two.md)、既有media恢复测试入口 | 本地断线恢复属于已确认核心链；多标签、乱序及跨企业访问等独立脚本本次未重跑，不从核心链确认推导全场景通过 |
| 1/5/10学员学习60秒 | tmp/experiments/study-人数-时间戳.json | 尚无数值；记录学时差额、解码帧数、P50/P95及状态可见时间 |
| MySQL持久化与服务重启 | 手册消息恢复步骤、事件ID关联的Outbox与消费日志 | 未执行；只读采集，不直接改完成状态 |
| 当前18项数据库条件测试 | 三组MapperIntegrationTest、docs/test/backend-2026-10-09.json | 管理8、培训9、学习1项全部通过，0跳过；使用隔离临时MySQL，不证明业务环境已迁移 |
| 历史11项数据库条件测试 | [Backend CI](https://github.com/Lijian500/road-transport-training-platform/actions/runs/34250552187)、docs/test/ci-2026-09-09.json | 09-09管理8、培训2、学习1项通过，原门禁和适用版本保留 |
| 自然到期计划历史档案 | [本批任务与学习过程截图](figures/real-scenes-20261017/README.md)、结束时间及任务ID | 本批任务和过程画面不构成自然到期或结业档案证据；真实完成及自然到期场景仍待补充，禁止改库制造历史成绩 |
| 人员车辆绑定、个人资料与登记照 | UserServiceImpl、VehicleServiceImpl、FaceReferenceServiceImpl及本轮相关测试 | 后端测试本轮通过；新增页面交互与真实登记流程未验收 |
| 签到签退摄像头与60秒凭据 | FaceCheckServiceImpl.verifyAttendance/requireAttendance、CameraCapture、StudyView及10月9日相关测试；[本批图5-4与图5-10](figures/real-scenes-20261017/README.md) | 后端凭据、过期及序号约束测试通过；本批新增真实签到摄像头预览和已签退会话的画面证据，未展示签到当次比对成功响应，亦未测量60秒有效期。随机抽验与签到签退是不同入口，不能互相替代证明 |
| 核验照片留存与受控预览 | 学习库V4/V5、PrivateImageStorageServiceImpl、LearningRecordServiceImpl及10月9日相关测试；[本批学习过程图5-10](figures/real-scenes-20261017/README.md) | 后端访问控制测试与临时库迁移通过；本批展示真实会话及抽验超时记录，不据此认定抽验已提交照片或比对通过，不扩大为历史照片补全或独立安全测量，历史照片仍可空 |
| 自然日、先学后考、全员结业及周期内回看 | PlanServiceImpl、ExamServiceImpl、PlanLifecycleService、LearningAccessServiceImpl及本轮相关测试 | 后端规则与真实SQL边界测试通过；TO_EXAM统计仍未过滤学习完成，不代表可立即开考的任务数 |
| 规定学时、播放进度及切换续播 | LearningSessionServiceImpl、StudentPlanDetailView、StudyView、实时协议及10月9日相关测试；[用户确认登记](../changes/2026-10-16-thesis-advisor-feedback-two.md)与[本批图5-5、图5-9](figures/real-scenes-20261017/README.md) | 后端规则测试通过，本地播放与恢复链已获用户确认；本批视频学习（当前暂停）及任务详情为真实页面，学时、课件位置和任务状态按画面解释，仍区分播放进度、有效学时与结业。旧播放恢复与补学夹具图已撤出当前论文；截图不新增断线恢复、补学成功或计时误差测量 |

## 每次实验的记录字段

关联变更编号及拟支持的论文章节，再归档代码版本或未提交差异、时间和时区、JDK/Node/浏览器版本、单实例服务与基础设施位置、计划/任务/会话ID、操作步骤、预期、实测、结论以及证据路径。失败也保留记录，并说明重跑是否使用了新的独立数据集。照片授权与原图仅在本机保存；公开报告不包含凭据、Cookie、签名URL和原始照片。

截图按真实测试页面、UI契约夹具和真实媒体区分，出处保存在随图来源记录中。本批9幅真实图与2幅夹具示例见[真实场景图库](figures/real-scenes-20261017/README.md)及[CHG-20261017-04～05](../changes/2026-10-17-thesis-real-scenes.md)；用户原图仅留本机，图5-4～图5-6沿用原图已有马赛克，无额外灰色覆盖，图5-10及备用视频处理不变，裁剪、拼接不补造画面状态。历史8图按[10月10日批次](../changes/2026-10-10-thesis-screenshot-annotations.md)清理底部标注的事实保留，原夹具来源仍见[历史图库](figures/screenshots-20261009/README.md)及[10月9日采集批次](../changes/2026-10-09-thesis-ui-screenshots.md)，不能改名为真实成绩。后续新增数量、耗时、误差和容量等结果，仍须依据对应真实报告填写，不能从截图或核心链用户确认推算。

论文更新完成后在变更记录回填论文版本、章节和覆盖代码范围；不能仅因底稿或索引已改就标记Word已同步。
