package me.lj.train.admin.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateWrapper;
import me.lj.train.admin.constant.AdminConstants;
import me.lj.train.admin.mapper.OrgMapper;
import me.lj.train.admin.mapper.PermissionMapper;
import me.lj.train.admin.mapper.RoleMapper;
import me.lj.train.admin.mapper.UserMapper;
import me.lj.train.admin.mapper.UserRoleMapper;
import me.lj.train.admin.model.entity.OrgEntity;
import me.lj.train.admin.model.entity.PermissionEntity;
import me.lj.train.admin.model.entity.RoleEntity;
import me.lj.train.admin.model.entity.UserEntity;
import me.lj.train.admin.model.entity.UserRoleEntity;
import me.lj.train.admin.support.AdminGuard;
import me.lj.train.admin.support.AuthorizationCacheService;
import me.lj.train.api.admin.AdminAuthService;
import me.lj.train.api.admin.AdminModels.ChangePasswordCommand;
import me.lj.train.api.admin.AdminModels.LoginAccount;
import me.lj.train.api.admin.AdminModels.LoginCommand;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.core.util.IdGenerator;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;
import me.lj.train.common.security.support.PasswordPolicy;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static me.lj.train.admin.model.table.OrgTableDef.ORG;
import static me.lj.train.admin.model.table.PermissionTableDef.PERMISSION;
import static me.lj.train.admin.model.table.RoleTableDef.ROLE;
import static me.lj.train.admin.model.table.UserTableDef.USER;

/**
 * 账号认证RPC实现，直接编排MyBatis-Flex Mapper。
 */
