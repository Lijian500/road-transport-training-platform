package me.lj.train.api.admin;

import me.lj.train.api.admin.AdminModels.AssignPermissionsCommand;
import me.lj.train.api.admin.AdminModels.ChangeStatusCommand;
import me.lj.train.api.admin.AdminModels.CreateRoleCommand;
import me.lj.train.api.admin.AdminModels.PermissionView;
import me.lj.train.api.admin.AdminModels.RoleQuery;
import me.lj.train.api.admin.AdminModels.RoleView;
import me.lj.train.api.admin.AdminModels.UpdateRoleCommand;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.Result;

import java.util.List;

/**
 * 角色与固定权限目录RPC接口。
 */
public interface RolePermissionService {

    Result<PageResult<RoleView>> page(RoleQuery query);

    Result<List<RoleView>> listEnabled();

    Result<RoleView> create(CreateRoleCommand command);

    Result<RoleView> update(UpdateRoleCommand command);

    Result<?> changeStatus(ChangeStatusCommand command);

    Result<?> delete(Long id);

    Result<List<PermissionView>> permissionTree();

    Result<?> assignPermissions(AssignPermissionsCommand command);
}
