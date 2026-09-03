package me.lj.train.webapi.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import me.lj.train.api.training.ExamModels.AnswerCommand;
import me.lj.train.api.training.ExamModels.ExamRecordView;
import me.lj.train.api.training.ExamModels.SaveAnswersCommand;
import me.lj.train.api.training.ExamService;
import me.lj.train.common.core.result.Result;
import me.lj.train.webapi.security.RequirePermission;
import me.lj.train.webapi.support.RpcResultSupport;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 学员一次考试、答题保存与交卷接口。 */
@RestController
@RequestMapping("/api/exams")
public class StudentExamController {

    private static final String PERMISSION = "student:exam:take";

    @DubboReference(check = false, timeout = 10000, retries = 0)
    private ExamService examService;

    /** 创建或恢复指定计划的唯一考试记录。 */
    @PostMapping("/plans/{planId}/records")
    @RequirePermission(PERMISSION)
    public Result<ExamRecordView> open(@PathVariable Long planId) {
        return Result.ok(RpcResultSupport.unwrap(examService.open(planId)));
    }

    /** 查询本人考试记录和已保存答案。 */
    @GetMapping("/records/{recordId}")
    @RequirePermission(PERMISSION)
    public Result<ExamRecordView> get(@PathVariable Long recordId) {
        return Result.ok(RpcResultSupport.unwrap(examService.get(recordId)));
    }

    /** 幂等覆盖保存本次变更涉及的题目答案。 */
    @PutMapping("/records/{recordId}/answers")
    @RequirePermission(PERMISSION)
    public Result<ExamRecordView> saveAnswers(
            @PathVariable Long recordId,
            @Valid @RequestBody SaveAnswersRequest request) {
        return Result.ok(RpcResultSupport.unwrap(examService.saveAnswers(
                new SaveAnswersCommand(recordId, request.answers()))));
    }

    /** 提交试卷并自动判分，重复提交返回原终态。 */
    @PostMapping("/records/{recordId}/submit")
    @RequirePermission(PERMISSION)
    public Result<ExamRecordView> submit(@PathVariable Long recordId) {
        return Result.ok(RpcResultSupport.unwrap(examService.submit(recordId)));
    }

    public record SaveAnswersRequest(
            @NotNull(message = "答案列表不能为空") List<AnswerCommand> answers) {
    }
}
