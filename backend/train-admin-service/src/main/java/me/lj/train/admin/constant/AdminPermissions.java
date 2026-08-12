package me.lj.train.admin.constant;

/**
 * 管理功能权限码。
 */
public final class AdminPermissions {

    public static final String ENTERPRISE_VIEW = "admin:enterprise:view";
    public static final String ENTERPRISE_CREATE = "admin:enterprise:create";
    public static final String ENTERPRISE_UPDATE = "admin:enterprise:update";
    public static final String ENTERPRISE_STATUS = "admin:enterprise:status";
    public static final String ADDRESS_VIEW = "admin:address:view";
    public static final String ADDRESS_CREATE = "admin:address:create";
    public static final String ADDRESS_UPDATE = "admin:address:update";
    public static final String ORG_VIEW = "admin:org:view";
    public static final String ORG_CREATE = "admin:org:create";
    public static final String ORG_UPDATE = "admin:org:update";
    public static final String ORG_DELETE = "admin:org:delete";
    public static final String USER_VIEW = "admin:user:view";
    public static final String USER_CREATE = "admin:user:create";
    public static final String USER_UPDATE = "admin:user:update";
    public static final String USER_STATUS = "admin:user:status";
    public static final String USER_RESET_PASSWORD = "admin:user:reset-password";
    public static final String USER_ASSIGN_ROLE = "admin:user:assign-role";
    public static final String ROLE_VIEW = "admin:role:view";
    public static final String ROLE_CREATE = "admin:role:create";
    public static final String ROLE_UPDATE = "admin:role:update";
    public static final String ROLE_STATUS = "admin:role:status";
    public static final String ROLE_DELETE = "admin:role:delete";
    public static final String ROLE_ASSIGN_PERMISSION = "admin:role:assign-permission";
    public static final String PERMISSION_VIEW = "admin:permission:view";

    private AdminPermissions() {
    }
}
