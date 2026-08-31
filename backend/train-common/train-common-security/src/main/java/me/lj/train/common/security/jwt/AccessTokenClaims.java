package me.lj.train.common.security.jwt;

import java.time.Instant;

/**
 * 已校验的Access Token核心声明。
 */
public class AccessTokenClaims {

    private final Long userId;
    private final Long enterpriseId;
    private final String username;
    private final String sessionId;
    private final long loginVersion;
    private final Instant expiresAt;

    public AccessTokenClaims(
            Long userId,
            Long enterpriseId,
            String username,
            String sessionId,
            long loginVersion,
            Instant expiresAt) {
        this.userId = userId;
        this.enterpriseId = enterpriseId;
        this.username = username;
        this.sessionId = sessionId;
        this.loginVersion = loginVersion;
        this.expiresAt = expiresAt;
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

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
