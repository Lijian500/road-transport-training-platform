# 真实媒体与本机答辩验收

本手册接续既有车辆、权限和心跳验收，使用真实OSS、浏览器视频、学习服务与培训服务。入口显式开启，缺少条件即失败；`ui`夹具项目保持独立。当前实际执行结果见[2026-09-08报告](media-acceptance-2026-09-08.md)。

## 1. 环境与素材

使用JDK17、Node.js 22.12及以上、本机Edge以及已有开发基础设施。先运行`node --version`和`mvn --version`；本次检查发现默认Node为18，实际浏览器验证使用本机已有的Node24.19.0，不修改系统默认版本。

本机已生成`tmp/media/training-demo.mp4`，时长100秒，H.264/yuv420p、faststart，无人物、外部图片与音轨；仅包含自制流程文字、计时器和连续动画。全片FFmpeg解码及Edge三秒播放检查通过，参数和SHA-256见同目录JSON。可以直接使用该文件，也可自行替换内容。

重新制作时准备Python、Pillow、支持libx264的FFmpeg和本机中文字体，运行`python scripts/make-demo-video.py --ffmpeg '实际ffmpeg.exe路径' --output tmp/media/new-demo.mp4`。脚本拒绝覆盖已有证据，默认100秒；本轮FFmpeg工具仅安装在忽略目录tmp/media-tools中，不属于项目依赖。配置视频路径和时长后，运行`node scripts/check-demo-video.mjs`可复查本机解码；此检查不经过OSS。

在仓库根目录的`.env.local`填写以下配置。密钥、原始照片、演示凭据不进入Git，也不作为公开论文附件。

| 配置 | 要求 |
| --- | --- |
| OSS_ENABLED、OSS_BUCKET、OSS_ACCESS_KEY_ID、OSS_ACCESS_KEY_SECRET | 专用私有测试Bucket与限定到该Bucket的测试身份 |
| OSS_REGION、OSS_ENDPOINT | 与测试Bucket实际地域一致 |
| FACE_ENABLED | true；需要重启学习服务使模型配置生效 |
| FACE_DETECTION_MODEL_PATH、FACE_RECOGNITION_MODEL_PATH | 已有YuNet、SFace模型的绝对路径；模型目录为backend/train-face-adapter/models |
| TRAIN_DEMO_VIDEO、TRAIN_DEMO_VIDEO_SECONDS | 本机已配置tmp/media/training-demo.mp4与100；替换素材应为60至180秒、100MiB以内的MP4及真实时长 |
| TRAIN_FACE_REFERENCE | 获授权的单人登记照，JPEG或PNG，5MiB以内 |
| TRAIN_FACE_SAME_PERSON | 同一个人的另一张照片；预检拒绝复制或改名后的相同内容 |
| TRAIN_FACE_DIFFERENT_PERSON、TRAIN_FACE_MULTIPLE | 获授权的不同人及多人照片，用于失败场景 |
| TRAIN_DEMO_STATE | 默认tmp/demo/state.json；重新实验时指定新的忽略目录内JSON文件 |

OSS CORS按照[部署说明](../deployment/README.md)配置实际来源，例如`http://127.0.0.1:5173`；允许PUT、GET、HEAD及Range所需请求头，暴露ETag、Content-Range等必要响应头。localhost与127.0.0.1是不同来源，实际使用哪个就配置哪个。

服务配置以运行中实例为准。通过Nacos配置RabbitMQ的环境，定向消息测试还需在当前进程或本机环境文件提供同一组`RABBITMQ_HOST/PORT/USERNAME/PASSWORD`。不要把连接密码写到命令参数中。

## 2. 准备与运行

以下命令从仓库根目录运行。先按[本机部署手册](../deployment/local-demo.md)启动基础设施和六个业务服务。

```powershell
# 只检查本地文件与配置声明，不代表云端或模型已验收
node scripts/run-media-acceptance.mjs --check

# 可先独立执行；保留原有两个企业及两位学员，并新增25个验收学员
node scripts/seed-demo.mjs --media

# 通过既有上传、登记与发布接口准备计划，不写数据库完成状态
node scripts/publish-demo.mjs --media

# 原有契约与基础真实接口回归
pnpm --dir frontend test:e2e --project=ui
$env:TRAIN_LIVE = 'true'
pnpm --dir frontend test:e2e --project=live

# 真实媒体项目：8项；真实学习并发项目：1、5、10学员三组
node scripts/run-media-acceptance.mjs
node scripts/run-media-acceptance.mjs --load

# 条件集成测试，可分别执行rabbit、oss、face，或使用all依次执行
.\scripts\test-media-integrations.ps1 -Suite rabbit -JavaHome 'C:\Program Files\Java\jdk-17.0.8'
.\scripts\test-media-integrations.ps1 -Suite oss -JavaHome 'C:\Program Files\Java\jdk-17.0.8'
.\scripts\test-media-integrations.ps1 -Suite face -JavaHome 'C:\Program Files\Java\jdk-17.0.8'
```

发布脚本保留原有仅学习、学习加考试计划，另建立9个业务/人工验收场景与3个并发组。新增课程包含两个顺序课件，规定学时等于两段视频总时长，便于检查播完仍缺学时的补学行为。超时考试使用独立的一分钟试卷；每个业务场景以及每组并发实验均使用不同学员。新计划约两分钟后生效。

同一状态文件用于准备过程的失败重试，已发布快照不修改。测试要求学习样本的有效学时从零开始；完成或部分使用过的样本应指定新的`TRAIN_DEMO_STATE`重新准备，不能清空正式记录以重复实验。状态文件可能含随机密码，只保留本机。

