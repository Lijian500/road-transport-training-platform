# 2026-09-28 导师意见摘要与相关技术修订

本批次归入[变更总索引](README.md)，仅处理导师提出的摘要结构与第 2 章技术介绍意见，不改变系统功能或实验结论。

## 1. 来源与代码基线

- 来源：用户转述导师批复，要求摘要第一段简介研究背景和目的，第二段说明主要研究内容和意义；第 2 章介绍实际使用的编程语言、框架、数据库等技术。
- 审查日期：2026-09-28；基准提交：`d273198f78ceaf02f2fbd8b7a5d3dc1efdaf14c4`。
- 用户指定文件为 `docs/thesis/李剑_计算机科学与技术_基于Dubbo的道路运输企业在线培训系统设计与实现.docx`。编辑前 SHA-256 为 `51d873d4577965c763647582129e8f0ef4b83eee90b969195e75265d4ef06d31`，与上次登记的最终稿完全一致，因此本次沿用已知基线进行局部核查。
- 已检查 `46288c9..HEAD` 提交与差异，以及 `96a8d84..HEAD` 的 backend、frontend、deploy、scripts 范围，未发现源码、配置、迁移或测试变化。编辑前暂存区为空，工作区仅有旧论文路径删除与用户指定论文路径未跟踪；这两项是用户已有状态，不推断实际更名时间。
- 本次核查依赖配置及相关实现用途；历史运行结果仍按原版本、环境和场景使用。未编译项目、未运行业务测试，不将本次文档检查登记为实验通过。

## 2. 修改条目

### CHG-20260928-01 摘要按背景目的与内容意义分段

- 旧规则 → 新规则：中英文摘要各为一个长段落，调整为各两个正文段落。第一段交代道路运输培训背景、问题和研究目的；第二段说明研究方法、技术与功能、关键机制、已有结果及应用意义。
- 实现状态：已写入 Word。中文正文 485 字符（含标点及英文，不含标题和关键词），英文摘要与中文内容对应，关键词保留。
- 证据边界：保留“归档测试”和“合成抖动输入”的限定；100 秒位置增量与 95 秒计时仍来源于原稿，未写成真实视频实测或完整培训闭环验收。
- 代码依据：现有业务范围及 `LearningTimeCalculator`、`LearningOutboxPublisher` 等机制；本次无实现变更。原有证据及全文核查范围见[前次批次](2026-09-14-thesis-verification-cleanup.md)。
- 论文状态：已同步；实际修改中文摘要与 Abstract，物理页码分别为第 5、6 页。

### CHG-20260928-02 第 2 章补充实际采用的相关技术

- 旧规则 → 新规则：原章侧重状态机、消息一致性与身份抽验，语言、框架及持久化仅在末节简述；现按八节介绍技术基本原理、选用依据与系统用途，保留原有理论分析及证据边界。
- 实现状态：技术使用均为项目已有实现；本次仅补充论文说明，没有新增业务功能。
- 实际章节：2.1 Java 与 TypeScript；2.2 Spring Boot 与 Vue；2.3 Dubbo 与 Nacos；2.4 MySQL、MyBatis-Flex、Flyway 与 Redis；2.5 WebSocket 与学习状态；2.6 RabbitMQ 与消息一致性；2.7 OpenCV 与人脸核验；2.8 OSS 与媒体访问。
- 连带修改：1.4 的章节组织说明及自动目录；式（2-1）、已有引用编号和参考文献保留。第 3～7 章、图表及实验数据未改写。
- 论文状态：已同步；第 2 章为物理第 11～16 页、正文第 5～10 页。

