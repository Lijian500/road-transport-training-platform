package me.lj.train.webapi.support;

import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;

/**
 * 将RPC统一返回转换为BFF业务结果。
 */
public final class RpcResultSupport {

    private RpcResultSupport() {
    }

    public static <T> T unwrap(Result<T> result) {
        if (result == null) {
            throw new BusinessException(AppErrorCode.SYSTEM_ERROR, "远程服务未返回结果");
        }
        if (!result.isSuccess()) {
            throw new BusinessException(AppErrorCode.fromCode(result.getCode()), result.getMessage());
        }
        return result.getData();
    }

    public static void ensureSuccess(Result<?> result) {
        unwrap(result);
    }
}
