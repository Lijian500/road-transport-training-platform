package me.lj.train.common.security.model;

import me.lj.train.common.security.support.PermissionMatcher;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 系统内部统一登录用户对象。
 */
public class LoginUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private Long enterpriseId;
    private String sessionId;
    private String username;
    private String displayName;
    private String enterpriseName;
    private long loginVersion;
    private boolean platformAdmin;
    private boolean mustChangePassword;
    private List<String> roles = new ArrayList<String>();
    private List<String> permissions = new ArrayList<String>();

    public boolean hasPermission(String permission) {
        return platformAdmin || PermissionMatcher.matchesAny(permissions, permission);
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getEnterpriseId() {
        return enterpriseId;
    }

    public void setEnterpriseId(Long enterpriseId) {
        this.enterpriseId = enterpriseId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEnterpriseName() {
        return enterpriseName;
    }

    public void setEnterpriseName(String enterpriseName) {
        this.enterpriseName = enterpriseName;
    }

    public long getLoginVersion() {
        return loginVersion;
    }

    public void setLoginVersion(long loginVersion) {
        this.loginVersion = loginVersion;
    }

    public boolean isPlatformAdmin() {
        return platformAdmin;
    }

    public void setPlatformAdmin(boolean platformAdmin) {
        this.platformAdmin = platformAdmin;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles == null ? new ArrayList<String>() : roles;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions == null ? new ArrayList<String>() : permissions;
    }
}
