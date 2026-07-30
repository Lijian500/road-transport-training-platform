package me.lj.train.common.dubbo;

import me.lj.train.common.security.SecurityConstants;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;

import java.util.Arrays;
import java.util.Collections;

/**
 * 在Dubbo提供者线程中恢复并及时清理登录用户上下文。
 */
@Activate(group = CommonConstants.PROVIDER, order = -10000)
public class UserContextProviderFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String userId = attachment(SecurityConstants.HEADER_USER_ID);
        if (userId == null || userId.isEmpty()) {
            return invoker.invoke(invocation);
        }
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(Long.valueOf(userId));
        String enterpriseId = attachment(SecurityConstants.HEADER_ENTERPRISE_ID);
        if (enterpriseId != null && !enterpriseId.isEmpty()) {
            loginUser.setEnterpriseId(Long.valueOf(enterpriseId));
        }
        loginUser.setSessionId(attachment(SecurityConstants.HEADER_SESSION_ID));
        loginUser.setLoginVersion(parseLong(attachment(SecurityConstants.HEADER_LOGIN_VERSION)));
        loginUser.setPlatformAdmin(Boolean.parseBoolean(attachment(SecurityConstants.HEADER_PLATFORM_ADMIN)));
        loginUser.setMustChangePassword(Boolean.parseBoolean(
                attachment(SecurityConstants.HEADER_MUST_CHANGE_PASSWORD)));
        String permissions = attachment(SecurityConstants.HEADER_PERMISSIONS);
        loginUser.setPermissions(permissions == null || permissions.isEmpty()
                ? Collections.<String>emptyList()
                : Arrays.asList(permissions.split(",")));
        UserContext.set(loginUser);
        try {
            return invoker.invoke(invocation);
        } finally {
            UserContext.clear();
        }
    }

    private String attachment(String key) {
        return RpcContext.getServerAttachment().getAttachment(key);
    }

    private long parseLong(String value) {
        return value == null || value.isEmpty() ? 0L : Long.parseLong(value);
    }
}
