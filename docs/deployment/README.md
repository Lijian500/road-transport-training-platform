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
2. 启动`train-admin-service`（HTTP 8091、Dubbo 20891），Flyway自动迁移；
3. 启动`train-web-api`（8081）；
4. 启动`train-gateway`（8080）；
5. 在`frontend`执行`pnpm dev`，访问`http://localhost:5173`。

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
可用环境或CI中执行。
