package me.lj.train.common.security.support;

import java.util.Collection;

/**
 * 权限码匹配器，支持星号通配符。
 */
public final class PermissionMatcher {

    private PermissionMatcher() {
    }

    public static boolean matchesAny(Collection<String> grantedPermissions, String requiredPermission) {
        if (grantedPermissions == null || requiredPermission == null) {
            return false;
        }
        for (String grantedPermission : grantedPermissions) {
            if (matches(grantedPermission, requiredPermission)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matches(String grantedPermission, String requiredPermission) {
        if ("*".equals(grantedPermission) || grantedPermission.equals(requiredPermission)) {
            return true;
        }
        String[] grantedParts = grantedPermission.split(":");
        String[] requiredParts = requiredPermission.split(":");
        if (grantedParts.length != requiredParts.length) {
            return false;
        }
        for (int index = 0; index < grantedParts.length; index++) {
            if (!"*".equals(grantedParts[index]) && !grantedParts[index].equals(requiredParts[index])) {
                return false;
            }
        }
        return true;
    }
}
