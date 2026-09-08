# 本机演示交付进展（2026-09-08）

本轮交付脚本、自制视频、真实验收入口、定向修复和本机回归；真实OSS闭环、人脸比对和学习并发仍待OSS配置与授权照片。前一轮报告保留在[既有验收报告](acceptance-2026-09-07.md)，两轮计数不累加为同一轮全量结果。

## 已实现

- 保留原有两种演示计划；新增25个独立学员以及学习、考试、抽验成功/失败/超时、恢复、人工摄像头和1/5/10学员并发场景的准备与发布能力。
- 自制100秒H.264视频，包含流程文字、连续计时与动画，无人物和外部图片；本机配置已写入视频路径、时长及现有模型路径，FACE_ENABLED已开启。
- 独立media及study-load浏览器项目、失败即中断的条件预检、同人不同照片模型测试及独立消息路由实验。
- 补学入口：全部课件已播放而有效学时不足时，允许对已完成课件发起PLAY位置0；服务端只重置当前位置，保留累计学时、完成标记和最高确认位置。
- 视频已被服务端判定完成时，结束事件不再重复提交非法PAUSE。
- Outbox开启mandatory/returns；Broker ACK同时伴随NO_ROUTE时保留重试，避免错误标记SENT。
- 按服务名定向启停；数据库条件测试CI门禁；E2E类型检查；完整[验收步骤与人工待办](media-acceptance.md)。

## 本轮实际验证

| 检查 | 结果 | 本机证据 |
| --- | --- | --- |
| 补学及Outbox定向测试 | 15项通过，0跳过 | tmp/media-learning-test.log |
| 领域回归 | 8个测试类、36项通过，0跳过；学习服务25项、培训服务11项，包含100秒抖动计入95秒 | tmp/media-domain-regression.log |
| 真实RabbitMQ实验 | 1项通过；独立路由退回、补队列后恢复、发送器重建及重复事件ID；临时队列已清理 | tmp/media-rabbit-test.log |
| 脚本条件验证 | 4项Node测试通过；缺配置或空计划时失败并列出缺项 | scripts/lib/media-config.test.mjs |
| 前端单元测试 | 14个文件、55项通过 | tmp/media-frontend-unit.log |
| 前端类型检查与构建 | E2E TypeScript、应用类型检查、ESLint与Vite构建通过 | tmp/media-frontend-build.log及本轮终端输出 |
| 浏览器UI契约 | 5项通过，包含补学交互；媒体行为为明确夹具 | tmp/media-ui-test.log |
| 原有真实接口回归 | 3项通过，覆盖车辆、档案权限和认证心跳；更新学习服务后复跑3项通过 | tmp/media-live-regression.log、tmp/media-live-after-restart.log |
| 演示账号 | 真实开发环境新增25名独立验收学员；当前OSS能力关闭，未发布媒体计划 | tmp/media-seed.log及忽略目录的演示状态 |
| 自制视频 | 100秒、960×540、20fps、636261字节；FFmpeg全片解码2000帧通过，Edge读取时长100秒并真实播放约3秒，无媒体错误 | tmp/media/training-demo.mp4、tmp/media/training-demo.json、tmp/media/video-browser-check.json、tmp/media-video-full-decode.log |
| 模型启用与本机服务 | 学习服务更新后启动成功，启用现有YuNet/SFace模型，进程已加载opencv_java490.dll，健康检查UP；其余服务与基础设施端口可连接 | tmp/local-run/current/train-learning-service.out.log及本轮健康检查 |
| 数据库执行门禁 | 本机报告仍为8+2+1项跳过，门禁按预期返回1；不是数据库测试通过 | tmp/media-database-gate.log |

后端采用JDK17.0.8；浏览器自动化使用Node24.19.0和本机Edge。发现默认终端Node18不满足Playwright要求后，仅切换测试进程的Node路径重新执行。Vite公共入口仍有超过500kB的既有体积告警，本轮未做拆包重构。

消息实验首次因本机环境文件未包含RabbitMQ连接参数而明确失败；随后从正在使用的Nacos本地快照读取连接参数到当前进程，完成真实Broker实验。没有把连接密码写入仓库、命令参数或报告。

补充考试测试时发现MyBatis-Flex更新代理不能通过实体getter读取待更新值，改为按项目现有模式断言UpdateWrapper内容后通过。验收脚本同时纠正了“未学完不得进入考试”的错误假设，保持既有接口允许的进入顺序，验证结业必须同时满足两项条件。

自制视频的本机解码检查使用data URL，不经过OSS，不代表Range、CORS、签名续期或学习落库通过。视频生成依赖Pillow与FFmpeg，本轮FFmpeg来自临时目录安装的imageio-ffmpeg 0.6.0，不修改项目依赖或提交二进制工具。

## 未执行与交付限制

- 当前运行中OSS能力仍关闭，未提供已授权照片；8项media测试、3组真实学习并发测试、真实OSS及同人不同照片模型测试未执行。已生成的MP4可直接用于后续上传。
- 没有真实结业、自然到期档案或摄像头采集证据；脚本与测试代码的存在不等于这些场景验收成功。
- 本机无Docker，11项数据库条件测试未补跑；新的远程CI工作流尚未执行。
- 签名真实到期、Token真实到期、页面隐藏、权限撤销、MySQL Outbox与完整业务服务重启、CPU/内存及实际投递延迟，按手册保留人工/故障验收记录。
- RabbitMQ实验使用真实消息传输及生产发送器，数据库Mapper为测试替身；不能替代上述完整持久化恢复实验。

继续执行前，按验收手册补齐本机配置和素材，重新运行预检与发布脚本，再归档真实结果。当前阶段仍为“交付验证进行中”。

论文材料已同步[技术底稿](../thesis/design-and-defense.md)、[约8分钟讲稿](../thesis/defense-script-8min.md)及[证据索引](../thesis/evidence-index.md)，车辆和学习档案的旧“不包含”条目已修正，历史报告保留。
