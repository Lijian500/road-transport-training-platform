package me.lj.train.webapi.controller;

import me.lj.train.api.learning.LearningRecordModels.CourseRecordView;
import me.lj.train.api.learning.LearningRecordService;
import me.lj.train.api.learning.LearningStatisticsModels.TaskDurationView;
import me.lj.train.api.training.TrainingRecordModels.*;
import me.lj.train.api.training.TrainingRecordService;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.Result;
import me.lj.train.webapi.security.RequirePermission;
import me.lj.train.webapi.support.RpcResultSupport;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 聚合培训快照与学习历史，保持各服务的数据所有权。 */
@RestController
@RequestMapping("/api/training")
public class TrainingRecordController {
    @DubboReference(check = false, timeout = 10000, retries = 0)
    private TrainingRecordService trainingRecordService;
    @DubboReference(check = false, timeout = 10000, retries = 0)
    private LearningRecordService learningRecordService;

    /** 分页获取本人档案，学时RPC只传递当前页任务ID。 */
    @GetMapping("/student/records")
    @RequirePermission("student:plan:view")
    public Result<PageResult<RecordItem>> page(
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String completionStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String activity) {
        PageResult<TrainingRecordView> page = RpcResultSupport.unwrap(trainingRecordService.pageMyRecords(
                new RecordQuery(pageNumber, pageSize, keyword, completionStatus, fromDate, toDate, activity)));
        Map<Long, TaskDurationView> durations = page.getRecords().isEmpty() ? Map.of()
                : RpcResultSupport.unwrap(learningRecordService.myDurations(page.getRecords().stream()
                        .map(TrainingRecordView::taskId).toList())).stream()
                        .collect(Collectors.toMap(TaskDurationView::taskId, Function.identity()));
        return Result.ok(new PageResult<>(page.getRecords().stream().map(row -> new RecordItem(row,
                durations.containsKey(row.taskId()) ? durations.get(row.taskId()).effectiveDurationMillis() : 0L)).toList(),
                page.getTotal(), page.getPageNumber(), page.getPageSize()));
    }

    /** 当前学员首页概览，不调用考试开启接口。 */
    @GetMapping("/student/overview")
    @RequirePermission("student:plan:view")
    public Result<StudentOverviewView> overview() {
        return Result.ok(RpcResultSupport.unwrap(trainingRecordService.myOverview()));
    }

    /** 读取本人档案，未学习课程以零学时展示。 */
    @GetMapping("/student/records/{taskId}")
    @RequirePermission("student:plan:view")
    public Result<RecordDetail> studentDetail(@PathVariable Long taskId) {
        TrainingRecordView training = RpcResultSupport.unwrap(trainingRecordService.getMyRecord(taskId));
        return Result.ok(merge(training, RpcResultSupport.unwrap(learningRecordService.myCourses(taskId))));
    }

    /** 读取本企业参训任务详情，先校验培训任务再查询学习库。 */
    @GetMapping("/statistics/participants/{taskId}/record")
    @RequirePermission("admin:statistics:view")
    public Result<RecordDetail> adminDetail(@PathVariable Long taskId) {
        TrainingRecordView training = RpcResultSupport.unwrap(trainingRecordService.getAdminRecord(taskId));
        return Result.ok(merge(training, RpcResultSupport.unwrap(learningRecordService.adminCourses(taskId))));
    }

    /** 以发布快照为课程目录，避免缺少进度行时丢失课程。 */
    private RecordDetail merge(TrainingRecordView training, List<CourseRecordView> progress) {
        Map<Long, CourseRecordView> byId = progress.stream().collect(Collectors.toMap(
                CourseRecordView::planCourseId, Function.identity()));
        return new RecordDetail(training, training.courses().stream().map(course -> byId.getOrDefault(
                course.planCourseId(), new CourseRecordView(course.planCourseId(), course.courseName(),
                        course.requiredDurationMillis(), 0, "NOT_STARTED", null))).toList());
    }

    /** 档案分页项。 */
    public record RecordItem(TrainingRecordView training, long effectiveDurationMillis) { }
    /** 培训及逐课程学习详情。 */
    public record RecordDetail(TrainingRecordView training, List<CourseRecordView> courses) { }
}
