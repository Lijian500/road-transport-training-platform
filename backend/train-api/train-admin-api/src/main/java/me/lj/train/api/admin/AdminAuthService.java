package me.lj.train.api.admin;

import me.lj.train.api.admin.AdminModels.ChangePasswordCommand;
import me.lj.train.api.admin.AdminModels.LoginAccount;
import me.lj.train.api.admin.AdminModels.LoginCommand;
import me.lj.train.common.core.result.Result;

/**
 * 账号认证与授权信息RPC接口。
 */
public interface AdminAuthService {

    Result<LoginAccount> authenticate(LoginCommand command);

    Result<LoginAccount> getAuthorization(Long userId);

    Result<LoginAccount> changePassword(ChangePasswordCommand command);
}
