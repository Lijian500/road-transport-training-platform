package me.lj.train.common.core.result;

/**
 * 基础、认证及管理模块错误码。
 */
public enum AppErrorCode implements ErrorCode {

    SUCCESS("SUCCESS", "操作成功", 200),
    PARAM_INVALID("S9001", "请求参数不正确", 400),
    RESOURCE_NOT_FOUND("S9002", "请求的资源不存在", 404),
    METHOD_NOT_ALLOWED("S9003", "请求方法不支持", 405),
    SYSTEM_ERROR("S9999", "系统繁忙，请稍后重试", 500),

    UNAUTHORIZED("A0001", "请先登录", 401),
    INVALID_CREDENTIALS("A0002", "用户名或密码错误", 401),
    TOKEN_EXPIRED("A0003", "登录状态已过期", 401),
    REFRESH_TOKEN_INVALID("A0004", "刷新令牌无效或已失效", 401),
    ACCOUNT_DISABLED("A0005", "账号已被禁用", 403),
    FORBIDDEN("A0006", "无权执行该操作", 403),
    PASSWORD_CHANGE_REQUIRED("A0007", "请先修改初始密码", 403),
    CSRF_INVALID("A0008", "请求安全校验失败", 403),

    ENTERPRISE_NOT_FOUND("M1001", "企业不存在", 404),
    ORG_NOT_FOUND("M1002", "组织不存在", 404),
    USER_NOT_FOUND("M1003", "用户不存在", 404),
    ROLE_NOT_FOUND("M1004", "角色不存在", 404),
    USERNAME_EXISTS("M1005", "用户名已存在", 409),
    ENTERPRISE_CODE_EXISTS("M1006", "企业编码已存在", 409),
    ROLE_CODE_EXISTS("M1007", "角色编码已存在", 409),
    DATA_SCOPE_VIOLATION("M1008", "不能访问其他企业的数据", 403),
    DATA_IN_USE("M1009", "数据正在使用，不能删除", 409),
    BUILTIN_DATA_READONLY("M1010", "内置数据不能修改或删除", 409),
    LAST_ADMIN_PROTECTED("M1011", "必须至少保留一个启用的企业管理员", 409),
    PASSWORD_INCORRECT("M1012", "原密码不正确", 400),
    PASSWORD_POLICY_INVALID("M1013", "密码需为8至64位，并同时包含字母和数字", 400),
    ORG_NAME_EXISTS("M1014", "同级组织名称已存在", 409),
    ORG_CODE_EXISTS("M1015", "组织编码已存在", 409);

    private final String code;
    private final String message;
    private final int httpStatus;

    AppErrorCode(String code, String message, int httpStatus) {
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

    public static AppErrorCode fromCode(String code) {
        for (AppErrorCode errorCode : values()) {
            if (errorCode.code.equals(code)) {
                return errorCode;
            }
        }
        return SYSTEM_ERROR;
    }
}
