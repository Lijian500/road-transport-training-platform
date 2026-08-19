package me.lj.train.learning.support;

import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;

/**
 * 学习服务权限和企业范围校验。
 */
public final class LearningGuard {

    public static final String STUDENT_LEARNING_STUDY = "student:learning:study";

    private LearningGuard() {
    }

    public static LoginUser requireStudent() {
        LoginUser user = UserContext.require();
        if (user.isMustChangePassword()) {
            throw new BusinessException(AppErrorCode.PASSWORD_CHANGE_REQUIRED);
        }
        if (user.isPlatformAdmin() || user.getEnterpriseId() == null
                || !user.hasPermission(STUDENT_LEARNING_STUDY)) {
            throw new BusinessException(AppErrorCode.FORBIDDEN);
        }
        return user;
    }
}
