package me.lj.train.common.security;

/**
 * 认证Cookie、请求头、JWT声明和Redis键常量。
 */
public final class SecurityConstants {

    public static final String ACCESS_TOKEN_COOKIE = "TRAIN_ACCESS_TOKEN";
    public static final String REFRESH_TOKEN_COOKIE = "TRAIN_REFRESH_TOKEN";
    public static final String CSRF_COOKIE = "XSRF-TOKEN";
    public static final String CSRF_HEADER = "X-XSRF-TOKEN";

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_ENTERPRISE_ID = "X-Enterprise-Id";
    public static final String HEADER_SESSION_ID = "X-Session-Id";
    public static final String HEADER_LOGIN_VERSION = "X-Login-Version";
    public static final String HEADER_PLATFORM_ADMIN = "X-Platform-Admin";
    public static final String HEADER_MUST_CHANGE_PASSWORD = "X-Must-Change-Password";
    public static final String HEADER_PERMISSIONS = "X-User-Permissions";
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    public static final String CLAIM_ENTERPRISE_ID = "enterprise_id";
    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_SESSION_ID = "session_id";
    public static final String CLAIM_LOGIN_VERSION = "login_version";

    public static final String REDIS_LOGIN_VERSION_PREFIX = "auth:login-version:";
    public static final String REDIS_REFRESH_PREFIX = "auth:refresh:";
    public static final String REDIS_AUTHORIZATION_PREFIX = "auth:authorization:";

    private SecurityConstants() {
    }
}
