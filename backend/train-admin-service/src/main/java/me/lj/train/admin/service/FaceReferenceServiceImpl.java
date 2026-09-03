package me.lj.train.admin.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateWrapper;
import me.lj.train.admin.constant.AdminPermissions;
import me.lj.train.admin.mapper.UserMapper;
import me.lj.train.admin.model.entity.UserEntity;
import me.lj.train.admin.support.AdminGuard;
import me.lj.train.api.admin.FaceReferenceModels.BindFaceReferenceCommand;
import me.lj.train.api.admin.FaceReferenceModels.FaceReferenceChangeView;
import me.lj.train.api.admin.FaceReferenceModels.FaceReferenceView;
import me.lj.train.api.admin.FaceReferenceService;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;

import static me.lj.train.admin.model.table.UserTableDef.USER;

/**
 * 用户人脸登记照元数据目录实现，OSS内容仍由培训服务管理。
 */
@DubboService(timeout = 5000, retries = 0)
public class FaceReferenceServiceImpl extends AdminServiceSupport implements FaceReferenceService {

    private final UserMapper userMapper;

    public FaceReferenceServiceImpl(
            PlatformTransactionManager transactionManager,
            UserMapper userMapper) {
        super(transactionManager);
        this.userMapper = userMapper;
    }

    @Override
    public Result<FaceReferenceView> get(Long userId) {
        return execute(() -> {
            LoginUser operator = UserContext.require();
            Long enterpriseId = requireReadEnterprise(operator, userId);
            return toView(requireUser(userId, enterpriseId, false));
        });
    }

    @Override
    public Result<FaceReferenceChangeView> bind(BindFaceReferenceCommand command) {
        return executeTransactional(() -> {
            if (command == null || command.storageObjectId() == null) {
                throw new BusinessException(AppErrorCode.PARAM_INVALID, "登记照存储对象不能为空");
            }
            Long enterpriseId = AdminGuard.requireEnterprisePermission(
                    AdminPermissions.FACE_CHECK_MANAGE);
            UserEntity user = requireUser(command.userId(), enterpriseId, true);
            Long previousObjectId = user.getFaceReferenceObjectId();
            LocalDateTime now = LocalDateTime.now();
            UpdateWrapper<UserEntity> update = UpdateWrapper.of(UserEntity.class)
                    .set(USER.FACE_REFERENCE_OBJECT_ID, command.storageObjectId())
                    .set(USER.FACE_REFERENCE_UPDATED_AT, now)
                    .set(USER.UPDATED_BY, UserContext.require().getUserId());
            userMapper.updateByCondition(update.toEntity(), USER.ID.eq(user.getId())
                    .and(USER.ENTERPRISE_ID.eq(enterpriseId)));
            return new FaceReferenceChangeView(
                    new FaceReferenceView(user.getId(), command.storageObjectId(), true, now),
                    previousObjectId);
        });
    }

    @Override
    public Result<FaceReferenceChangeView> remove(Long userId) {
        return executeTransactional(() -> {
            Long enterpriseId = AdminGuard.requireEnterprisePermission(
                    AdminPermissions.FACE_CHECK_MANAGE);
            UserEntity user = requireUser(userId, enterpriseId, true);
            Long previousObjectId = user.getFaceReferenceObjectId();
            UpdateWrapper<UserEntity> update = UpdateWrapper.of(UserEntity.class)
                    .set(USER.FACE_REFERENCE_OBJECT_ID, null)
                    .set(USER.FACE_REFERENCE_UPDATED_AT, null)
                    .set(USER.UPDATED_BY, UserContext.require().getUserId());
            userMapper.updateByCondition(update.toEntity(), USER.ID.eq(user.getId())
                    .and(USER.ENTERPRISE_ID.eq(enterpriseId)));
            return new FaceReferenceChangeView(
                    new FaceReferenceView(user.getId(), null, false, null), previousObjectId);
        });
    }

    /**
     * 学员仅能解析本人登记照；管理员查看他人时必须具备查看权限。
     */
    private Long requireReadEnterprise(LoginUser operator, Long userId) {
        if (userId != null && userId.equals(operator.getUserId())) {
            if (operator.getEnterpriseId() == null) {
                throw new BusinessException(AppErrorCode.FORBIDDEN);
            }
            return operator.getEnterpriseId();
        }
        return AdminGuard.requireEnterpriseAnyPermission(
                AdminPermissions.FACE_CHECK_VIEW, AdminPermissions.FACE_CHECK_MANAGE);
    }

    private UserEntity requireUser(Long userId, Long enterpriseId, boolean lock) {
        QueryWrapper query = QueryWrapper.create()
                .where(USER.ID.eq(userId))
                .and(USER.ENTERPRISE_ID.eq(enterpriseId));
        if (lock) {
            query.forUpdate();
        }
        UserEntity user = userId == null ? null : userMapper.selectOneByQuery(query);
        if (user == null) {
            throw new BusinessException(AppErrorCode.USER_NOT_FOUND);
        }
        AdminGuard.checkEnterprise(user.getEnterpriseId(), enterpriseId);
        return user;
    }

    private FaceReferenceView toView(UserEntity user) {
        return new FaceReferenceView(
                user.getId(), user.getFaceReferenceObjectId(),
                user.getFaceReferenceObjectId() != null, user.getFaceReferenceUpdatedAt());
    }
}