本地路径预检仅核对存在性、格式声明与照片内容差异。真实视频时长、CORS、解码、抽验结果仍由后续浏览器和模型测试验证。Node上传脚本成功不能替代浏览器上传成功。

## 3. 自动化覆盖与证据

| 场景 | 实际断言 |
| --- | --- |
| 浏览器直传 | 通过现有管理页面上传MP4，读取浏览器真实元数据，对照声明时长 |
| 私有播放 | 真实视频解码、跨域Range返回206及正确字节数；未解锁课件拒绝签名 |
| 学习与结业 | 仅学习、学习加考试、人脸通过加考试分别闭环；不足学时通过页面补学，不加速播放、不伪造学时 |
| 考试 | 学习已完成但考试未通过时仍未结业；答案保存后刷新恢复；重复交卷结果相同；跨企业不能读取成绩；截止后自动判为超时且不结业 |
| 抽验 | 真实照片通过或失败；多人照片消耗一次尝试，同requestId重发不重复计次；不同人耗尽重试；零提交超时可查询 |
| 学习恢复 | 暂停不计时、断网后安全暂停、重连保持暂停、重复事件不增加学时、乱序拒绝、旧会话拒绝、多标签页接管 |
| 档案与统计 | 本人和管理员档案一致、规定学时与有效学时一致、计划完成数正确；跨企业档案和会话访问被拒绝 |
| RabbitMQ | 独立demo.verify路由缺少队列时保留重试；补齐队列并重建发送器后投递；重复投递保持相同事件ID |
| 真实学习并发 | 1、5、10名独立学员，分别观察60秒正常播放，实际播放不足59秒时失败；记录解码帧数、确认响应P50/P95、有效学时与媒体位置差、开始状态可见等待时间 |

RabbitMQ实验使用真实Broker及发送器，但持久化Mapper是测试替身，不能据此宣称MySQL Outbox与完整业务服务重启恢复已通过。生产消费者的重复消息幂等和迟到开始事件不回退完成状态，由对应服务测试单独覆盖。

并发实验的开始状态可见等待包含RPC和轮询查询开销，不等同于Broker内部投递延迟。每次采样输出新的`tmp/experiments/study-*.json`，保留原始样本；禁止用心跳吞吐代替真实学习落库吞吐。

浏览器报告为`frontend/playwright-report`；最终档案截图为`output/playwright/*-media-real.png`。每轮归档时记录Git提交及是否含未提交改动、环境版本、执行命令、时间和通过/失败/跳过数。采集脚本不会把Cookie、签名URL或人脸原图写入实验JSON。

## 4. 必须补充的人工与故障验收

这些项目尚未被自动化脚本完整覆盖，执行后逐项登记实测结果，不使用默认“通过”。

| 项目 | 操作与通过标准 |
| --- | --- |
| 真实摄像头 | 使用独立camera账号进入对应计划；在本机localhost或127.0.0.1启用摄像头，实拍、提交、通过并手动继续。另验证拒绝授权后的明确提示。自动照片提交不能替代本项。 |
| 真实签名到期 | 在隔离演示配置缩短学习URL有效期并重启培训服务；记录签名截止时间，过期后让播放器重新发起Range请求，确认获得新签名并从服务端位置恢复。浏览器缓存命中不能算到期验收；完成后恢复配置。 |
| 页面隐藏 | 学习中切换实际浏览器标签页或最小化窗口，确认document.hidden为true后暂停；返回后保持暂停，按已确认位置继续。 |
| Access Token到期 | 仅在本机演示配置缩短Access Token有效期，新登录后等待真实到期；确认4401关闭、HTTP刷新及重新绑定，原会话保持安全暂停；完成后恢复配置。 |
| 权限撤销 | 使用独立isolation账号学习；管理员撤销该账号学习权限或停用账号，确认其连接关闭且新请求被拒绝；记录具体业务码并恢复该账号原配置。避免修改所有学员共用的内置角色。 |
| 服务重启与Outbox | 仅停止本机培训服务，在演示学员完成学习后保留学习库与Broker；重新启动培训服务，确认任务投影及统计自动收敛。再在存在待发送记录时重启学习服务，核对同一eventId恢复发送、不重复结业；不停止共享Broker或修改正式队列。 |
| 历史档案 | 提前建立短有效期的独立计划并真实完成学习/考试；自然到期后查询档案与监管详情，确认仍可查且查询不创建会话、不启动考试。不得直接改库伪造结束计划。 |
| 资源与投递延迟 | 实验期间记录学习/培训服务及数据库CPU、内存，并对照独立任务的Outbox创建/发送时间和消费记录时间；记录时钟同步条件。自动并发JSON暂不包含这些指标。 |

现在可以按服务名定向启停，仍保留PID、Java命令行与仓库路径校验：

```powershell
.\scripts\stop-apps.ps1 -Service train-training-service
.\scripts\start-apps.ps1 -Service train-training-service -JavaHome 'C:\Program Files\Java\jdk-17.0.8'
```

定向启动会复制新的构建JAR；进程仍存活时不会覆盖或替换。每次故障实验结束，确认目标服务就绪及演示任务状态一致。

## 5. CI与交付标准

后端CI新增RabbitMQ临时服务和手动触发入口；构建后检查Admin、Training、Learning三组数据库测试实际执行，任何跳过、缺报告或失败都使该检查失败。该门禁依赖本轮`clean verify`避免沿用旧报告。前端CI增加E2E TypeScript和媒体条件守卫测试，相关脚本变更也触发检查。

完成交付前需同时具备：两种培训模式及人脸流程的真实成功记录；失败、超时与恢复记录；自然结束计划的只读档案；真实摄像头记录；原始实验数据与环境说明；可复现启动步骤。外部条件不足时保留待验收状态，不把准备好的账号或草稿课程当作已结业培训。
