package me.lj.train.common.core.result;

import java.io.Serializable;

/**
 * 统一错误码契约。
 */
public interface ErrorCode extends Serializable {

    String getCode();

    String getMessage();

    int getHttpStatus();
}
