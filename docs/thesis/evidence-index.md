# 论文与演示证据索引

本表记录证据来自哪里及能支持什么结论。最新状态以[阶段报告](../test/media-acceptance-2026-09-08.md)为准；操作见[本机启动](../deployment/local-demo.md)和[真实媒体验收](../test/media-acceptance.md)。路径相对仓库根目录，tmp和output为本机忽略目录。

| 论文论点或演示内容 | 证据入口 | 当前边界 |
| --- | --- | --- |
| 自制100秒MP4可解码 | tmp/media/training-demo.json、tmp/media/video-browser-check.json、tmp/media-video-full-decode.log | 本机视频解码通过，未经过OSS或学习计时 |
| 企业车辆、档案权限与认证心跳 | tmp/media-live-regression.log | 3项真实接口回归通过；不是完整培训闭环 |
| 全部视频播完但学时不足可补学 | tmp/media-learning-test.log、tmp/media-ui-test.log | 后端规则和UI夹具通过；真实视频差额未测 |
| 合成抖动下100秒计入95秒 | LearningTimeCalculatorTest、tmp/media-domain-regression.log | 合成输入结果，不代表现实误差分布 |
| 抽验、考试、结业与乱序消费规则 | tmp/media-domain-regression.log | 领域定向测试，数据库Mapper为替身 |
| 无路由消息不丢弃及路由恢复 | tmp/media-rabbit-test.log | 真实Broker；不是完整服务和MySQL重启 |
| 正常完成、考试与统计一致 | media测试报告附件real-completion.json；output/playwright/*-media-real.png | 尚未生成真实闭环结果；执行后核对任务ID、时间及计划模式 |
| 同人不同照、不同人、多人照片 | 模型集成测试及media抽验记录 | 未执行；登记照与同人照片必须不同内容 |
| 真实摄像头采集 | camera独立学员、实际抽验任务及操作者记录 | 未执行；文件上传测试不能替代摄像头 |
| 断网、多标签、乱序和跨企业记录访问 | media恢复测试结果 | 测试入口已实现，未执行 |
| 1/5/10学员学习60秒 | tmp/experiments/study-人数-时间戳.json | 尚无数值；记录学时差额、解码帧数、P50/P95及状态可见时间 |
| MySQL持久化与服务重启 | 手册消息恢复步骤、事件ID关联的Outbox与消费日志 | 未执行；只读采集，不直接改完成状态 |
| 11项数据库条件测试 | GitHub Actions后端日志及Surefire报告 | CI门禁已增加，当前未执行 |
| 自然到期计划历史档案 | 学员与管理档案截图、结束时间及任务ID | 待真实完成及自然到期，禁止改库制造历史成绩 |

## 每次实验的记录字段

归档代码版本或未提交差异、时间和时区、JDK/Node/浏览器版本、单实例服务与基础设施位置、计划/任务/会话ID、操作步骤、预期、实测、结论以及证据路径。失败也保留记录，并说明重跑是否使用了新的独立数据集。照片授权与原图仅在本机保存；公开报告不包含凭据、Cookie、签名URL和原始照片。

截图分为真实接口页面、UI契约夹具和真实媒体三类，图题明确注明来源。不要将带ui-fixture文件名的图片改名为真实成绩。实际媒体完成后，由真实报告数据填写结果表，再将讲稿中“尚未执行”改为与证据一致的表述。
