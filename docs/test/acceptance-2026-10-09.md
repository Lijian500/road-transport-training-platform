# 2026-10-09 后端全量测试验收报告

本轮在用户授权的 ECS Docker 环境执行同一源码版本的后端 `clean verify`：**61个测试类、266项测试全部通过，失败、错误、跳过均为0**。此前跳过的真实OSS和授权照片模型测试均已执行；三组真实MySQL测试及RabbitMQ测试也全部通过。此结论限定于本轮后端自动化清单，不替代浏览器完整培训流程验收。

## 1. 版本、时间与环境

- 执行时间：2026-10-09 17:58:40～18:04:28（Asia/Shanghai，UTC+08:00），墙钟耗时5分48秒，Maven构建计时5分44秒，退出码0。
- 源码提交：`13d64637efc3727b42d9e871b74aa92bfa4fb454`。测试包由该提交的 `git archive` 生成，385个后端文件与归档逐字节摘要一致；本地检出文件按CRLF/LF规范比较后内容一致，没有未提交源码改动。
- 与论文上次登记的 `1491b02364164e300e447793fa6c2a89c0231abb` 比较，后端、前端、脚本和数据库目录没有源码差异。本轮没有为通过测试修改业务逻辑、测试断言或依赖版本。
- 运行方式：通过 Workbench 上传完整测试包，在用户实例的权限受限临时目录内启动独立Maven容器；使用宿主Docker运行Testcontainers临时MySQL，连接既有RabbitMQ，访问配置的真实私有OSS，并在容器内读取两个人脸模型和4张授权照片。

| 环境项 | 本轮实际版本或配置 |
| --- | --- |
| 宿主 / 测试镜像系统 | Ubuntu 22.04.5 LTS / Ubuntu 24.04.5 LTS |
| JDK / Maven | Eclipse Temurin 17.0.20.1+1 / Maven 3.9.16 |
| Maven镜像 | `maven:3.9-eclipse-temurin-17`；固定镜像摘要见JSON |
| Docker / 测试连接API | 29.7.1 / 1.44（服务器最低接受1.40） |
| Testcontainers / docker-java | 1.21.2 / 3.4.2 |
| MySQL / RabbitMQ | 8.4.11 / 3.9.11 |
| 资源限制 | Maven容器1.5 CPU、1024MiB内存；Maven堆256MiB、测试堆384MiB、测试JVM活动处理器数2 |

Docker API兼容性通过本轮运行参数 `-Dapi.version=1.44` 设置，未修改服务器Docker配置或项目依赖。完整环境与镜像摘要、源码核对结果、逐类逐项结果归档于[后端测试JSON](backend-2026-10-09.json)；[脱敏构建日志](backend-2026-10-09-build.txt)保留本轮完整输出，[源码摘要清单](backend-source-manifest-2026-10-09.json)保留385个后端文件的Git归档SHA-256。

## 2. 执行命令与完整性核对

本轮使用宿主网络、挂载Docker socket，并设置 `TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1`。OSS与RabbitMQ开关分别为 `OSS_INTEGRATION_ENABLED=true`、`RABBITMQ_INTEGRATION_ENABLED=true`；凭据通过容器环境文件注入，人脸开关及容器内素材路径通过下列Java系统属性注入。公开命令不包含密钥、原照片文件名或连接密码：

```bash
mvn --batch-mode --no-transfer-progress -f backend/pom.xml clean verify \
  -Dapi.version=1.44 '-DargLine=-Xmx384m -XX:ActiveProcessorCount=2' \
  -Dface.integration.enabled=true \
  -Dface.detection.model=/tmp/road-transport-full-20261009-13d6463/private/models/detection.onnx \
  -Dface.recognition.model=/tmp/road-transport-full-20261009-13d6463/private/models/recognition.onnx \
  -Dface.reference.image=/tmp/road-transport-full-20261009-13d6463/private/photos/reference.image \
  -Dface.same.image=/tmp/road-transport-full-20261009-13d6463/private/photos/same.image \
  -Dface.different.image=/tmp/road-transport-full-20261009-13d6463/private/photos/different.image \
  -Dface.multiple.image=/tmp/road-transport-full-20261009-13d6463/private/photos/multiple.image
```

本轮先清除旧构建产物，随后读取本轮Surefire报告：61个测试类与源码测试类清单全部匹配，无缺失或额外测试类；逐项状态合计与逐类统计一致。额外核对六个条件集成测试类均有测试执行，失败、错误、跳过均为0，六项门禁全部为真；因此本次“全量通过”同时包含报告完整性和条件测试实际执行，不仅依据 `BUILD SUCCESS`。

