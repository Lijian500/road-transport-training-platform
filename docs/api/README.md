# API与协议

## 通用约定

- HTTP前缀为`/api`，所有响应使用`Result<T>`；
- 成功响应为`{"code":"SUCCESS","message":"操作成功","data":...}`；
- 失败响应使用统一错误码和与语义匹配的HTTP状态；
- 数据库`BIGINT`标识在REST JSON中序列化为字符串；
- Access Token和Refresh Token只写入HttpOnly Cookie，不出现在响应体；
- 除安全方法外，写请求须携带`X-XSRF-TOKEN`，其值来自`XSRF-TOKEN` Cookie。

分页查询使用`pageNumber`和`pageSize`，分页结果包含`records`、`pageNumber`、
`pageSize`、`total`。用户名全平台唯一，密码为8至64位并同时包含字母和数字。

## 认证接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/auth/csrf` | 初始化CSRF Cookie |
| POST | `/api/auth/login` | 登录并写入两类会话Cookie |
| POST | `/api/auth/refresh` | 原子轮换Refresh Token |
| POST | `/api/auth/logout` | 注销当前会话并清除Cookie |
| GET | `/api/auth/me` | 返回用户、企业、角色、权限、工作台及强制改密状态 |
| POST | `/api/auth/change-password` | 修改当前用户密码并使旧会话失效 |

首次登录或密码重置后，`mustChangePassword`为`true`。此时除当前会话、修改密码
和退出外的受保护接口均不可使用。

## 管理接口

| 资源 | 接口 |
|---|---|
| 企业 | `GET/POST /api/admin/enterprises`、`PUT /{id}`、`PATCH /{id}/status` |
| 部门 | `GET /api/admin/orgs/tree`、`POST /api/admin/orgs`、`PUT/DELETE /api/admin/orgs/{id}` |
| 用户 | `GET/POST /api/admin/users`、`PUT /{id}`、`PATCH /{id}/status`、`PUT /{id}/password`、`PUT /{id}/roles` |
| 角色 | `GET/POST /api/admin/roles`、`GET /options`、`PUT/DELETE /{id}`、`PATCH /{id}/status`、`PUT /{id}/permissions` |
| 权限 | `GET /api/admin/permissions/tree` |

平台超管绕过企业范围但只管理企业资源；企业管理员只能访问本企业数据，并且只能
向角色授予自己拥有的企业级权限。企业和用户只允许启停，内置角色、有关联数据的
部门/角色以及最后一个企业管理员不能删除或失效。

## Dubbo契约

`train-admin-api`提供认证、企业、组织、用户和角色权限服务及独立DTO。所有RPC
返回明确泛型的`Result<T>`；无数据响应使用`Result<?>`，数据库实体不跨模块暴露。

接口定义以代码为准。禁止在文档、响应体、日志或前端存储中记录真实令牌、账号
密码和私钥。
