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
| 组织 | `GET/POST /api/admin/enterprises`、`PUT /{id}`、`PATCH /{id}/status`、`GET /{id}/administrators`、`PUT /{id}/administrators/{userId}/password` |
| 地址 | `GET /api/admin/addresses/children`、`POST /api/admin/addresses`、`PUT /api/admin/addresses/{id}` |
| 部门 | `GET /api/admin/orgs/tree`、`POST /api/admin/orgs`、`PUT/DELETE /api/admin/orgs/{id}` |
| 用户 | `GET/POST /api/admin/users`、`PUT /{id}`、`PATCH /{id}/status`、`PUT /{id}/password`、`PUT /{id}/roles` |
| 角色 | `GET/POST /api/admin/roles`、`GET /options`、`PUT/DELETE /{id}`、`PATCH /{id}/status`、`PUT /{id}/permissions` |
| 权限 | `GET /api/admin/permissions/tree` |

## 课程及OSS直传接口

| 资源 | 接口 |
|---|---|
| 课程 | `GET/POST /api/training/courses`、`GET/PUT/DELETE /api/training/courses/{id}`、`PATCH /api/training/courses/{id}/status` |
| 课件 | `PUT/DELETE /api/training/courses/{courseId}/coursewares/{id}`、`PUT /api/training/courses/{courseId}/coursewares/order` |
| 封面 | `DELETE /api/training/courses/{id}/cover`、`GET /api/training/courses/{id}/cover/preview-url` |
| 上传能力 | `GET /api/training/storage/capability` |
| 上传会话 | `POST /api/training/courses/{id}/cover/upload-sessions`、`POST /api/training/courses/{id}/coursewares/upload-sessions`、`POST /api/training/upload-sessions/{id}/part-urls`、`GET /api/training/upload-sessions/{id}/parts`、`POST /api/training/upload-sessions/{id}/complete`、`DELETE /api/training/upload-sessions/{id}` |
| 视频预览 | `GET /api/training/courses/{courseId}/coursewares/{id}/preview-url` |

封面和MP4文件由浏览器使用短期预签名地址直接上传到私有阿里云OSS，不经过Gateway、
Dubbo或Java服务。签名响应只包含URL、HTTP方法、必须请求头和过期时间；AccessKey
不进入响应、数据库或日志。视频完成接口具备幂等语义：OSS已合并但数据库事务未提交时，
客户端可使用同一会话重试完成。OSS未配置时课程CRUD仍可使用，能力接口返回禁用原因。

组织接口沿用`/enterprises`、`EnterpriseService`和`enterprise_id`等技术标识以保持兼容，
根组织业务性质由`organizationNature`区分企业和行管。新增组织必须传入`areaId`；企业
只能选择区县，行管可以选择省、市或区县。列表响应包含`areaPath`用于省市区回显，
地址选项通过`/addresses/children`按父行政代码懒加载。详细范围规则见
[行政区域数据范围规则](../architecture/address-data-scope.md)。

平台超管绕过组织范围但只管理组织资源；组织管理员只能访问本组织数据，并且只能
向角色授予自己拥有的组织级权限。组织和用户只允许启停，内置角色、有关联数据的
部门/角色以及最后一个组织管理员不能删除或失效。空部门和未分配的自定义角色采用
软删除，保留删除人、删除时间及原编码；用户角色、角色权限等当前关系仍采用物理替换。

## Dubbo契约

`train-admin-api`提供认证、根组织、部门、用户和角色权限服务；`train-training-api`
提供课程及对象存储服务与独立DTO。所有RPC
返回明确泛型的`Result<T>`；无数据响应使用`Result<?>`，数据库实体不跨模块暴露。

接口定义以代码为准。禁止在文档、响应体、日志或前端存储中记录真实令牌、账号
密码和私钥。
