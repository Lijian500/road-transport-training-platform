# 学习档案、监管与车辆接口

所有路径经过网关，以 `/api` 开头。成功为 `{"code":"SUCCESS","message":"成功","data":...}`，失败沿用项目业务码。Long ID序列化为字符串，时间为服务端本地ISO日期时间，学时单位为毫秒。分页参数 `pageNumber=1`、`pageSize=10`，上限由统一PageRequest限制为100。

## 学员与统计管理员

| 方法与路径 | 参数/返回 | 权限 |
| --- | --- | --- |
| GET `/training/student/overview` | totalCount、toStudyCount、toExamCount、completedCount、recentTasks（最多5条） | student:plan:view |
| GET `/training/student/records` | 分页、keyword、completionStatus、fromDate、toDate、activity；返回PageResult<RecordItem> | student:plan:view |
| GET `/training/student/records/{taskId}` | RecordDetail：training + courses | student:plan:view，且为本人 |
| GET `/training/statistics/participants/{taskId}/record` | 同上，本企业参训任务 | admin:statistics:view |
| GET `/learning/records/student/sessions` | taskId必填、分页、status、fromTime、toTime | student:plan:view，且为本人 |
| GET `/learning/records/admin/sessions` | 同上，本企业范围 | admin:statistics:view |
| GET `/learning/records/{audience}/sessions/{id}/events` | 分页，返回已受理学习事件 | audience仅student或admin，分别校验本人/本企业 |
| GET `/learning/records/{audience}/sessions/{id}/face-checks` | 分页，返回抽验任务及每次提交结果 | 同上 |

`RecordItem`包含training和effectiveDurationMillis。training包含taskId、planId、userId、displayName、planName、planStatus、startAt、endAt、studyStatus、examStatus、completionStatus、examScore、examPassed、completedAt、requiredDurationMillis。未开始考试时成绩为空。详情courses按发布快照顺序返回规定学时、有效学时、完成状态及完成时间，未产生进度的课程显示零学时。

completionStatus允许COMPLETED、NOT_COMPLETED；activity允许TO_STUDY、TO_EXAM、COMPLETED。档案日期按**计划开始日期**筛选，fromDate/toDate均包含当天。待学习、待考试仅计入当前有效期内任务；待考试包括未开始与进行中考试，沿用现有允许先考试的规则。首页全部培训、完成数量包含历史计划。所有档案查询只读，不初始化学习进度，不开考，不刷新计划状态。

事件包含requestId、sequence、eventType、fromStatus、toStatus、上报/确认位置、本次creditedDurationMillis和serverTime。**仅已受理事件入表**，不可据此推断拒绝请求数。抽验列表以face_check_task为主，零次提交的超时任务仍返回；attempts包含结果、原因、相似度及耗时，不返回照片、对象地址、模板或内部响应载荷。会话ID每次单独检查归属。

## 企业车辆

| 方法与路径 | 参数/返回 | 权限 |
| --- | --- | --- |
| GET `/admin/vehicles` | 分页、keyword（车牌）、orgId、status；PageResult<VehicleView> | admin:vehicle:view |
| GET `/admin/vehicles/departments` | 可选的本企业启用部门 | 车辆查看/新增/修改权限之一 |
| POST `/admin/vehicles` | plateNumber、vehicleType、orgId可空、remark可空；VehicleView | admin:vehicle:create |
| PUT `/admin/vehicles/{id}` | 同上；VehicleView | admin:vehicle:update |
| PUT `/admin/vehicles/{id}/status` | status=ENABLED或DISABLED | admin:vehicle:status |

车牌去首尾空白并转大写，长度3–16且不含空白；车辆类型必填且最多64字，备注最多255字。默认启用，无删除接口。同企业车牌唯一，由数据库唯一索引处理并发冲突；不同企业可以登记相同车牌。绑定部门必须属于本企业且为启用部门；车辆引用的部门不能删除。查询和更新从登录上下文取企业，不接受客户端enterpriseId。

## RPC

新增TrainingRecordService、LearningRecordService、VehicleService，所有方法返回明确泛型的Result。Web API聚合培训与学习数据，各服务只访问自己的数据库。新增权限及车辆表由管理库V12迁移初始化；原企业管理员角色自动补齐车辆权限，新企业初始化沿用企业权限全集。

V13仅为没有任何权限的、未删除的企业内置STUDENT角色补齐student前缀权限。已有授权的角色保持不变。启动后的既有权限缓存初始化器会清理这些内置角色关联用户的授权缓存。
