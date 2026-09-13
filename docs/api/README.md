# API与协议

学习档案、学时监管、首页与车辆接口见[接口说明](records-and-vehicles.md)及[OpenAPI 3.0文档](extension.openapi.json)。2026-09-13按工作区（基准`374d762`及未提交改动）静态核对，本次未执行测试或更新Word论文。后续按[变更总索引](../changes/README.md)和[首次回溯记录](../changes/2026-09-13-since-09-07.md)维护增量依据，历史测试不自动覆盖当前实现。OpenAPI仍是原扩展接口快照，本次新增字段和接口以当前代码及本文为准，不能假定该JSON已同步。

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
| GET | `/api/auth/profile` | 本人资料，包括只读组织、角色及车辆信息 |
| PUT | `/api/auth/profile` | 更新本人displayName和phone |

首次登录或密码重置后，`mustChangePassword`为`true`。此时除当前会话、修改密码
和退出外的受保护接口均不可使用。

## 管理接口

| 资源 | 接口 |
|---|---|
| 组织 | `GET/POST /api/admin/enterprises`、`PUT /{id}`、`PATCH /{id}/status`、`GET /{id}/administrators`、`PUT /{id}/administrators/{userId}/password` |
| 地址 | `GET /api/admin/addresses/children`、`POST /api/admin/addresses`、`PUT /api/admin/addresses/{id}` |
| 部门 | `GET /api/admin/orgs/tree`、`POST /api/admin/orgs`、`PUT/DELETE /api/admin/orgs/{id}` |
| 用户 | `GET/POST /api/admin/users`、`GET/PUT /{id}`、`PATCH /{id}/status`、`PUT /{id}/password`、`PUT /{id}/roles` |
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

## 培训计划与学员任务接口

| 资源 | 接口 |
|---|---|
| 培训计划 | `GET/POST /api/training/plans`、`GET/PUT/DELETE /api/training/plans/{id}` |
| 发布与取消 | `POST /api/training/plans/{id}/publish`、`POST /api/training/plans/{id}/cancel` |
| 计划候选项 | `GET /api/training/plans/course-candidates`、`GET /api/training/plans/participant-candidates` |
| 学员任务 | `GET /api/training/student/plans`、`GET /api/training/student/plans/{id}` |
| 学员任务汇总进度 | `GET /api/training/student/plans/progress`，参数planIds |

汇总进度要求`student:plan:view`权限，planIds最多100个且必须为正数；仅返回本人非草稿、未删除计划的planId、requiredDurationMillis、effectiveDurationMillis。要求学时来自发布快照，缺少学习记录按零汇总，不初始化进度；与可能调用ensureProgress的学习详情进度接口分别使用。

计划使用独立的`admin:plan:view/create/update/publish/cancel`权限。课程和学员候选接口
只要求计划创建或编辑权限，不依赖`admin:course:view`或`admin:user:view`。计划发布会在
同一培训库事务中重新校验已启用课程、有效学员和起止时间，并冻结课程规则、课件清单
及学员展示信息；发布后不可编辑。学员任务接口只按当前登录用户和`enterprise_id`查询，
未分配用户及其他组织用户不可见。要求考试的计划还会冻结已启用试卷、考试时长和及格分；
学习完成且考试及格后，任务才进入培训完成状态。计划新增和编辑将起止时间按自然日归一为00:00:00和23:59:59；至少存在一名有效参训人员且全部结业时可提前进入FINISHED。完成学员在原周期内仍可回看，到期、未开始或取消计划禁止播放。人员创建/编辑的vehicleId绑定规则见[档案与车辆接口](records-and-vehicles.md)。

## 人脸登记与学习抽验接口

| 资源 | 接口 |
|---|---|
| 人脸登记照 | `GET/DELETE /api/admin/users/{userId}/face-reference`、`POST /upload-sessions`、`POST /upload-sessions/{sessionId}/complete`、`GET /preview-url` |
| 学习抽验 | `GET /api/learning/sessions/{sessionId}/face-check`、`POST /api/learning/face-checks/{taskId}/submissions` |

登记照和抽验照片均为私有图片。登记照采用短期预签名地址直传，抽验照片采用受限
`multipart/form-data`提交；照片Base64和原图内容不进入WebSocket、业务响应或日志。
抽验任务按计划规则在有效学习过程中触发，待处理时暂停有效学时，支持失败重试和超时终态。

签到签退核验为`GET/POST /api/learning/sessions/{sessionId}/attendance-face`：GET返回是否要求核验，POST以multipart提交action、clientInstanceId、photo，要求student:learning:study权限。开启人脸核验时页面通过摄像头采集，图片接口本身不能证明采集来源或活体；服务端核对动作、会话状态和客户端，核验成功后保存绑定动作与下一事件序号的60秒凭据，对应SIGN_IN或SIGN_OUT受理时再次校验，序号推进后不能复用。照片支持JPEG、PNG、WebP且最大5MB。正式事件受理后关联签到/签退照片，抽验提交关联照片，档案提供受控短期预览。

登记照可由本人或有对应管理权限的本企业管理员维护，平台管理员可维护本人登记照。独立抽验查询GET /api/learning/sessions/{sessionId}/face-check仍只返回待处理任务；会话查询在TERMINATED时返回最近FAILED/TIMED_OUT抽验，其他状态返回待处理任务。控制器“最近完成”注释未与当前实现一致，调用方应依据服务逻辑区分两个入口。

