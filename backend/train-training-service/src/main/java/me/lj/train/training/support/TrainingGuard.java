package me.lj.train.training.support;

import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;

/**
 * 课程服务权限、租户范围及基础格式校验。
 */
public final class TrainingGuard {

    private TrainingGuard() {
    }

    public static Long requireEnterprisePermission(String permission) {
        LoginUser loginUser = UserContext.require();
        if (loginUser.isMustChangePassword()) {
            throw new BusinessException(AppErrorCode.PASSWORD_CHANGE_REQUIRED);
        }
        if (loginUser.isPlatformAdmin() || loginUser.getEnterpriseId() == null) {
            throw new BusinessException(AppErrorCode.DATA_SCOPE_VIOLATION, "平台账号不能操作企业课程");
        }
        if (!loginUser.hasPermission(permission)) {
            throw new BusinessException(AppErrorCode.FORBIDDEN);
        }
        return loginUser.getEnterpriseId();
    }

    public static void checkEnterprise(Long actualEnterpriseId, Long expectedEnterpriseId) {
        if (actualEnterpriseId == null || !actualEnterpriseId.equals(expectedEnterpriseId)) {
            throw new BusinessException(AppErrorCode.DATA_SCOPE_VIOLATION);
        }
    }

    public static String requireText(String value, String fieldName, int maxLength) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, fieldName + "不能为空");
        }
        if (normalized.length() > maxLength) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID,
                    fieldName + "不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    public static String optionalText(String value, String fieldName, int maxLength) {
        String normalized = value == null ? null : value.trim();
        if (normalized != null && normalized.length() > maxLength) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID,
                    fieldName + "不能超过" + maxLength + "个字符");
        }
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }
}
