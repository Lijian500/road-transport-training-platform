# 贡献说明

## 基本原则

- 项目使用无BOM UTF-8编码；
- 后端Java包名统一以`me.lj.train`开头；
- 前端和后端分别使用pnpm与Maven管理；
- 优先完成最小必要改动，避免无关重构；
- 业务服务不能访问其他服务的数据表；
- 不向仓库提交密钥、真实人脸照片、真实企业数据或原商业项目代码。

## 分支和提交

建议分支：

```text
main
feature/<name>
fix/<name>
docs/<name>
```

提交信息采用简化Conventional Commits：

```text
feat: 新增学习会话创建能力
fix: 修复重复消息累计学时
docs: 更新实施进度
chore: 初始化项目骨架
```

## 提交前检查

后端：

```powershell
mvn -f backend/pom.xml clean verify
```

前端：

```powershell
pnpm --dir frontend install
pnpm --dir frontend typecheck
pnpm --dir frontend test
pnpm --dir frontend build
```

首次安装成功后必须提交生成的`frontend/pnpm-lock.yaml`；此后CI和本地构建应改用`--frozen-lockfile`确保依赖一致。

每次完成一个阶段性功能时，还需要更新根目录的`IMPLEMENTATION_STATUS.md`。
