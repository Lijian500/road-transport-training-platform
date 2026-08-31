package me.lj.train.realtime.security;

import me.lj.train.common.core.result.AppErrorCode;

/** 实时连接认证或授权失败。 */
public class RealtimeAuthException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final AppErrorCode errorCode;

    public RealtimeAuthException(AppErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public AppErrorCode getErrorCode() {
        return errorCode;
    }
}
