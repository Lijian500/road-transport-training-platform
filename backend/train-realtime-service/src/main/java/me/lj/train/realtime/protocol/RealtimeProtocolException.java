package me.lj.train.realtime.protocol;

/** 客户端实时消息格式或状态不符合协议。 */
public class RealtimeProtocolException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RealtimeProtocolException(String message) {
        super(message);
    }
}
