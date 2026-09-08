# 本机运行与HTTPS演示

要求JDK17、Maven、Node22.12.0及以上、pnpm（版本见frontend/package.json）。业务应用以单实例JAR运行，基础设施使用已有开发环境或Docker Compose。该版本没有业务应用容器编排，也未实现跨实例WebSocket会话迁移。

## 启停

在仓库根目录的PowerShell执行。`.env.local`仅本机使用，按`.env.example`填写；Nacos连接及JWT密钥必须可用。

```powershell
# 使用现有开发基础设施时开启转发；使用本机Docker时执行start-infra.ps1
.\scripts\ssh-tunnel.ps1 start
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17.0.8'
.\scripts\build.ps1
.\scripts\start-apps.ps1 -JavaHome $env:JAVA_HOME -EnvironmentFile .env.local
.\scripts\check-environment.ps1
$env:VITE_GATEWAY_TARGET = 'http://127.0.0.1:8080'
pnpm --dir frontend dev
```

start-apps将6个JAR复制到tmp/local-run/current后隐藏启动，避免Windows锁定target内产物；已有受管PID或监听端口会保留。脚本显式设置端口，覆盖Nacos中的server.port。端口为8091管理、8092培训、8093学习、8081 Web API、8082实时、8080网关。进程和日志均在tmp/local-run/current，启动提交不代表就绪。

check-environment默认检查开发转发端口13306、16379、15673、18848及6个业务HTTP端口，只验证TCP连接。还须访问 `/api/auth/csrf` 并完成登录与业务接口检查。本机Docker默认基础设施端口不同，应按.env配置单独检查。若前端已有`.env.local`将代理设成18080，以上进程变量覆盖它。

```powershell
.\scripts\stop-apps.ps1
.\scripts\ssh-tunnel.ps1 stop
```

stop-apps只停止元数据内PID且命令行匹配本仓库JAR的Java进程，不停止端口上其他程序。退出后仍可保留前端手动启动会话供演示。

## 演示数据与验收

```powershell
node scripts/seed-demo.mjs
pnpm --dir frontend test:e2e --project=ui
$env:TRAIN_LIVE = 'true'
pnpm --dir frontend test:e2e --project=live --project=load
```

演示脚本要求本机环境内的平台账号可登录且已完成首次改密，不修改平台账号。每次先使用tmp/demo/state.json恢复本次进度，首次创建demo_时间戳命名的独立企业。脚本创建两个虚构企业、部门、学员、车辆、草稿课程和试卷。随机演示密码只保存在该忽略文件；不要把它、浏览器会话或运行日志作为公开论文附件。更换TRAIN_DEMO_STATE可创建新的独立数据集。

load项目执行1、10、50并发连接的已认证心跳实验，结果写入tmp/experiments/heartbeat.json；不绑定学习会话，不计入培训学时。结果与未验收范围见[验收报告](../test/acceptance-2026-09-07.md)。

云媒体步骤见 `node scripts/publish-demo.mjs`：需先设置TRAIN_DEMO_VIDEO为自制MP4绝对路径、TRAIN_DEMO_VIDEO_SECONDS为真实视频时长，至少60秒，并配置私有OSS。缺少配置时脚本明确失败，不伪造上传和发布成功。两份计划分别为仅学习与学习+考试，发布后规则冻结。

## HTTPS/WSS

先按主部署说明配置OSS CORS、JWT、Nacos和人脸模型。远程摄像头采集要求浏览器认可的安全上下文。部署在HTTPS时设JWT_SECURE_COOKIE=true，实时Origin允许列表填写实际域名，OSS CORS也填写同一来源。

```powershell
pnpm --dir frontend build
$env:TRAIN_TLS_CERT_DIR = 'D:/secure/train-certs'
docker compose --env-file .env -f deploy/docker-compose.yml -f deploy/docker-compose.https.yml config --quiet
docker compose --env-file .env -f deploy/docker-compose.yml -f deploy/docker-compose.https.yml up -d nginx
docker compose --env-file .env -f deploy/docker-compose.yml -f deploy/docker-compose.https.yml exec nginx nginx -t
```

证书目录需含fullchain.pem与privkey.pem；示例使用标准443端口，域名和证书由实际环境提供。Nginx挂载frontend/dist，SPA路由刷新回退index.html，`/api/`转发网关，`/ws/`保持Upgrade和转发协议头。验证首页、刷新档案路径、登录后的Secure Cookie、WSS握手101、摄像头授权、OSS Range播放及退出失效。没有证书或Docker的环境不得标为TLS部署验收通过。

回滚应用前备份三个业务数据库。V12仅新增车辆表与权限；降级旧应用可保留该表，避免执行破坏性删表。数据库备份恢复需在目标环境独立验证。