| 核查内容 | 代码定位与用途 |
| --- | --- |
| Java 17、Spring Boot 3.5.3、Dubbo 3.3.6、MyBatis-Flex 1.11.4、OpenCV 4.9.0 | `backend/train-dependencies/pom.xml`；Java 运行与依赖版本依据 |
| Vue、TypeScript、Element Plus、Pinia、Vue Router、Vite | `frontend/package.json`、`frontend/src/main.ts`；依赖及应用组件接入 |
| Nacos 配置与服务发现 | 各服务 `src/main/resources/application.yml` 的 `spring.config.import`、Nacos 配置与 `dubbo.registry.address` |
| 数据持久化与缓存 | 各服务 Mapper 和 `db/migration` 目录；管理服务 `AuthorizationCacheService`；`deploy/docker-compose.yml` 仅用于组件配置定位，不作为实测版本 |
| 实时学习与消息 | `LearningWebSocketHandler.handle`、`LearningTimeCalculator`、`LearningOutboxPublisher`；实时连接、计时及异步结果传递 |
| 人脸核验 | `backend/train-face-adapter/.../OpenCvFaceVerifier.java` 中图像解码、`alignCrop`、`feature` 与 `match` 调用 |
| 私有媒体 | `CourseStorageServiceImpl`、`PrivateImageStorageServiceImpl`、`AliyunOssStorageService`；对象存储与授权访问 |

新增基础概念同时参照官方资料核对：[Java 语言规范](https://docs.oracle.com/javase/specs/jls/se17/html/jls-1.html)、[TypeScript 手册](https://www.typescriptlang.org/docs/handbook/typescript-in-5-minutes.html)、[Spring Boot 自动配置](https://docs.spring.io/spring-boot/reference/using/auto-configuration.html)、[Vue 介绍](https://vuejs.org/guide/introduction.html)、[MySQL InnoDB](https://dev.mysql.com/doc/refman/8.4/en/innodb-introduction.html)、[Nacos](https://nacos.io/en/docs/v3.0/what-is-nacos/)、[Redis](https://redis.io/docs/latest/develop/get-started/data-store/)、[WebSocket 协议](https://www.rfc-editor.org/rfc/rfc6455)、[RabbitMQ 消息模型](https://www.rabbitmq.com/tutorials/amqp-concepts)、[OpenCV 人脸处理](https://docs.opencv.org/4.x/d0/dd4/tutorial_dnn_face.html)。项目版本以仓库依赖为准，不用在线文档当前版本替换项目版本；MyBatis-Flex 站点本次读取失败，其使用方式依据项目现有依赖与 Mapper 代码核对。

## 3. 验证与同步回填

- 论文文件：[当前 Word 论文](../thesis/李剑_计算机科学与技术_基于Dubbo的道路运输企业在线培训系统设计与实现.docx)。
- 编辑后 SHA-256：`3a732944ff43e24f7b2a85b6a63e8c2cad5bab66805aa33ea53eb7e46b87a429`。
- 已处理编号：CHG-20260928-01、CHG-20260928-02，均已同步。覆盖代码为当前 HEAD 下与 `96a8d84` 相同的既有实现；本次是相关技术与文字结构的定向核查，不宣称重新执行全文定稿审计或业务验收。
- 内容检查：两种摘要各两段；八个技术小节均进入自动目录；27 项段落替换或插入内容全部存在；其他非空正文无遗漏；13 个表格、21 条参考文献、3 幅行内图及全部 6 个媒体部件内容不变；公式文本保持不变。
- 排版检查：运行 `render_docx.py` 因缺少 LibreOffice 失败，运行时清单确认 Windows 包未随附 LibreOffice；沿用前次隐藏 WPS 实例更新域、自动目录和页码并导出 PDF，再用随附 Poppler 生成 40 张页面图片，逐页检查。中英文摘要均独立成页，第 2 章目录与正文对应，新增文字无裁切或重叠。
- 原稿已有格式事项继续保留：中文摘要及正文首章首页的页眉页码设置、图 4-1 与图 4-3 两处原有注释压线，见前次批次；本次未扩大为全篇版式重做。真实媒体、真人照片、容量等实验边界不变。
- 本次编辑及 QA 中间材料保存在被 Git 忽略的 `tmp/thesis-advisor-20260928`，仅用于操作备份与检查，不作为独立的论文变更账本；长期登记以本批次为准。

| 文件范围 | 状态 |
| --- | --- |
| 用户指定 Word 论文 | 原路径完成局部修订、目录更新和 40 页检查 |
| 本批次、`docs/changes/README.md` | 登记两项导师意见、基线、范围与最终版本 |
| `docs/thesis/README.md`、`IMPLEMENTATION_STATUS.md` | 更新当前论文入口与本次完成情况，保留历史日期和结果 |
| 旧 Word 路径删除 | 用户既有状态，未恢复或另行覆盖 |
| 源码、接口、迁移、测试及历史验收报告 | 未修改、未补跑 |
