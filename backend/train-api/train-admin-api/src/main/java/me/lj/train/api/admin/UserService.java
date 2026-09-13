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

    /** 查看当前登录人的基本信息。 */
    Result<UserView> profile();

    /** 仅修改本人姓名和手机号，不接收用户ID或权限字段。 */
    Result<UserView> updateProfile(String displayName, String phone);


    Result<PageResult<UserView>> page(UserQuery query);

    /** 查看本企业人员详情。 */
    Result<UserView> detail(Long id);

    Result<UserView> create(CreateUserCommand command);

    Result<UserView> update(UpdateUserCommand command);

    Result<?> changeStatus(ChangeStatusCommand command);

    Result<?> resetPassword(ResetPasswordCommand command);

    Result<?> assignRoles(AssignRolesCommand command);
}
