package me.lj.train.webapi.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import me.lj.train.api.admin.AdminModels.AssignRolesCommand;
import me.lj.train.api.admin.AdminModels.ChangeStatusCommand;
import me.lj.train.api.admin.AdminModels.CreateUserCommand;
import me.lj.train.api.admin.AdminModels.ResetPasswordCommand;
import me.lj.train.api.admin.AdminModels.UpdateUserCommand;
import me.lj.train.api.admin.AdminModels.UserQuery;
import me.lj.train.api.admin.AdminModels.UserView;
import me.lj.train.api.admin.UserService;
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
 * 企业用户管理REST接口。
 */
@RestController
@RequestMapping("/api/admin/users")
public class UserController {

    @DubboReference(check = false, timeout = 5000, retries = 0)
    private UserService userService;

    @GetMapping
    @RequirePermission("admin:user:view")
    public Result<PageResult<UserView>> page(
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) String status) {
        return Result.ok(RpcResultSupport.unwrap(userService.page(
                new UserQuery(pageNumber, pageSize, keyword, orgId, status))));
    }

    @PostMapping
    @RequirePermission("admin:user:create")
    public Result<UserView> create(@Valid @RequestBody CreateUserRequest request) {
        return Result.ok(RpcResultSupport.unwrap(userService.create(new CreateUserCommand(
                request.username(),
                request.displayName(),
                request.phone(),
                request.orgId(),
                request.temporaryPassword(),
                request.roleIds()))));
    }

    @PutMapping("/{id}")
    @RequirePermission("admin:user:update")
    public Result<UserView> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return Result.ok(RpcResultSupport.unwrap(userService.update(new UpdateUserCommand(
                id, request.displayName(), request.phone(), request.orgId()))));
    }

    @PatchMapping("/{id}/status")
    @RequirePermission("admin:user:status")
    public Result<?> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody EnterpriseController.StatusRequest request) {
        RpcResultSupport.ensureSuccess(userService.changeStatus(new ChangeStatusCommand(id, request.status())));
        return Result.ok();
    }

    @PutMapping("/{id}/password")
    @RequirePermission("admin:user:reset-password")
    public Result<?> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody ResetPasswordRequest request) {
        RpcResultSupport.ensureSuccess(userService.resetPassword(
                new ResetPasswordCommand(id, request.temporaryPassword())));
        return Result.ok();
    }

    @PutMapping("/{id}/roles")
    @RequirePermission("admin:user:assign-role")
    public Result<?> assignRoles(@PathVariable Long id, @RequestBody AssignRolesRequest request) {
        RpcResultSupport.ensureSuccess(userService.assignRoles(new AssignRolesCommand(id, request.roleIds())));
        return Result.ok();
    }

    public record CreateUserRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "姓名不能为空") String displayName,
            String phone,
            Long orgId,
            @NotBlank(message = "临时密码不能为空") String temporaryPassword,
            List<Long> roleIds) {
    }

    public record UpdateUserRequest(
            @NotBlank(message = "姓名不能为空") String displayName,
            String phone,
            Long orgId) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "临时密码不能为空") String temporaryPassword) {
    }

    public record AssignRolesRequest(List<Long> roleIds) {
    }
}
