package me.lj.train.common.core.result;

import java.io.Serializable;

/**
 * REST与RPC统一返回对象。
 *
 * @param <T> 返回数据类型
 */
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final Result<?> SUCCESS = new Result<Object>(
            AppErrorCode.SUCCESS.getCode(), AppErrorCode.SUCCESS.getMessage(), null);

    private String code;
    private String message;
    private T data;

    public Result() {
    }

    public Result(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> ok(T data) {
        return new Result<T>(AppErrorCode.SUCCESS.getCode(), AppErrorCode.SUCCESS.getMessage(), data);
    }

    @SuppressWarnings("unchecked")
    public static <T> Result<T> ok() {
        return (Result<T>) SUCCESS;
    }

    public static <T> Result<T> failed(ErrorCode errorCode) {
        return new Result<T>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> Result<T> failed(ErrorCode errorCode, String message) {
        return new Result<T>(errorCode.getCode(), message, null);
    }

    public static <T> Result<T> failed(String code, String message) {
        return new Result<T>(code, message, null);
    }

    public boolean isSuccess() {
        return AppErrorCode.SUCCESS.getCode().equals(code);
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
