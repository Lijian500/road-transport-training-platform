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
import org.apache.dubbo.rpc.RpcContextAttachment;
import org.apache.dubbo.rpc.RpcException;

/**
 * 将BFF中的受信任用户上下文传递给Dubbo提供者。
 */
@Activate(group = CommonConstants.CONSUMER, order = -10000)
public class UserContextConsumerFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        RpcContextAttachment attachment = RpcContext.getClientAttachment();
        clearTrustedAttachments(attachment);
        LoginUser loginUser = UserContext.get();
        if (loginUser != null) {
            attachment
                    .setAttachment(SecurityConstants.HEADER_USER_ID, String.valueOf(loginUser.getUserId()))
                    .setAttachment(SecurityConstants.HEADER_ENTERPRISE_ID,
                            loginUser.getEnterpriseId() == null ? "" : String.valueOf(loginUser.getEnterpriseId()))
                    .setAttachment(SecurityConstants.HEADER_SESSION_ID,
                            loginUser.getSessionId() == null ? "" : loginUser.getSessionId())
                    .setAttachment(SecurityConstants.HEADER_LOGIN_VERSION, String.valueOf(loginUser.getLoginVersion()))
                    .setAttachment(SecurityConstants.HEADER_PLATFORM_ADMIN, String.valueOf(loginUser.isPlatformAdmin()))
                    .setAttachment(SecurityConstants.HEADER_MUST_CHANGE_PASSWORD,
                            String.valueOf(loginUser.isMustChangePassword()))
                    .setAttachment(SecurityConstants.HEADER_PERMISSIONS,
                            String.join(",", loginUser.getPermissions()));
        }
        try {
            return invoker.invoke(invocation);
        } finally {
            // 防止Dubbo调用线程复用时泄露上一请求的用户身份。
            clearTrustedAttachments(attachment);
        }
    }

    private void clearTrustedAttachments(RpcContextAttachment attachment) {
        attachment.removeAttachment(SecurityConstants.HEADER_USER_ID);
        attachment.removeAttachment(SecurityConstants.HEADER_ENTERPRISE_ID);
        attachment.removeAttachment(SecurityConstants.HEADER_SESSION_ID);
        attachment.removeAttachment(SecurityConstants.HEADER_LOGIN_VERSION);
        attachment.removeAttachment(SecurityConstants.HEADER_PLATFORM_ADMIN);
        attachment.removeAttachment(SecurityConstants.HEADER_MUST_CHANGE_PASSWORD);
        attachment.removeAttachment(SecurityConstants.HEADER_PERMISSIONS);
    }
}
