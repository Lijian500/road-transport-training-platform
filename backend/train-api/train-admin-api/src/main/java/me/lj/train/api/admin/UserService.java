package me.lj.train.api.admin;

import me.lj.train.api.admin.AdminModels.AssignRolesCommand;
import me.lj.train.api.admin.AdminModels.ChangeStatusCommand;
import me.lj.train.api.admin.AdminModels.CreateUserCommand;
import me.lj.train.api.admin.AdminModels.ResetPasswordCommand;
import me.lj.train.api.admin.AdminModels.UpdateUserCommand;
import me.lj.train.api.admin.AdminModels.UserQuery;
import me.lj.train.api.admin.AdminModels.UserView;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.Result;

/**
 * 组织用户管理RPC接口。
 */
public interface UserService {

    Result<PageResult<UserView>> page(UserQuery query);

    Result<UserView> create(CreateUserCommand command);

    Result<UserView> update(UpdateUserCommand command);

    Result<?> changeStatus(ChangeStatusCommand command);

    Result<?> resetPassword(ResetPasswordCommand command);

    Result<?> assignRoles(AssignRolesCommand command);
}