@DubboService(timeout = 3000, retries = 0)
public class AdminAuthServiceImpl extends AdminServiceSupport implements AdminAuthService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final OrgMapper orgMapper;
    private final PermissionMapper permissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthorizationCacheService cacheService;

    public AdminAuthServiceImpl(
            PlatformTransactionManager transactionManager,
            UserMapper userMapper,
            RoleMapper roleMapper,
            OrgMapper orgMapper,
            PermissionMapper permissionMapper,
            UserRoleMapper userRoleMapper,
            PasswordEncoder passwordEncoder,
            AuthorizationCacheService cacheService) {
        super(transactionManager);
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.orgMapper = orgMapper;
        this.permissionMapper = permissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.cacheService = cacheService;
    }

    @Override
    public Result<LoginAccount> authenticate(LoginCommand command) {
        return execute(() -> {
            if (command == null) {
                throw new BusinessException(AppErrorCode.INVALID_CREDENTIALS);
            }
            String username = AdminGuard.normalizeUsername(command.username());
            UserEntity user = findByUsername(username);
            if (user == null || !passwordEncoder.matches(command.password(), user.getPasswordHash())) {
                throw new BusinessException(AppErrorCode.INVALID_CREDENTIALS);
            }
            checkAvailable(user);
            cacheService.syncLoginVersion(user);
            return buildLoginAccount(user);
        });
    }

    @Override
    public Result<LoginAccount> getAuthorization(Long userId) {
        return execute(() -> {
            UserEntity user = userId == null ? null : userMapper.selectOneById(userId);
            if (user == null) {
                throw new BusinessException(AppErrorCode.UNAUTHORIZED);
            }
            checkAvailable(user);
            cacheService.syncLoginVersion(user);
            return buildLoginAccount(user);
        });
    }

    @Override
    public Result<LoginAccount> changePassword(ChangePasswordCommand command) {
        return executeTransactional(() -> {
            LoginUser currentUser = UserContext.require();
            if (command == null || command.userId() == null || !command.userId().equals(currentUser.getUserId())) {
                throw new BusinessException(AppErrorCode.FORBIDDEN);
            }
            UserEntity user = userMapper.selectOneById(command.userId());
            if (user == null) {
                throw new BusinessException(AppErrorCode.USER_NOT_FOUND);
            }
            if (!passwordEncoder.matches(command.oldPassword(), user.getPasswordHash())) {
                throw new BusinessException(AppErrorCode.PASSWORD_INCORRECT);
            }
            if (!PasswordPolicy.isValid(command.newPassword())) {
                throw new BusinessException(AppErrorCode.PASSWORD_POLICY_INVALID);
            }
            UpdateWrapper<UserEntity> update = UpdateWrapper.of(UserEntity.class)
                    .set(USER.PASSWORD_HASH, passwordEncoder.encode(command.newPassword()))
                    .set(USER.MUST_CHANGE_PASSWORD, false)
                    .set(USER.LOGIN_VERSION, USER.LOGIN_VERSION.add(1))
                    .set(USER.UPDATED_BY, currentUser.getUserId());
            userMapper.updateByCondition(update.toEntity(), USER.ID.eq(user.getId()));
            UserEntity changedUser = userMapper.selectOneById(user.getId());
            cacheService.syncLoginVersion(changedUser);
            cacheService.invalidateAuthorization(changedUser.getId());
            return buildLoginAccount(changedUser);
        });
    }

    /**
     * 幂等初始化唯一平台超级管理员；启动流程需要直接感知并终止初始化异常。
     */
    public void bootstrapSuperAdmin(String username, String password, String displayName) {
        runInTransaction(() -> doBootstrapSuperAdmin(username, password, displayName));
    }

    private void doBootstrapSuperAdmin(String username, String password, String displayName) {
        String normalizedUsername = AdminGuard.normalizeUsername(username);
        if (!PasswordPolicy.isValid(password)) {
            throw new BusinessException(AppErrorCode.PASSWORD_POLICY_INVALID);
        }
        lockBootstrap();
        UserEntity existing = findByUsername(normalizedUsername);
        if (existing != null) {
            if (!existing.isPlatformAdmin()) {
                throw new BusinessException(AppErrorCode.USERNAME_EXISTS);
            }
            cacheService.syncLoginVersion(existing);
            return;
        }
        UserEntity platformAdmin = userMapper.selectOneByQuery(QueryWrapper.create()
                .where(USER.PLATFORM_ADMIN.eq(true)));
        if (platformAdmin != null) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "平台超级管理员已经初始化");
        }

        RoleEntity role = roleMapper.selectOneByQuery(QueryWrapper.create()
                .where(ROLE.ENTERPRISE_ID.isNull())
                .and(ROLE.ROLE_CODE.eq(AdminConstants.ROLE_SUPER_ADMIN)));
        if (role == null) {
            role = new RoleEntity();
            role.setId(IdGenerator.nextId());
            role.setRoleCode(AdminConstants.ROLE_SUPER_ADMIN);
            role.setRoleName("平台超级管理员");
            role.setDescription("平台内置超级管理员角色");
            role.setStatus(AdminConstants.STATUS_ENABLED);
            role.setBuiltIn(true);
            roleMapper.insertSelective(role);
        }

        UserEntity user = new UserEntity();
        user.setId(IdGenerator.nextId());
        user.setUsername(normalizedUsername);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(AdminGuard.requireText(displayName, "显示名称"));
        user.setStatus(AdminConstants.STATUS_ENABLED);
        user.setLoginVersion(1L);
        user.setMustChangePassword(false);
        user.setPlatformAdmin(true);
        user.setCreatedBy(user.getId());
        user.setUpdatedBy(user.getId());
        userMapper.insertSelective(user);
        userRoleMapper.insertSelective(newUserRole(user.getId(), role.getId()));
        cacheService.syncLoginVersion(user);
    }

    /**
     * 固定权限目录在所有实例启动前已由Flyway创建，以行锁串行化超管初始化。
     */
    private void lockBootstrap() {
        PermissionEntity lockRow = permissionMapper.selectOneByQuery(QueryWrapper.create()
                .where(PERMISSION.ID.eq(100L))
                .forUpdate());
        if (lockRow == null) {
            throw new BusinessException(AppErrorCode.SYSTEM_ERROR, "固定权限目录尚未初始化");
        }
    }

    private UserEntity findByUsername(String username) {
        return userMapper.selectOneByQuery(QueryWrapper.create().where(USER.USERNAME.eq(username)));
    }

    private OrgEntity findEnterprise(Long enterpriseId) {
        return enterpriseId == null ? null : orgMapper.selectOneByQuery(QueryWrapper.create()
                .where(ORG.ID.eq(enterpriseId))
                .and(ORG.ORG_TYPE.eq(AdminConstants.ORG_ENTERPRISE)));
    }

    private void checkAvailable(UserEntity user) {
        if (!AdminConstants.STATUS_ENABLED.equals(user.getStatus())) {
            throw new BusinessException(AppErrorCode.ACCOUNT_DISABLED);
        }
        if (user.getEnterpriseId() != null) {
            OrgEntity enterprise = findEnterprise(user.getEnterpriseId());
            if (enterprise == null || !AdminConstants.STATUS_ENABLED.equals(enterprise.getStatus())) {
                throw new BusinessException(AppErrorCode.ACCOUNT_DISABLED);
            }
        }
    }

    private LoginAccount buildLoginAccount(UserEntity user) {
        OrgEntity enterprise = findEnterprise(user.getEnterpriseId());
        String enterpriseName = enterprise == null ? null : enterprise.getOrgName();
        if (user.isPlatformAdmin()) {
            return new LoginAccount(
                    user.getId(),
                    null,
                    user.getUsername(),
                    user.getDisplayName(),
                    null,
                    user.getLoginVersion(),
                    true,
                    user.isMustChangePassword(),
                    Collections.singletonList(AdminConstants.ROLE_SUPER_ADMIN),
                    Collections.singletonList("*"));
        }
        List<RoleEntity> roles = roleMapper.listByUserId(user.getId()).stream()
                .filter(role -> AdminConstants.STATUS_ENABLED.equals(role.getStatus()))
                .collect(Collectors.toList());
        List<String> roleCodes = roles.stream().map(RoleEntity::getRoleCode).collect(Collectors.toList());
        List<String> permissions = roleMapper.listPermissionCodesByUserId(user.getId()).stream()
                .distinct()
                .collect(Collectors.toList());
        return new LoginAccount(
                user.getId(),
                user.getEnterpriseId(),
                user.getUsername(),
                user.getDisplayName(),
                enterpriseName,
                user.getLoginVersion(),
                false,
                user.isMustChangePassword(),
                roleCodes,
                permissions);
    }

    private UserRoleEntity newUserRole(Long userId, Long roleId) {
        UserRoleEntity relation = new UserRoleEntity();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        return relation;
    }
}