## 3. 全量结果

| 模块 | 测试类 | 测试项 | 通过 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| train-common | 3 | 4 | 4 | 0 | 0 | 0 |
| train-gateway | 1 | 4 | 4 | 0 | 0 | 0 |
| train-web-api | 2 | 3 | 3 | 0 | 0 | 0 |
| train-realtime-service | 8 | 27 | 27 | 0 | 0 | 0 |
| train-admin-service | 15 | 78 | 78 | 0 | 0 | 0 |
| train-training-service | 18 | 98 | 98 | 0 | 0 | 0 |
| train-face-adapter | 4 | 10 | 10 | 0 | 0 | 0 |
| train-learning-service | 10 | 42 | 42 | 0 | 0 | 0 |
| **合计** | **61** | **266** | **266** | **0** | **0** | **0** |

表中仅列有测试报告的模块；没有测试类的Reactor模块不增加测试计数。参数化测试按Surefire实际执行项计数，不按Java方法数计数。

以下为上述266项中的条件集成测试子集，**不另加到全量总数**：

| 测试类 | 本轮结果 | 实际覆盖与边界 |
| --- | --- | --- |
| AdminMapperIntegrationTest | 8通过、0跳过 | 临时真实MySQL中的管理库迁移、查询隔离、权限目录、多表事务回滚及数据库表达式 |
| TrainingMapperIntegrationTest | 9通过、0跳过 | 培训库迁移、索引、课程资产与企业隔离；并发结业事务和全员结业六个参数化边界 |
| LearningMapperIntegrationTest | 1通过、0跳过 | 学习库迁移、学时相关表及幂等和活动会话唯一约束 |
| AliyunOssIntegrationTest | 1通过、0跳过 | 真实私有OSS的分片上传、查询、合并、元数据、签名GET、206 Range内容及删除后不存在断言 |
| OpenCvFaceVerifierIntegrationTest | 1通过、0跳过 | 真实模型与4张授权照片：单脸、多脸、同图基础比对、同人不同照片、不同人、无人脸和损坏图片 |
| LearningOutboxRabbitIntegrationTest | 1通过、0跳过 | 真实Broker的独立路由退回、补齐临时队列后的恢复、重建发送器及重复事件ID；持久化Mapper为测试替身 |

三组数据库共18项（管理8、培训9、学习1），全部执行通过。OSS使用196608字节带MP4头的合成内容，不是完整可播放视频；人脸测试采用适配器默认相似度阈值0.363，登记照与同人照片内容不同，所选样本满足断言，但本次不估计识别准确率或活体效果。

## 4. 清理与证据保存

- OSS测试在 `finally` 中清理随机对象并断言对象不存在；RabbitMQ测试删除本次随机队列并关闭连接。Testcontainers临时MySQL及回收容器均已回收。
- 测试完成后，已删除远程整个任务临时目录及本机敏感上传包，并检查删除成功。原有业务容器继续运行，没有重启或改写业务数据。
- 公开归档只保留脱敏命令、运行环境、测试计数与用例状态、源码摘要及必要日志；不归档环境文件、原始Surefire系统属性、密钥、照片、模型或签名URL。本机 `.env.local` 和用户原始素材不因临时包清理而删除。
- 构建日志按真实配置值扫描并脱敏；严格用户名替换可能影响日志中同名模块片段的显示，正式模块名称与计数以测试JSON为准。日志及源码摘要清单的SHA-256保存在测试JSON的 `evidence.sha256`，清理核对结果保存在 `cleanup`。

## 5. 历史结果与剩余验收范围

[2026-09-09报告](acceptance-2026-09-09.md)的“58类210项、208通过、2跳过”仍是该次 `fcbb506` 版本的历史事实，本轮不回写历史报告，也不将两轮计数相加。当前后端自动化结果以本次 `13d64637` 的61类266项为准；前端55项单测、4项条件守卫、5项UI契约测试及原有真实接口结果仍按09-09归档解释，本轮未重跑前端。

本轮未执行浏览器OSS CORS、真实MP4解码与续签、摄像头采集、两种培训模式完整结业、1/5/10学员真实学习并发、自然到期档案，以及MySQL Outbox与业务服务重启的跨服务恢复实验。这些事项保持原边界，不因后端266项通过而标为已验收。论文及当前入口的实际修改、版本和复核结果统一见[本轮变更记录](../changes/2026-10-09-backend-full-verification.md)。
