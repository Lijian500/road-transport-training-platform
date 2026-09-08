package me.lj.train.webapi.controller;

import me.lj.train.api.learning.LearningRecordModels.*;
import me.lj.train.api.learning.LearningRecordService;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.Result;
import me.lj.train.webapi.security.RequirePermission;
import me.lj.train.webapi.support.RpcResultSupport;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/** 学习历史和管理监管的只读REST入口，角色范围由RPC再次校验。 */
@RestController
@RequestMapping("/api/learning/records")
public class LearningRecordController {
    @DubboReference(check = false, timeout = 10000, retries = 0)
    private LearningRecordService service;

    /** 当前学员的历史会话。 */
    @GetMapping("/student/sessions")
    @RequirePermission("student:plan:view")
    public Result<PageResult<SessionRecordView>> mySessions(@RequestParam Long taskId,
            @RequestParam(defaultValue = "1") int pageNumber, @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toTime) {
        return Result.ok(RpcResultSupport.unwrap(service.mySessions(new SessionQuery(pageNumber, pageSize, taskId, status, fromTime, toTime))));
    }

    /** 本企业参训人员的历史会话。 */
    @GetMapping("/admin/sessions")
    @RequirePermission("admin:statistics:view")
    public Result<PageResult<SessionRecordView>> adminSessions(@RequestParam Long taskId,
            @RequestParam(defaultValue = "1") int pageNumber, @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toTime) {
        return Result.ok(RpcResultSupport.unwrap(service.adminSessions(new SessionQuery(pageNumber, pageSize, taskId, status, fromTime, toTime))));
    }

    /** 本人会话事件分页。 */
    @GetMapping("/student/sessions/{id}/events")
    @RequirePermission("student:plan:view")
    public Result<PageResult<EventRecordView>> myEvents(@PathVariable Long id,
            @RequestParam(defaultValue = "1") int pageNumber, @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(RpcResultSupport.unwrap(service.myEvents(id, pageNumber, pageSize)));
    }

    /** 本企业会话事件分页。 */
    @GetMapping("/admin/sessions/{id}/events")
    @RequirePermission("admin:statistics:view")
    public Result<PageResult<EventRecordView>> adminEvents(@PathVariable Long id,
            @RequestParam(defaultValue = "1") int pageNumber, @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(RpcResultSupport.unwrap(service.adminEvents(id, pageNumber, pageSize)));
    }

    /** 本人抽验任务及提交结果。 */
    @GetMapping("/student/sessions/{id}/face-checks")
    @RequirePermission("student:plan:view")
    public Result<PageResult<FaceRecordView>> myFaceChecks(@PathVariable Long id,
            @RequestParam(defaultValue = "1") int pageNumber, @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(RpcResultSupport.unwrap(service.myFaceChecks(id, pageNumber, pageSize)));
    }

    /** 本企业抽验任务及提交结果。 */
    @GetMapping("/admin/sessions/{id}/face-checks")
    @RequirePermission("admin:statistics:view")
    public Result<PageResult<FaceRecordView>> adminFaceChecks(@PathVariable Long id,
            @RequestParam(defaultValue = "1") int pageNumber, @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(RpcResultSupport.unwrap(service.adminFaceChecks(id, pageNumber, pageSize)));
    }
}
