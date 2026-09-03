package me.lj.train.webapi.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import me.lj.train.api.training.ExamModels.CreateQuestionCommand;
import me.lj.train.api.training.ExamModels.QuestionQuery;
import me.lj.train.api.training.ExamModels.QuestionView;
import me.lj.train.api.training.ExamModels.UpdateQuestionCommand;
import me.lj.train.api.training.ExamQuestionService;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.Result;
import me.lj.train.webapi.security.RequirePermission;
import me.lj.train.webapi.support.RpcResultSupport;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 企业单选题、判断题题库管理接口。 */
@RestController
@RequestMapping("/api/training/exam/questions")
public class ExamQuestionController {

    private static final String VIEW_PERMISSION = "admin:exam:view";
    private static final String MANAGE_PERMISSION = "admin:exam:manage";

    @DubboReference(check = false, timeout = 10000, retries = 0)
    private ExamQuestionService questionService;

    /** 分页查询本企业题目。 */
    @GetMapping
    @RequirePermission(VIEW_PERMISSION)
    public Result<PageResult<QuestionView>> page(
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) String status) {
        return Result.ok(RpcResultSupport.unwrap(questionService.page(
                new QuestionQuery(pageNumber, pageSize, keyword, questionType, status))));
    }

    /** 创建一道客观题。 */
    @PostMapping
    @RequirePermission(MANAGE_PERMISSION)
    public Result<QuestionView> create(@Valid @RequestBody QuestionRequest request) {
        return Result.ok(RpcResultSupport.unwrap(questionService.create(
                new CreateQuestionCommand(request.questionType(), request.content(),
                        request.options(), request.correctAnswer(), request.analysis()))));
    }

    /** 查询题目详情。 */
    @GetMapping("/{id}")
    @RequirePermission(VIEW_PERMISSION)
    public Result<QuestionView> get(@PathVariable Long id) {
        return Result.ok(RpcResultSupport.unwrap(questionService.get(id)));
    }

    /** 编辑尚可维护的题目。 */
    @PutMapping("/{id}")
    @RequirePermission(MANAGE_PERMISSION)
    public Result<QuestionView> update(
            @PathVariable Long id,
            @Valid @RequestBody QuestionRequest request) {
        return Result.ok(RpcResultSupport.unwrap(questionService.update(
                new UpdateQuestionCommand(id, request.questionType(), request.content(),
                        request.options(), request.correctAnswer(), request.analysis()))));
    }

    /** 启用或停用题目。 */
    @PatchMapping("/{id}/status")
    @RequirePermission(MANAGE_PERMISSION)
    public Result<QuestionView> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusRequest request) {
        return Result.ok(RpcResultSupport.unwrap(
                questionService.changeStatus(id, request.status())));
    }

    /** 软删除不再使用的题目。 */
    @DeleteMapping("/{id}")
    @RequirePermission(MANAGE_PERMISSION)
    public Result<?> delete(@PathVariable Long id) {
        RpcResultSupport.ensureSuccess(questionService.delete(id));
        return Result.ok();
    }

    public record QuestionRequest(
            @NotBlank(message = "题型不能为空") String questionType,
            @NotBlank(message = "题干不能为空")
            @Size(max = 1000, message = "题干不能超过1000个字符") String content,
            @NotNull(message = "题目选项不能为空") List<String> options,
            @NotBlank(message = "正确答案不能为空") String correctAnswer,
            @Size(max = 1000, message = "答案解析不能超过1000个字符") String analysis) {
    }

    public record StatusRequest(@NotBlank(message = "状态不能为空") String status) {
    }
}
