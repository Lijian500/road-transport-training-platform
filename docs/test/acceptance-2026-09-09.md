# 本机论文演示交付与CI补验（2026-09-09）

本轮已将11项数据库条件测试放到GitHub Actions的Docker环境执行并全部通过，前后端CI均已成功。真实媒体验收工具、自制视频、补学修复与论文材料已交付；OSS、授权照片、摄像头及完整培训数据仍待补齐，整个业务闭环尚未完成验收。

改动已提交并推送至`codex/local-demo-media-acceptance`分支，主分支未合并。[09-08本机报告](media-acceptance-2026-09-08.md)及[此前报告](acceptance-2026-09-07.md)保留历史验证记录。机器可读计数见[CI结果JSON](ci-2026-09-09.json)，不同轮次及重复运行不累加。

## 1. 本轮真实CI结果

| 验证 | 结果 | 证据 |
| --- | --- | --- |
| 后端JDK17全量clean verify | 58个测试类、210项：208通过，0失败，0错误，2跳过 | [Backend CI](https://github.com/Lijian500/road-transport-training-platform/actions/runs/34250552187)，代码fcbb506 |
| 管理库MySQL测试 | 8通过、0跳过，含迁移、查询隔离、多表回滚与数据库表达式 | 同一后端作业的AdminMapperIntegrationTest |
| 培训库MySQL测试 | 2通过、0跳过，含迁移/索引与企业隔离 | 同一后端作业的TrainingMapperIntegrationTest |
| 学习库MySQL测试 | 1通过、0跳过，含学时表及幂等唯一约束 | 同一后端作业的LearningMapperIntegrationTest |
| 数据库执行门禁 | 成功；读取上述8+2+1项报告，任何跳过或缺报告都会失败 | 同一后端作业的Require database integration tests to execute步骤 |
| 真实RabbitMQ实验 | 1通过、0跳过，临时队列和独立路由 | 同一后端作业的LearningOutboxRabbitIntegrationTest；属于208项通过之一 |
| 前端完整CI | 安装、依赖来源校验、ESLint、应用/E2E类型检查及构建通过；14个文件55项单测、4项条件守卫、5项UI契约测试通过 | [Frontend CI](https://github.com/Lijian500/road-transport-training-platform/actions/runs/34249525727)，代码7ca2f94 |

前后端作业的代码提交不同，7ca2f94之后仅修改数据库测试及报告，前端源码和工作流没有变化。后端剩余2项跳过分别是真实Aliyun OSS测试和同人不同照片模型测试，CI没有注入相关密钥或照片。Docker数据库测试成功不替代完整培训流程、MySQL Outbox服务重启或真实学时性能测试。

## 2. CI发现并修复的问题

| 触发与失败 | 修复 | 验证 |
| --- | --- | --- |
| 首轮前端安装失败，锁文件385个下载地址指向本机公司镜像 | 改为官方npm地址，并在frontend/.npmrc固定公开依赖来源；包版本、依赖关系及integrity未改变 | 本机与CI的385项来源检查通过，未关闭校验策略 |
| 首轮管理库8项测试因缺少JdbcTemplate不能启动 | 三个Mapper测试的最小上下文补充JdbcTemplateAutoConfiguration | 后续Docker测试真实执行 |
| 管理库权限目录查询为空 | 测试中的likeRight改为显式前缀likeRaw，保留原有权限内容和数量断言 | 管理库权限迁移断言通过 |
| 最小上下文中多表回滚未生效 | 三个Mapper测试补齐FlexTransactionAutoConfiguration，与正常Spring Boot装配一致 | 管理库真实多表回滚通过 |
| 培训库独立测试被Nacos配置导入检查拦截 | 培训库、学习库与管理库一致，显式关闭测试中的Nacos配置/发现及Dubbo | 其余3项数据库测试及全量构建通过 |

事务装配处理参考[MyBatis-Flex官方事务说明](https://mybatis-flex.com/zh/core/tx.html)，并以真实MySQL回滚结果验证。修复没有替换数据库断言为Mock，也没有放宽依赖来源校验。

失败运行也保留：[首次前端失败](https://github.com/Lijian500/road-transport-training-platform/actions/runs/34249148377)、[首次后端装配失败](https://github.com/Lijian500/road-transport-training-platform/actions/runs/34249148372)、[权限查询与回滚失败](https://github.com/Lijian500/road-transport-training-platform/actions/runs/34249525857)、[Nacos检查失败](https://github.com/Lijian500/road-transport-training-platform/actions/runs/34250081712)。

## 3. 本机已交付内容

- `seed-demo.mjs --media`已建立25名独立验收学员；发布脚本保留原有两种模式，追加抽验成功/失败/超时、考试超时、恢复及并发场景。
- `tmp/media/training-demo.mp4`为自制100秒H.264视频，960×540、20fps、636261字节。FFmpeg全片2000帧解码和Edge约3秒播放检查通过，生成方式、摘要和证据见09-08报告；本机解码未经过OSS。
- 本机已配置视频路径与时长，启用现有YuNet/SFace模型。学习服务已更新运行并通过健康检查，原始本机配置备份保留在Git忽略目录。
- 全部课件播放完而有效学时不足时可从头补学，保留累计学时、完成标记和最高确认位置；PLAY本身不计时。100秒抖动输入计入95秒的合成实验已纳入回归。
- Outbox同时检查Broker确认和无路由退回，避免NO_ROUTE被误记为SENT；重复消费与迟到开始事件不回退完成状态有定向回归。
- 独立8项media与3组study-load验收已实现，缺配置或未发布场景时明确失败。支持按服务名启停，便于本机恢复实验。
- 已同步[启动说明](../deployment/local-demo.md)、[真实媒体验收手册](media-acceptance.md)、[论文技术底稿](../thesis/design-and-defense.md)、[约8分钟讲稿](../thesis/defense-script-8min.md)及[证据索引](../thesis/evidence-index.md)。车辆和学习档案的旧“不包含”条目已修正。

## 4. 尚需补齐的真实证据

09-09复查时，运行中OSS能力仍为关闭，四项授权照片路径均未提供，媒体计划数为0。当前不得宣称真实结业、真实照片比对、摄像头采集或1/5/10学员学习并发实验已通过。

继续执行需要在本机`.env.local`配置专用私有OSS的Bucket、地域、Endpoint及受限测试身份，设置`OSS_ENABLED=true`，按实际浏览器来源配置CORS并重启培训服务。再填写`TRAIN_FACE_REFERENCE`、`TRAIN_FACE_SAME_PERSON`、`TRAIN_FACE_DIFFERENT_PERSON`、`TRAIN_FACE_MULTIPLE`四个本地授权照片路径；同人照片必须与登记照不同。视频和模型已就绪。

条件齐备后依次运行预检、`publish-demo.mjs --media`、8项media测试、3组study-load实验及OSS/模型条件测试，人工补充真实摄像头、真实Token/签名到期、页面隐藏、权限撤销、服务重启、自然到期档案和资源指标。具体命令与预期结果见验收手册。远程HTTPS、行管跨企业监管和多实例扩展仍后置。
