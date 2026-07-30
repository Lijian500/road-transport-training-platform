package me.lj.train.webapi.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import me.lj.train.api.admin.AdminModels.AssignPermissionsCommand;
import me.lj.train.api.admin.AdminModels.ChangeStatusCommand;
import me.lj.train.api.admin.AdminModels.CreateRoleCommand;
import me.lj.train.api.admin.AdminModels.PermissionView;
import me.lj.train.api.admin.AdminModels.RoleQuery;
import me.lj.train.api.admin.AdminModels.RoleView;
import me.lj.train.api.admin.AdminModels.UpdateRoleCommand;
import me.lj.train.api.admin.RolePermissionService;
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

/**
 * 角色及固定权限目录REST接口。
 */
@RestController
@RequestMapping("/api/admin")
public class RolePermissionController {

    @DubboReference(check = false, timeout = 5000, retries = 0)
    private RolePermissionService rolePermissionService;

    @GetMapping("/roles")
    @RequirePermission("admin:role:view")
    public Result<PageResult<RoleView>> page(
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.ok(RpcResultSupport.unwrap(rolePermissionService.page(
                new RoleQuery(pageNumber, pageSize, keyword, status))));
    }

    @GetMapping("/roles/options")
    @RequirePermission("admin:role:view")
    public Result<List<RoleView>> options() {
        return Result.ok(RpcResultSupport.unwrap(rolePermissionService.listEnabled()));
    }

    @PostMapping("/roles")
    @RequirePermission("admin:role:create")
    public Result<RoleView> create(@Valid @RequestBody RoleRequest request) {
        return Result.ok(RpcResultSupport.unwrap(rolePermissionService.create(
                new CreateRoleCommand(request.code(), request.name(), request.description()))));
    }

    @PutMapping("/roles/{id}")
    @RequirePermission("admin:role:update")
    public Result<RoleView> update(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        return Result.ok(RpcResultSupport.unwrap(rolePermissionService.update(
                new UpdateRoleCommand(id, request.name(), request.description()))));
    }

    @PatchMapping("/roles/{id}/status")
    @RequirePermission("admin:role:status")
    public Result<?> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody EnterpriseController.StatusRequest request) {
        RpcResultSupport.ensureSuccess(rolePermissionService.changeStatus(
                new ChangeStatusCommand(id, request.status())));
        return Result.ok();
    }

    @DeleteMapping("/roles/{id}")
    @RequirePermission("admin:role:delete")
    public Result<?> delete(@PathVariable Long id) {
        RpcResultSupport.ensureSuccess(rolePermissionService.delete(id));
        return Result.ok();
    }

    @PutMapping("/roles/{id}/permissions")
    @RequirePermission("admin:role:assign-permission")
    public Result<?> assignPermissions(
            @PathVariable Long id,
            @RequestBody AssignPermissionsRequest request) {
        RpcResultSupport.ensureSuccess(rolePermissionService.assignPermissions(
                new AssignPermissionsCommand(id, request.permissionIds())));
        return Result.ok();
    }

    @GetMapping("/permissions/tree")
    @RequirePermission("admin:permission:view")
    public Result<List<PermissionView>> permissionTree() {
        return Result.ok(RpcResultSupport.unwrap(rolePermissionService.permissionTree()));
    }

    public record RoleRequest(
            @NotBlank(message = "角色编码不能为空") String code,
            @NotBlank(message = "角色名称不能为空") String name,
            String description) {
    }

    public record UpdateRoleRequest(
            @NotBlank(message = "角色名称不能为空") String name,
            String description) {
    }

    public record AssignPermissionsRequest(List<Long> permissionIds) {
    }
}
