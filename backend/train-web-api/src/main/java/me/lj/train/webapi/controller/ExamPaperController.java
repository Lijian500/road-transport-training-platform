package me.lj.train.webapi.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import me.lj.train.api.training.ExamModels.CreatePaperCommand;
import me.lj.train.api.training.ExamModels.PaperOptionView;
import me.lj.train.api.training.ExamModels.PaperQuery;
import me.lj.train.api.training.ExamModels.PaperView;
import me.lj.train.api.training.ExamModels.UpdatePaperCommand;
import me.lj.train.api.training.ExamPaperService;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.Result;
import me.lj.train.webapi.security.RequirePermission;
import me.lj.train.webapi.support.RpcResultSupport;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 企业试卷草稿、组卷与启用接口。 */
@RestController
@RequestMapping("/api/training/exam/papers")
public class ExamPaperController {

    private static final String VIEW_PERMISSION = "admin:exam:view";
    private static final String MANAGE_PERMISSION = "admin:exam:manage";

    @DubboReference(check = false, timeout = 10000, retries = 0)
    private ExamPaperService paperService;

    /** 分页查询本企业试卷。 */
    @GetMapping
    @RequirePermission(VIEW_PERMISSION)
    public Result<PageResult<PaperView>> page(
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.ok(RpcResultSupport.unwrap(paperService.page(
                new PaperQuery(pageNumber, pageSize, keyword, status))));
    }

    /** 创建试卷草稿。 */
    @PostMapping
    @RequirePermission(MANAGE_PERMISSION)
    public Result<PaperView> create(@Valid @RequestBody PaperRequest request) {
        return Result.ok(RpcResultSupport.unwrap(paperService.create(
                new CreatePaperCommand(request.name(), request.description(),
                        request.durationMinutes(), request.passScore(), request.questionScore(),
                        request.manualQuestionIds(), request.randomFillCount()))));
    }

    /** 查询试卷及已固化题目。 */
    @GetMapping("/{id}")
    @RequirePermission(VIEW_PERMISSION)
    public Result<PaperView> get(@PathVariable Long id) {
        return Result.ok(RpcResultSupport.unwrap(paperService.get(id)));
    }

    /** 编辑试卷草稿和选题规则。 */
    @PutMapping("/{id}")
    @RequirePermission(MANAGE_PERMISSION)
    public Result<PaperView> update(
            @PathVariable Long id,
            @Valid @RequestBody PaperRequest request) {
        return Result.ok(RpcResultSupport.unwrap(paperService.update(
                new UpdatePaperCommand(id, request.name(), request.description(),
                        request.durationMinutes(), request.passScore(), request.questionScore(),
                        request.manualQuestionIds(), request.randomFillCount()))));
    }

    /** 删除未启用的试卷草稿。 */
    @DeleteMapping("/{id}")
    @RequirePermission(MANAGE_PERMISSION)
    public Result<?> delete(@PathVariable Long id) {
        RpcResultSupport.ensureSuccess(paperService.delete(id));
        return Result.ok();
    }

    /** 将手工题与随机补齐题固化后启用试卷。 */
    @PostMapping("/{id}/enable")
    @RequirePermission(MANAGE_PERMISSION)
    public Result<PaperView> enable(@PathVariable Long id) {
        return Result.ok(RpcResultSupport.unwrap(paperService.enable(id)));
    }

    /** 查询培训计划可关联的已启用试卷。 */
    @GetMapping("/options")
    @RequirePermission({VIEW_PERMISSION, "admin:plan:create", "admin:plan:update"})
    public Result<List<PaperOptionView>> options(
            @RequestParam(required = false) String keyword) {
        return Result.ok(RpcResultSupport.unwrap(paperService.listEnabled(keyword)));
    }

    public record PaperRequest(
            @NotBlank(message = "试卷名称不能为空")
            @Size(max = 128, message = "试卷名称不能超过128个字符") String name,
            @Size(max = 1000, message = "试卷说明不能超过1000个字符") String description,
            @Min(value = 1, message = "考试时长必须大于0")
            @Max(value = 480, message = "考试时长不能超过480分钟") int durationMinutes,
            @Min(value = 1, message = "及格分必须大于0") int passScore,
            @Min(value = 1, message = "每题分值必须大于0") int questionScore,
            @NotNull(message = "手工选题不能为空") List<Long> manualQuestionIds,
            @Min(value = 0, message = "随机补题数不能小于0") int randomFillCount) {
    }
}
