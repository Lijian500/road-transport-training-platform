package me.lj.train.realtime.rpc;

/** 学习RPC返回的标准业务错误。 */
public class RealtimeRpcException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final String code;

    public RealtimeRpcException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
