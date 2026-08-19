package me.lj.train.admin.support;

import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 管理操作的权限、数据范围及基础格式校验。
 */
public final class AdminGuard {

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z0-9_-]{2,64}");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9._-]{3,64}");

    private AdminGuard() {
    }

    public static LoginUser requirePermission(String permission) {
        LoginUser loginUser = UserContext.require();
        if (loginUser.isMustChangePassword()) {
            throw new BusinessException(AppErrorCode.PASSWORD_CHANGE_REQUIRED);
        }
        if (!loginUser.hasPermission(permission)) {
            throw new BusinessException(AppErrorCode.FORBIDDEN);
        }
        return loginUser;
    }

    public static LoginUser requirePlatformPermission(String permission) {
        LoginUser loginUser = requirePermission(permission);
        if (!loginUser.isPlatformAdmin()) {
            throw new BusinessException(AppErrorCode.FORBIDDEN);
        }
        return loginUser;
    }

    public static Long requireEnterprisePermission(String permission) {
        LoginUser loginUser = requirePermission(permission);
        if (loginUser.getEnterpriseId() == null) {
            throw new BusinessException(AppErrorCode.DATA_SCOPE_VIOLATION);
        }
        return loginUser.getEnterpriseId();
    }

    /**
     * 要求当前企业账号至少拥有一项指定权限。
     */
    public static Long requireEnterpriseAnyPermission(String... permissions) {
        LoginUser loginUser = UserContext.require();
        if (loginUser.isMustChangePassword()) {
            throw new BusinessException(AppErrorCode.PASSWORD_CHANGE_REQUIRED);
        }
        if (loginUser.getEnterpriseId() == null) {
            throw new BusinessException(AppErrorCode.DATA_SCOPE_VIOLATION);
        }
        if (permissions == null || Arrays.stream(permissions).noneMatch(loginUser::hasPermission)) {
            throw new BusinessException(AppErrorCode.FORBIDDEN);
        }
        return loginUser.getEnterpriseId();
    }

    public static void checkEnterprise(Long actualEnterpriseId, Long expectedEnterpriseId) {
        if (actualEnterpriseId == null || !actualEnterpriseId.equals(expectedEnterpriseId)) {
            throw new BusinessException(AppErrorCode.DATA_SCOPE_VIOLATION);
        }
    }

    public static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, fieldName + "不能为空");
        }
        return value.trim();
    }

    public static String normalizeCode(String value, String fieldName) {
        String code = requireText(value, fieldName).toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID,
                    fieldName + "仅支持2至64位大写字母、数字、下划线和中划线");
        }
        return code;
    }

    public static String normalizeUsername(String value) {
        String username = requireText(value, "用户名");
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID,
                    "用户名仅支持3至64位字母、数字、点、下划线和中划线");
        }
        return username;
    }

    public static String normalizeStatus(String value) {
        if (!"ENABLED".equals(value) && !"DISABLED".equals(value)) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "状态值不正确");
        }
        return value;
    }
}