## 考试、判分与培训统计接口

| 资源 | 接口 |
|---|---|
| 题库 | `GET/POST /api/training/exam/questions`、`GET/PUT/DELETE /api/training/exam/questions/{id}`、`PATCH /{id}/status` |
| 试卷 | `GET/POST /api/training/exam/papers`、`GET/PUT/DELETE /api/training/exam/papers/{id}`、`POST /{id}/enable`、`GET /options` |
| 学员考试 | `POST /api/exams/plans/{planId}/records`、`GET /api/exams/records/{recordId}`、`PUT /answers`、`POST /submit` |
| 培训统计 | `GET /api/training/statistics/overview`、`GET /plans`、`GET /participants` |

题库支持单选题和判断题；试卷支持手工选题与随机补齐，并在启用时固化题目快照。
每名学员在一个培训任务下只有一条考试记录，答案可增量保存，截止时自动交卷，提交后
自动判分；重复交卷返回既有成绩，不再次判分。新开考及继续进行中的考试要求学习状态COMPLETED，已有结束考试记录仍可读取；培训结业必须同时满足学习完成和考试通过。统计接口按当前企业汇总计划、参训、学习、考试、完成率和
服务端确认的有效学时，并提供计划及学员分页明细。

## 视频学习与有效学时接口

| 资源 | 接口 |
|---|---|
| 学习进度 | `GET /api/learning/plans/{planId}/progress`、`GET /api/learning/plans/{planId}/courses/{planCourseId}` |
| 学习会话 | `POST /api/learning/sessions`、`GET /api/learning/sessions/active`、`GET /api/learning/sessions/{id}` |
| 学习事件 | `POST /api/learning/sessions/{id}/events`、`POST /api/learning/sessions/{id}/terminate` |
| 学员播放签名 | `GET /api/learning/sessions/{id}/coursewares/{snapshotId}/play-url` |

全部接口要求`student:learning:study`，并按当前用户和组织隔离。事件仅接受`SIGN_IN`、
`PLAY`、`PROGRESS`、`PAUSE`和`SIGN_OUT`；同一请求ID幂等、序号必须严格递增。服务端按
接收时间与确认位置计算有效学时，课程内课件严格顺序。播放签名先校验活动学习会话，
再由培训服务校验任务、计划有效期和课件快照，响应不暴露Bucket、ObjectKey或密钥。

全部课件播放完成但有效学时不足时，页面提供“从头补学当前课件”。对已完成课件发送
`PLAY`且`videoPositionMillis=0`，服务端重置该课件当前确认位置，保留累计学时、最高
确认位置和完成标记；PLAY本身不计时，后续真实播放仍使用同一计时规则。未完成课件
不适用此重置语义，禁止拖动及顺序解锁规则保持有效。

学习Outbox使用publisher confirm与mandatory return共同判断投递结果；ACK伴随无路由
退回时保留重试，不能标记SENT。消费端仍按事件ID去重，较晚到达的STARTED事件不得
使已完成任务退回学习中。

## WebSocket实时学习协议

同源端点为`/ws/learning`。Gateway完成Cookie JWT和Redis登录版本检查后，实时服务会再次
精确校验`Origin`、Access Token签名、登录版本、企业归属、`student:learning:study`
权限及强制改密状态。连接建立后必须依次发送`BIND_SESSION`和`SYNC_STATE`。

统一信封字段为`type`、`requestId`、`studySessionId`、`seq`、`sentAt`和`payload`；业务ID
使用字符串，`seq`和`lastSequence`使用JSON整数，`sentAt`使用ISO时间且不参与有效学时计算。客户端消息为
`BIND_SESSION`、`HEARTBEAT`、`SYNC_STATE`、`SIGN_IN`、`PLAY`、`PROGRESS`、`PAUSE`和
`SIGN_OUT`；服务端消息为`ACK`、`STATE_SYNC`、`PROGRESS_CONFIRMED`、`PONG`、`ERROR`
、`SESSION_REPLACED`、`FACE_CHECK_REQUIRED`和`FACE_CHECK_RESULT`。

`ERROR.payload`固定包含`code`、`message`、`retryable`和`resyncRequired`。学习RPC成功后
先返回`ACK`，再以`PROGRESS_CONFIRMED`返回最终状态与有效学时。断线重发必须复用原
`requestId`和`seq`；`L3005`要求先同步状态，过期进度及签到/播放不自动重放。实时通道
不可用时播放器暂停并重连，不自动改走REST学习事件接口。单条消息上限16KB，心跳20秒、
连接超时60秒；首次会话绑定总等待上限30秒，重连不延期，超时释放页面加载并关闭连接。`4401`表示刷新HTTP会话后重连，`4403`表示登录版本或学习权限已失效，
`4409`表示连接已被其他页面接管。

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

`train-admin-api`提供认证、根组织、部门、用户、角色权限和计划学员目录服务；
`train-training-api`提供课程、对象存储、培训计划、考试、统计及当前学员任务服务与独立DTO；
`train-learning-api`提供会话查询、`bindSession`、学习事件、抽验、播放授权和有效学时统计。
所有RPC
返回明确泛型的`Result<T>`；无数据响应使用`Result<?>`，数据库实体不跨模块暴露。

接口定义以代码为准。禁止在文档、响应体、日志或前端存储中记录真实令牌、账号
密码和私钥。
