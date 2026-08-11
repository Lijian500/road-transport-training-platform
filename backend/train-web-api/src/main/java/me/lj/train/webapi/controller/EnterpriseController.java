package me.lj.train.webapi.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import me.lj.train.api.admin.AdminModels.ChangeStatusCommand;
import me.lj.train.api.admin.AdminModels.CreateEnterpriseCommand;
import me.lj.train.api.admin.AdminModels.EnterpriseAdministratorView;
import me.lj.train.api.admin.AdminModels.EnterpriseQuery;
import me.lj.train.api.admin.AdminModels.EnterpriseView;
import me.lj.train.api.admin.AdminModels.ResetEnterpriseAdministratorPasswordCommand;
import me.lj.train.api.admin.AdminModels.UpdateEnterpriseCommand;
import me.lj.train.api.admin.EnterpriseService;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.Result;
import me.lj.train.webapi.security.RequirePermission;
import me.lj.train.webapi.support.RpcResultSupport;
import org.apache.dubbo.config.annotation.DubboReference;
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

/**
 * 平台企业管理REST接口。
 */
@RestController
@RequestMapping("/api/admin/enterprises")
public class EnterpriseController {

    @DubboReference(check = false, timeout = 5000, retries = 0)
    private EnterpriseService enterpriseService;

    @GetMapping
    @RequirePermission("admin:enterprise:view")
    public Result<PageResult<EnterpriseView>> page(
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.ok(RpcResultSupport.unwrap(enterpriseService.page(
                new EnterpriseQuery(pageNumber, pageSize, keyword, status))));
    }

    @PostMapping
    @RequirePermission("admin:enterprise:create")
    public Result<EnterpriseView> create(@Valid @RequestBody CreateEnterpriseRequest request) {
        CreateEnterpriseCommand command = new CreateEnterpriseCommand(
                request.code(),
                request.name(),
                request.contactName(),
                request.contactPhone(),
                request.address(),
                request.adminUsername(),
                request.adminDisplayName(),
                request.adminPhone(),
                request.temporaryPassword());
        return Result.ok(RpcResultSupport.unwrap(enterpriseService.create(command)));
    }

    @PutMapping("/{id}")
    @RequirePermission("admin:enterprise:update")
    public Result<EnterpriseView> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEnterpriseRequest request) {
        return Result.ok(RpcResultSupport.unwrap(enterpriseService.update(
                new UpdateEnterpriseCommand(
                        id, request.name(), request.contactName(), request.contactPhone(), request.address()))));
    }

    @PatchMapping("/{id}/status")
    @RequirePermission("admin:enterprise:status")
    public Result<?> changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        RpcResultSupport.ensureSuccess(enterpriseService.changeStatus(new ChangeStatusCommand(id, request.status())));
        return Result.ok();
    }

    @GetMapping("/{id}/administrators")
    @RequirePermission("admin:enterprise:view")
    public Result<List<EnterpriseAdministratorView>> listAdministrators(@PathVariable Long id) {
        return Result.ok(RpcResultSupport.unwrap(enterpriseService.listAdministrators(id)));
    }

    @PutMapping("/{id}/administrators/{userId}/password")
    @RequirePermission("admin:enterprise:update")
    public Result<?> resetAdministratorPassword(
            @PathVariable Long id,
            @PathVariable Long userId,
            @Valid @RequestBody ResetPasswordRequest request) {
        RpcResultSupport.ensureSuccess(enterpriseService.resetAdministratorPassword(
                new ResetEnterpriseAdministratorPasswordCommand(id, userId, request.getTemporaryPassword())));
        return Result.ok();
    }

    public record CreateEnterpriseRequest(
            @NotBlank(message = "企业编码不能为空") String code,
            @NotBlank(message = "企业名称不能为空") String name,
            String contactName,
            String contactPhone,
            String address,
            @NotBlank(message = "管理员用户名不能为空") String adminUsername,
            @NotBlank(message = "管理员姓名不能为空") String adminDisplayName,
            String adminPhone,
            @NotBlank(message = "临时密码不能为空") String temporaryPassword) {
    }

    public record UpdateEnterpriseRequest(
            @NotBlank(message = "企业名称不能为空") String name,
            String contactName,
            String contactPhone,
            String address) {
    }

    public record StatusRequest(@NotBlank(message = "状态不能为空") String status) {
    }

    public static final class ResetPasswordRequest {

        @NotBlank(message = "临时密码不能为空")
        private String temporaryPassword;

        public String getTemporaryPassword() {
            return temporaryPassword;
        }

        public void setTemporaryPassword(String temporaryPassword) {
            this.temporaryPassword = temporaryPassword;
        }
    }
}
