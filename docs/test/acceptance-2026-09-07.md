# 功能扩展验收报告（2026-09-07至2026-09-08）

本次实现学习档案、学时监管、车辆管理和工作台首页，并补充演示、部署、接口及论文材料。报告区分真实接口、浏览器契约夹具与条件不足未执行项目；不把草稿课程和模拟档案当作已完成的真实培训。

## 环境与验证记录

环境为Windows、JDK17.0.8、Node24.19.0、pnpm11.19.0和本机Edge。开发基础设施经已有SSH转发连接，MySQL、Redis、RabbitMQ及Nacos可用，六个业务服务已启动。浏览器经过Vite 5173与Gateway 8080访问业务服务。

| 检查 | 结果与证据 |
| --- | --- |
| 后端领域与受影响依赖verify | 通过；tmp/domain-verify.log |
| 新企业权限查询回归 | 14项EnterpriseServiceImplTest通过；tmp/admin-permission-verify.log |
| 实时认证及协议回归 | 22项通过，包含真实Spring Security链的新增测试；tmp/realtime-chain-verify.log |
| 后端累计Surefire报告 | 200项，187通过、13按环境条件跳过，0失败、0错误；非同一轮完整Reactor执行 |
| 前端单元测试 | 14个文件、55项通过；tmp/frontend-test.log |
| 前端静态检查与构建 | ESLint通过，vue-tsc及Vite build通过；tmp/frontend-lint.log、tmp/frontend-build.log |
| E2E TypeScript检查 | 在frontend目录用现有tsc校验通过；tmp/e2e-typecheck.log |
| 浏览器验收 | 累计8项通过：ui 4项、live 3项、load 1项；分两轮验证，证据见下文 |
| 冻结依赖安装 | pnpm install --frozen-lockfile通过；tmp/frozen-install.log |
| 数据库迁移 | 管理库V12车辆和V13学员权限修复已在真实开发库执行 |
| 脚本及文档结构 | PowerShell/Node语法、OpenAPI JSON及引用、变更文件UTF-8无BOM检查通过 |

Vite仍提示公共入口包超过500kB，这是现有依赖打包的体积告警；本次未做无关拆包重构。CI工作流增加冻结安装、lint及浏览器契约测试，但未在远程CI执行，现有锁文件仍使用项目配置的包仓库。

跳过项为AdminMapperIntegrationTest 8项、LearningMapperIntegrationTest 1项、TrainingMapperIntegrationTest 2项（无本机Docker），以及真实OSS、人脸素材测试各1项。这些跳过测试不计入通过数。

## 浏览器验收覆盖

ui项目4项在tmp/e2e-final.log中通过。该轮真实联调发现的问题修复后，live项目3项和load项目1项在tmp/e2e-live-final.log中全部通过（44.6秒）。累计8项为各项目最新结果，不将较早日志中的真实联调失败计作通过，也不声称同一轮8项全绿。

ui项目使用确定的接口夹具，覆盖：结束计划档案、课程学时、只读请求、零提交超时抽验、首页卡片筛选和导航、权限错误展示、车辆新增/编辑/停用/启用。夹具只拦截 `/api/` 请求，避免误拦截Vite的 `/src/api/` 模块。

live项目使用seed-demo创建的两个独立虚构企业，经真实接口验证：车辆查询、同企业车牌冲突S9001、跨企业车辆修改S9002、学员首页与档案查询、学员访问管理员接口A0006，以及已认证WebSocket心跳往返。负向断言检查具体业务码，不以任意失败作为权限校验成功。

真实环境尚无已上传课件和已发布培训，因此真实学员档案目前为空。非空历史详情与零提交抽验的页面展示由ui夹具验证，归属与只读逻辑由服务测试验证；不声称已经用真实结业记录完成端到端验收。

## 联调发现并修复的问题

1. 新企业的内置学员角色没有权限。现用MyBatis-Flex的likeRight生成后缀匹配，无法匹配student开头的权限码。改成显式 `LIKE 'student:%'`，测试断言实际SQL；V13仅修复没有任何授权的企业内置STUDENT角色。迁移后真实演示学员恢复workspace、plan、learning和exam四项权限。
2. WebSocket认证过滤器早于Spring Security执行，已设置的Principal被安全上下文包装覆盖，连接升级后立即关闭4401。调整过滤器顺序，并增加完整WebFlux/Spring Security链的主体保留回归测试。
3. Nacos将网关端口配置为18080，与默认代理8080不一致。启动脚本显式设置各服务端口，前端运行时代理与网关一致。
4. Windows直接运行target内JAR会锁住Maven重新打包文件。启动脚本先复制到受管运行目录，停止时验证PID、Java命令行与仓库路径。
5. 构建脚本原先未检查原生命令退出码；已改为构建或安装失败即中止，避免输出误导性的完成信息。

