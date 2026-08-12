# 部署说明

当前阶段提供本地基础设施编排，Java业务应用通过IDE或Maven独立运行，不构建
业务容器。

## 本地基础设施

1. 安装Docker Desktop并确认`docker compose`可用。
2. 将仓库根目录`.env.example`复制为`.env`。
3. 替换所有`change-me`或`replace-with`示例值。
4. 在仓库根目录执行`./scripts/start-infra.ps1`。
5. 完成开发后执行`./scripts/stop-infra.ps1`。

根目录`.env`是本项目唯一的环境变量模板来源，不得提交真实密码。`stop-infra.ps1 -RemoveVolumes`会删除本项目本地数据卷，脚本会要求输入`DELETE`二次确认。

## 阿里云OSS

课程封面和MP4视频使用私有阿里云OSS。将根目录`.env.example`中的`OSS_*`占位复制到
`train-training-service`的进程环境；AccessKey只允许通过环境变量注入。未配置或
`OSS_ENABLED=false`时服务仍可启动，课程CRUD可用，但管理端上传按钮会禁用并展示原因。

Bucket配置要求：

- 读写权限设为私有；生产环境建议使用仅允许目标Bucket必要对象操作的RAM子账号；
- CORS来源只填写实际管理端域名，开发环境可另加`http://localhost:5173`；
- 允许方法为`GET`、`HEAD`、`PUT`，允许请求头按OSS控制台使用`*`或签名实际请求头；
- 暴露响应头`ETag`，缓存预检响应；禁止把Bucket设为公共读；
- 建议配置未完成分片生命周期规则作为服务端定时清理之外的兜底。

固定默认值为8 MiB分片、上传签名15分钟、预览签名30分钟、会话24小时、视频最大
5 GiB、封面最大5 MiB。修改大小或TTL时只调整服务环境变量，前端通过能力接口读取，
无需另行配置。本期不启用CDN、云点播或转码。

## JWT密钥

在PowerShell 7中执行：

```powershell
.\scripts\generate-jwt-keys.ps1
```

脚本默认在仓库`.keys`目录创建PKCS#8私钥和X.509公钥，并在目标文件已存在时拒绝
覆盖。将绝对路径分别写入：

- `JWT_PRIVATE_KEY_PATH`：仅`train-web-api`；
- `JWT_PUBLIC_KEY_PATH`：`train-web-api`和`train-gateway`。

生产环境必须由密钥管理系统挂载独立密钥，并设置`JWT_SECURE_COOKIE=true`。

## 人脸模型

在仓库根目录执行：

```powershell
.\scripts\download-face-models.ps1
```

脚本从OpenCV官方模型库下载并校验YuNet与SFace权重。将输出的绝对路径分别写入
`FACE_DETECTION_MODEL_PATH`和`FACE_RECOGNITION_MODEL_PATH`，再为
`train-learning-service`设置`FACE_ENABLED=true`。模型权重已被Git忽略，生产环境应只读挂载；
其余阈值和图片限制见[`train-face-adapter`说明](../../backend/train-face-adapter/README.md)。

## 平台超管初始化

首次启动`train-admin-service`时设置：

```text
APP_BOOTSTRAP_ENABLED=true
APP_BOOTSTRAP_ADMIN_USERNAME=<唯一用户名>
APP_BOOTSTRAP_ADMIN_PASSWORD=<满足密码策略的强密码>
APP_BOOTSTRAP_ADMIN_DISPLAY_NAME=<显示名称>
```

初始化逻辑是幂等的，不会覆盖已有账号。首次成功后建议将
`APP_BOOTSTRAP_ENABLED`改回`false`。不得把真实密码写入Git配置。

## 本地启动顺序

1. 启动MySQL、Redis和Nacos；
2. 启动`train-admin-service`（HTTP 8091、Dubbo 20891），Flyway自动迁移管理库；
3. 启动`train-training-service`（HTTP 8092、Dubbo 20892），Flyway自动迁移培训库；
4. 启动`train-web-api`（8081）；
5. 启动`train-gateway`（8080）；
6. 在`frontend`执行`pnpm dev`，访问`http://localhost:5173`。

三个Java应用必须使用一致的`JWT_ISSUER`和Redis配置。Admin Service与Web API
通过Nacos中的Dubbo注册发现，默认地址为`127.0.0.1:8848`。

## 验证命令

```powershell
mvn -f backend\pom.xml clean verify
pnpm --dir frontend install --frozen-lockfile
pnpm --dir frontend lint
pnpm --dir frontend typecheck
pnpm --dir frontend test
pnpm --dir frontend build
```

本机没有Docker时只能完成代码、配置和构建验证，MySQL/Redis/Nacos联调需在Docker
可用环境或CI中执行。真实OSS联调默认跳过；准备专用测试Bucket及密钥并设置
`OSS_INTEGRATION_ENABLED=true`后，执行以下测试验证分片上传、合并、Range读取和删除，
再进行浏览器分片上传验收：

```powershell
mvn -f backend\pom.xml -pl train-training-service -am `
  -Dtest=AliyunOssIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```
