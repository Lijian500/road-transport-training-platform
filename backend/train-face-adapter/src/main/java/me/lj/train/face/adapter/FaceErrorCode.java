package me.lj.train.face.adapter;

import me.lj.train.common.core.result.ErrorCode;

/**
 * 人脸适配模块错误码。
 */
public enum FaceErrorCode implements ErrorCode {

    INVALID_IMAGE("F4001", "图片为空、损坏或格式不受支持", 400),
    IMAGE_TOO_LARGE("F4002", "图片大小或像素数量超过限制", 413),
    MODEL_NOT_READY("F4003", "人脸模型未正确配置", 503),
    INFERENCE_FAILED("F4004", "人脸模型推理失败", 500);

    private final String code;
    private final String message;
    private final int httpStatus;

    FaceErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }
}