## 确定性学时实验

执行真实LearningTimeCalculator，以可复现输入校验计算边界；此实验不运行浏览器视频、不经过网络和数据库。

| 输入场景 | 媒体增量 | 实际计入 | 说明 |
| --- | ---: | ---: | --- |
| 100段均匀1秒上报 | 100000ms | 100000ms | 服务器与媒体一致 |
| 100段媒体各1秒，服务器间隔交替0.9/1.1秒 | 100000ms | 95000ms | 保守取最小值导致5000ms差额，不多计 |
| 31秒未上报，最大间隔30秒 | 31000ms | 0ms | 超时不补记 |
| 服务器/媒体前进10秒，课程仅剩3秒 | 10000ms | 3000ms | 不超过剩余规定学时 |

本机输出：tmp/learning-time-experiment.json。该结果说明算法在抖动输入下可能少计，不能将其表述为“任何网络条件下误差为零”。真实端到端学时误差仍需有效MP4和OSS条件。

## WebSocket心跳实验

2026-09-08 01:06（Asia/Shanghai）在本机Edge执行一次短时实验。同一已认证学员分别建立1、10、50个并发连接，每个连接顺序发送20次心跳。累计收到1220个PONG，连接错误为0；[原始实验数据](heartbeat-2026-09-08.json)保留未取整数值。

| 并发连接 | 收到响应 | 错误 | 批次耗时（ms） | 响应/秒 | P50（ms） | P95（ms） | 最大值（ms） |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 20 | 0 | 724.1 | 27.62 | 12.0 | 60.0 | 249.1 |
| 10 | 200 | 0 | 1560.5 | 128.16 | 9.0 | 52.5 | 296.5 |
| 50 | 1000 | 0 | 5544.2 | 180.37 | 4.2 | 46.0 | 235.5 |

批次耗时包含建立连接，延迟为每次心跳发送到PONG到达的浏览器往返时间。此实验经过真实网关及实时服务，但没有绑定学习会话或计入学时，且各批次样本量、预热程度不同。结果用于证明认证与心跳链路可运行，不能据此推断多用户生产容量、并发学时落库吞吐或长期稳定性。

## 复现命令

```powershell
# 先按部署手册启动服务，以下命令从仓库根目录运行
node scripts/seed-demo.mjs
pnpm --dir frontend test
pnpm --dir frontend lint
pnpm --dir frontend build
pnpm --dir frontend test:e2e --project=ui
$env:TRAIN_LIVE = 'true'
pnpm --dir frontend test:e2e --project=live --project=load

# JDK17源文件方式运行，先构建learning与common-core
java '-Dfile.encoding=UTF-8' -cp 'backend/train-learning-service/target/classes;backend/train-common/train-common-core/target/classes' scripts/LearningTimeExperiment.java
```

浏览器报告位于frontend/playwright-report，截图位于output/playwright；带ui-fixture的图片使用模拟数据。演示凭据、日志和截图默认忽略，不应包含在公开论文附件中。

## 待验收项目

- OSS AccessKey、Bucket及前端CORS未配置：真实MP4上传、Range播放、签名续期、两种培训模式的完整学习/考试/结业流程尚未验收。
- 没有已授权的人脸登记/不同人/多人测试素材：真实照片上传、抽验成功/失败与模型准确率未验收；现有模型文件不等于已完成真实模型测试。
- 完整学习条件不足：并发学时落库、Outbox端到端延迟、RabbitMQ重投与服务重启后的完整恢复实验尚未执行。
- 没有本机Docker和演示域名证书：Nginx HTTPS配置模板已提供，nginx -t、TLS/WSS部署、远程摄像头及备份恢复未在目标环境验收。

这些条件具备后，应使用已提供的发布脚本和部署说明继续补齐真实闭环，保留本报告已有证据而不替换成推测结果。
