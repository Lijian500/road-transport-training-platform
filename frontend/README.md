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

当前已完成登录、会话恢复、强制改密、权限路由，以及企业、部门、用户、角色和
权限目录页面。HTTP层通过HttpOnly Cookie维持会话，自动携带CSRF令牌，并对并发
401请求执行单次刷新后重试；浏览器存储中不保存访问令牌或刷新令牌。

车辆、课程、培训、学习及考试页面不在本阶段范围内。服务端始终是身份和权限的
最终权威。
