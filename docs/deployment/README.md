# 部署说明

当前阶段仅提供本地基础设施编排，不构建或发布Java业务容器。

## 本地基础设施

1. 安装Docker Desktop并确认`docker compose`可用。
2. 将仓库根目录`.env.example`复制为`.env`。
3. 替换所有`change-me`或`replace-with`示例值。
4. 在仓库根目录执行`./scripts/start-infra.ps1`。
5. 完成开发后执行`./scripts/stop-infra.ps1`。

根目录`.env`是本项目唯一的环境变量模板来源，不得提交真实密码。`stop-infra.ps1 -RemoveVolumes`会删除本项目本地数据卷，脚本会要求输入`DELETE`二次确认。

## 后续补充

实现业务模块后，本目录再增加：

- Java服务和前端的环境变量清单；
- 服务启动顺序、端口及健康检查；
- 前端静态资源挂载和Nginx反向代理配置；
- 测试与生产环境的容器镜像和发布流程；
- 数据库备份恢复、日志采集、监控和回滚方案。
