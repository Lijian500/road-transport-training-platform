package me.lj.train.common.security.jwt;

/**
 * 已校验的Access Token核心声明。
 */
public class AccessTokenClaims {

    private final Long userId;
    private final Long enterpriseId;
    private final String username;
    private final String sessionId;
    private final long loginVersion;

    public AccessTokenClaims(Long userId, Long enterpriseId, String username, String sessionId, long loginVersion) {
        this.userId = userId;
        this.enterpriseId = enterpriseId;
        this.username = username;
        this.sessionId = sessionId;
        this.loginVersion = loginVersion;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getEnterpriseId() {
        return enterpriseId;
    }

    public String getUsername() {
        return username;
    }

    public String getSessionId() {
        return sessionId;
    }

    public long getLoginVersion() {
        return loginVersion;
    }
}
