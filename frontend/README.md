# 前端工程

本目录是可独立运行的Vue 3单页应用，统一承载管理工作台和学员学习中心。

## 环境要求

- Node.js 18.18或更高版本
- pnpm 9或更高版本

## 本地运行

```bash
pnpm install
pnpm dev
```

默认访问地址为<http://localhost:5173>。开发服务器会将`/api`和`/ws`代理到
`http://localhost:8080`，可以通过`VITE_GATEWAY_TARGET`修改目标网关。

## 常用命令

```bash
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

## 当前边界

当前仅初始化工程结构、登录入口、两个工作台占位页面、路由、Pinia、Axios和原生
WebSocket客户端骨架，不包含任何业务功能。服务端始终是身份、权限、学习状态和
有效学时的最终权威。
