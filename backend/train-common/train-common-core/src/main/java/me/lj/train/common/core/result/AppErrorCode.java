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

    ENTERPRISE_NOT_FOUND("M1001", "组织不存在", 404),
    ORG_NOT_FOUND("M1002", "组织不存在", 404),
    USER_NOT_FOUND("M1003", "用户不存在", 404),
    ROLE_NOT_FOUND("M1004", "角色不存在", 404),
    USERNAME_EXISTS("M1005", "用户名已存在", 409),
    ENTERPRISE_CODE_EXISTS("M1006", "组织编码已存在", 409),
    ROLE_CODE_EXISTS("M1007", "角色编码已存在", 409),
    DATA_SCOPE_VIOLATION("M1008", "不能访问其他组织的数据", 403),
    DATA_IN_USE("M1009", "数据正在使用，不能删除", 409),
    BUILTIN_DATA_READONLY("M1010", "内置数据不能修改或删除", 409),
    LAST_ADMIN_PROTECTED("M1011", "必须至少保留一个启用的组织管理员", 409),
    PASSWORD_INCORRECT("M1012", "原密码不正确", 400),
    PASSWORD_POLICY_INVALID("M1013", "密码需为8至64位，并同时包含字母和数字", 400),
    ORG_NAME_EXISTS("M1014", "同级组织名称已存在", 409),
    ORG_CODE_EXISTS("M1015", "组织编码已存在", 409),

    COURSE_NOT_FOUND("T1001", "课程不存在", 404),
    COURSEWARE_NOT_FOUND("T1002", "课件不存在", 404),
    COURSE_STATE_INVALID("T1003", "当前课程状态不允许该操作", 409),
    COURSE_ENABLE_INVALID("T1004", "课程不满足启用条件", 409),
    UPLOAD_DISABLED("T1101", "对象存储未配置，暂不可上传", 503),
    UPLOAD_SESSION_NOT_FOUND("T1102", "上传会话不存在", 404),
    UPLOAD_SESSION_INVALID("T1103", "上传会话状态无效或已过期", 409),
    STORAGE_OPERATION_FAILED("T1104", "对象存储操作失败", 502),
    STORAGE_OBJECT_INVALID("T1105", "上传文件校验失败", 400),
    UPLOAD_FILE_INVALID("T1106", "上传文件信息不符合要求", 400),

    PLAN_NOT_FOUND("T1201", "培训计划不存在", 404),
    PLAN_STATE_INVALID("T1202", "当前培训计划状态不允许该操作", 409),
    PLAN_PUBLISH_INVALID("T1203", "培训计划不满足发布条件", 409),
    PLAN_PARTICIPANT_INVALID("T1204", "参训学员信息无效", 400),
    STUDENT_TASK_NOT_FOUND("T1205", "培训任务不存在", 404),

    LEARNING_ACCESS_DENIED("L3001", "当前培训任务不允许学习", 403),
    LEARNING_SESSION_NOT_FOUND("L3002", "学习会话不存在", 404),
    LEARNING_SESSION_CONFLICT("L3003", "已有其他活动学习会话", 409),
    LEARNING_SESSION_STATE_INVALID("L3004", "当前学习状态不允许该操作", 409),
    LEARNING_EVENT_SEQUENCE_INVALID("L3005", "学习事件序号无效，请同步服务端状态", 409),
    LEARNING_COURSEWARE_LOCKED("L3006", "请先完成前一个课件", 409),
    LEARNING_POSITION_INVALID("L3007", "视频进度校验失败", 409),
    LEARNING_SESSION_STALE("L3008", "学习会话已失效，请重新进入课程", 409),
    LEARNING_PLAYBACK_UNAVAILABLE("L3009", "视频暂时无法播放", 503);

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
