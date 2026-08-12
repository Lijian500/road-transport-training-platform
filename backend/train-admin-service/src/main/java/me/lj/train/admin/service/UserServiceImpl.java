package me.lj.train.admin.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateWrapper;
import me.lj.train.admin.constant.AdminConstants;
import me.lj.train.admin.constant.AdminPermissions;
import me.lj.train.admin.mapper.OrgMapper;
import me.lj.train.admin.mapper.OrgUserMapper;
import me.lj.train.admin.mapper.RoleMapper;
import me.lj.train.admin.mapper.UserMapper;
import me.lj.train.admin.mapper.UserRoleMapper;
import me.lj.train.admin.model.entity.OrgEntity;
import me.lj.train.admin.model.entity.OrgUserEntity;
import me.lj.train.admin.model.entity.RoleEntity;
import me.lj.train.admin.model.entity.UserEntity;
import me.lj.train.admin.model.entity.UserRoleEntity;
import me.lj.train.admin.support.AdminGuard;
import me.lj.train.admin.support.AuthorizationCacheService;
import me.lj.train.api.admin.AdminModels.AssignRolesCommand;
import me.lj.train.api.admin.AdminModels.ChangeStatusCommand;
import me.lj.train.api.admin.AdminModels.CreateUserCommand;
import me.lj.train.api.admin.AdminModels.ResetPasswordCommand;
import me.lj.train.api.admin.AdminModels.UpdateUserCommand;
import me.lj.train.api.admin.AdminModels.UserQuery;
import me.lj.train.api.admin.AdminModels.UserView;
import me.lj.train.api.admin.UserService;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.page.PageRequest;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.core.util.IdGenerator;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;
import me.lj.train.common.security.support.PasswordPolicy;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static me.lj.train.admin.model.table.OrgTableDef.ORG;
import static me.lj.train.admin.model.table.OrgUserTableDef.ORG_USER;
import static me.lj.train.admin.model.table.RoleTableDef.ROLE;
import static me.lj.train.admin.model.table.UserRoleTableDef.USER_ROLE;
import static me.lj.train.admin.model.table.UserTableDef.USER;

/**
 * 用户管理RPC实现，直接编排MyBatis-Flex Mapper。
 */
@DubboService(timeout = 5000, retries = 0)
public class UserServiceImpl extends AdminServiceSupport implements UserService {

    private final UserMapper userMapper;
    private final OrgMapper orgMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final OrgUserMapper orgUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthorizationCacheService cacheService;

    public UserServiceImpl(
            PlatformTransactionManager transactionManager,
            UserMapper userMapper,
            OrgMapper orgMapper,
            RoleMapper roleMapper,
            UserRoleMapper userRoleMapper,
            OrgUserMapper orgUserMapper,
            PasswordEncoder passwordEncoder,
            AuthorizationCacheService cacheService) {
        super(transactionManager);
        this.userMapper = userMapper;
        this.orgMapper = orgMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.orgUserMapper = orgUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.cacheService = cacheService;
    }

    @Override
    public Result<PageResult<UserView>> page(UserQuery query) {
        return execute(() -> {
            Long enterpriseId = AdminGuard.requireEnterprisePermission(AdminPermissions.USER_VIEW);
            PageRequest request = query.toPageRequest();
            String keyword = trim(query.keyword());
            String status = trim(query.status());
            QueryWrapper wrapper = QueryWrapper.create()
                    .where(USER.ENTERPRISE_ID.eq(enterpriseId))
                    .and(USER.USERNAME.like(keyword)
                            .or(USER.DISPLAY_NAME.like(keyword))
                            .or(USER.PHONE.like(keyword))
                            .when(hasText(keyword)))
                    .and(USER.ORG_ID.eq(query.orgId()).when(query.orgId() != null))
                    .and(USER.STATUS.eq(status).when(hasText(status)))
                    .orderBy(USER.CREATED_AT.desc());
            Page<UserEntity> page = userMapper.paginate(
                    request.getPageNumber(), request.getPageSize(), wrapper);
            return PageResult.of(toViews(page.getRecords()), page.getTotalRow(), request);
        });
    }

    @Override
    public Result<UserView> create(CreateUserCommand command) {
        return executeTransactional(() -> {
            Long enterpriseId = AdminGuard.requireEnterprisePermission(AdminPermissions.USER_CREATE);
            LoginUser operator = UserContext.require();
            String username = AdminGuard.normalizeUsername(command.username());
            if (findByUsername(username) != null) {
                throw new BusinessException(AppErrorCode.USERNAME_EXISTS);
            }
            if (!PasswordPolicy.isValid(command.temporaryPassword())) {
                throw new BusinessException(AppErrorCode.PASSWORD_POLICY_INVALID);
            }
            OrgEntity org = requireOrg(command.orgId(), enterpriseId);
            List<Long> roleIds = normalizeRoleIds(command.roleIds(), enterpriseId, true);

            UserEntity user = new UserEntity();
            user.setId(IdGenerator.nextId());
            user.setEnterpriseId(enterpriseId);
            user.setOrgId(org.getId());
            user.setUsername(username);
            user.setPasswordHash(passwordEncoder.encode(command.temporaryPassword()));
            user.setDisplayName(AdminGuard.requireText(command.displayName(), "姓名"));
            user.setPhone(trim(command.phone()));
            user.setStatus(AdminConstants.STATUS_ENABLED);
            user.setLoginVersion(1L);
            user.setMustChangePassword(true);
            user.setPlatformAdmin(false);
            user.setCreatedBy(operator.getUserId());
            user.setUpdatedBy(operator.getUserId());
            userMapper.insertSelective(user);
            orgUserMapper.insertSelective(newOrgUser(user.getId(), org.getId(), enterpriseId));
            insertUserRoles(user.getId(), roleIds, enterpriseId);
            cacheService.syncLoginVersion(user);
            return loadView(user.getId());
        });
    }

    @Override
    public Result<UserView> update(UpdateUserCommand command) {
        return executeTransactional(() -> {
            Long enterpriseId = AdminGuard.requireEnterprisePermission(AdminPermissions.USER_UPDATE);
            UserEntity user = requireUser(command.id(), enterpriseId);
            checkCanManage(user);
            OrgEntity org = requireOrg(command.orgId(), enterpriseId);
            UpdateWrapper<UserEntity> update = UpdateWrapper.of(UserEntity.class)
                    .set(USER.DISPLAY_NAME, AdminGuard.requireText(command.displayName(), "姓名"))
                    .set(USER.PHONE, trim(command.phone()))
                    .set(USER.ORG_ID, org.getId())
                    .set(USER.UPDATED_BY, UserContext.require().getUserId());
            userMapper.updateByCondition(update.toEntity(), USER.ID.eq(user.getId()));
            orgUserMapper.deleteByQuery(QueryWrapper.create().where(ORG_USER.USER_ID.eq(user.getId())));
            orgUserMapper.insertSelective(newOrgUser(user.getId(), org.getId(), enterpriseId));
            cacheService.invalidateAuthorization(user.getId());
            return loadView(user.getId());
        });
    }

    @Override
    public Result<?> changeStatus(ChangeStatusCommand command) {
        return executeVoidTransactional(() -> {
            Long enterpriseId = AdminGuard.requireEnterprisePermission(AdminPermissions.USER_STATUS);
            LoginUser operator = UserContext.require();
            UserEntity user = requireUser(command.id(), enterpriseId);
            checkCanManage(user);
            String status = AdminGuard.normalizeStatus(command.status());
            if (user.getId().equals(operator.getUserId()) && AdminConstants.STATUS_DISABLED.equals(status)) {
                throw new BusinessException(AppErrorCode.FORBIDDEN, "不能禁用当前登录账号");
            }
            boolean disablingAdmin = AdminConstants.STATUS_ENABLED.equals(user.getStatus())
                    && AdminConstants.STATUS_DISABLED.equals(status)
                    && hasRole(user.getId(), AdminConstants.ROLE_ENTERPRISE_ADMIN);
            if (disablingAdmin) {
                lockEnterprise(enterpriseId);
                if (userMapper.countEnabledEnterpriseAdmins(enterpriseId) <= 1) {
                    throw new BusinessException(AppErrorCode.LAST_ADMIN_PROTECTED);
                }
            }
            UpdateWrapper<UserEntity> update = UpdateWrapper.of(UserEntity.class)
                    .set(USER.STATUS, status)
                    .set(USER.LOGIN_VERSION, USER.LOGIN_VERSION.add(1))
                    .set(USER.UPDATED_BY, operator.getUserId());
            userMapper.updateByCondition(update.toEntity(), USER.ID.eq(user.getId()));
            UserEntity changedUser = userMapper.selectOneById(user.getId());
            cacheService.syncLoginVersion(changedUser);
            cacheService.invalidateAuthorization(user.getId());
        });
    }

    @Override
    public Result<?> resetPassword(ResetPasswordCommand command) {
        return executeVoidTransactional(() -> {
            Long enterpriseId = AdminGuard.requireEnterprisePermission(AdminPermissions.USER_RESET_PASSWORD);
            LoginUser operator = UserContext.require();
            UserEntity user = requireUser(command.id(), enterpriseId);
            checkCanManage(user);
            if (user.getId().equals(operator.getUserId())) {
                throw new BusinessException(AppErrorCode.FORBIDDEN, "请通过修改密码功能更新自己的密码");
            }
            if (!PasswordPolicy.isValid(command.temporaryPassword())) {
                throw new BusinessException(AppErrorCode.PASSWORD_POLICY_INVALID);
            }
            UpdateWrapper<UserEntity> update = UpdateWrapper.of(UserEntity.class)
                    .set(USER.PASSWORD_HASH, passwordEncoder.encode(command.temporaryPassword()))
                    .set(USER.MUST_CHANGE_PASSWORD, true)
                    .set(USER.LOGIN_VERSION, USER.LOGIN_VERSION.add(1))
                    .set(USER.UPDATED_BY, operator.getUserId());
            userMapper.updateByCondition(update.toEntity(), USER.ID.eq(user.getId()));
            UserEntity changedUser = userMapper.selectOneById(user.getId());
            cacheService.syncLoginVersion(changedUser);
            cacheService.invalidateAuthorization(user.getId());
        });
    }

    @Override
    public Result<?> assignRoles(AssignRolesCommand command) {
        return executeVoidTransactional(() -> {
            Long enterpriseId = AdminGuard.requireEnterprisePermission(AdminPermissions.USER_ASSIGN_ROLE);
            UserEntity user = requireUser(command.userId(), enterpriseId);
            checkCanManage(user);
            List<Long> roleIds = normalizeRoleIds(command.roleIds(), enterpriseId, false);
            boolean hadAdminRole = hasRole(user.getId(), AdminConstants.ROLE_ENTERPRISE_ADMIN);
            boolean keepsAdminRole = selectRoles(roleIds).stream()
                    .anyMatch(role -> AdminConstants.ROLE_ENTERPRISE_ADMIN.equals(role.getRoleCode()));
            if (hadAdminRole
                    && !keepsAdminRole
                    && AdminConstants.STATUS_ENABLED.equals(user.getStatus())) {
                lockEnterprise(enterpriseId);
                if (userMapper.countEnabledEnterpriseAdmins(enterpriseId) <= 1) {
                    throw new BusinessException(AppErrorCode.LAST_ADMIN_PROTECTED);
                }
            }
            userRoleMapper.deleteByQuery(QueryWrapper.create().where(USER_ROLE.USER_ID.eq(user.getId())));
            insertUserRoles(user.getId(), roleIds, enterpriseId);
            cacheService.invalidateAuthorization(user.getId());
        });
    }

    /**
     * 校验角色归属；新增用户未选角色时仅企业可回退到内置学员角色。
     */
    private List<Long> normalizeRoleIds(
            List<Long> roleIds,
            Long enterpriseId,
            boolean requireRoleForCreate) {
        List<Long> normalized = roleIds == null
                ? new ArrayList<Long>()
                : roleIds.stream().filter(id -> id != null).distinct().collect(Collectors.toList());
        if (normalized.isEmpty()) {
            RoleEntity studentRole = findRoleByCode(enterpriseId, AdminConstants.ROLE_STUDENT);
            if (studentRole != null) {
                return Collections.singletonList(studentRole.getId());
            }
            if (requireRoleForCreate) {
                throw new BusinessException(AppErrorCode.PARAM_INVALID, "行管组织新增用户时必须选择角色");
            }
            return Collections.emptyList();
        }
        List<RoleEntity> roles = roleMapper.selectListByQuery(QueryWrapper.create()
                .where(ROLE.ENTERPRISE_ID.eq(enterpriseId))
                .and(ROLE.DELETED_AT.isNull())
                .and(ROLE.STATUS.eq(AdminConstants.STATUS_ENABLED))
                .and(ROLE.ID.in(normalized)));
        if (roles.size() != normalized.size()) {
            throw new BusinessException(AppErrorCode.DATA_SCOPE_VIOLATION);
        }
        LoginUser operator = UserContext.require();
        boolean canAssignAll = hasAllManagePermissions(
                operator, roleMapper.listPermissionCodesByRoleIds(normalized));
        if (!canAssignAll) {
            throw new BusinessException(AppErrorCode.FORBIDDEN, "不能分配权限高于当前账号的角色");
        }
        return normalized;
    }

    private void checkCanManage(UserEntity user) {
        LoginUser operator = UserContext.require();
        if (!hasAllManagePermissions(operator, roleMapper.listPermissionCodesByUserId(user.getId()))) {
            throw new BusinessException(AppErrorCode.FORBIDDEN, "不能管理权限高于当前账号的用户");
        }
    }

    private boolean hasAllManagePermissions(LoginUser operator, List<String> permissions) {
        return permissions.stream()
                .filter(permission -> permission.startsWith("admin:"))
                .allMatch(operator::hasPermission);
    }

    private boolean hasRole(Long userId, String roleCode) {
        return roleMapper.listByUserId(userId).stream()
                .anyMatch(role -> roleCode.equals(role.getRoleCode()));
    }

    private UserEntity requireUser(Long id, Long enterpriseId) {
        UserEntity user = id == null ? null : userMapper.selectOneById(id);
        if (user == null) {
            throw new BusinessException(AppErrorCode.USER_NOT_FOUND);
        }
        AdminGuard.checkEnterprise(user.getEnterpriseId(), enterpriseId);
        return user;
    }

    private OrgEntity requireOrg(Long id, Long enterpriseId) {
        OrgEntity org = id == null ? null : orgMapper.selectOneByQuery(QueryWrapper.create()
                .where(ORG.ID.eq(id))
                .and(ORG.DELETED_AT.isNull()));
        if (org == null) {
            throw new BusinessException(AppErrorCode.ORG_NOT_FOUND);
        }
        AdminGuard.checkEnterprise(org.getEnterpriseId(), enterpriseId);
        return org;
    }

    /**
     * 串行化同一根组织的管理员移除操作，避免并发操作绕过最后管理员保护。
     */
    private void lockEnterprise(Long enterpriseId) {
        OrgEntity enterprise = orgMapper.selectOneByQuery(QueryWrapper.create()
                .where(ORG.ID.eq(enterpriseId))
                .and(ORG.ORG_TYPE.eq(AdminConstants.ORG_ENTERPRISE))
                .and(ORG.DELETED_AT.isNull())
                .forUpdate());
        if (enterprise == null) {
            throw new BusinessException(AppErrorCode.ENTERPRISE_NOT_FOUND);
        }
    }

    private UserEntity findByUsername(String username) {
        return userMapper.selectOneByQuery(QueryWrapper.create().where(USER.USERNAME.eq(username)));
    }

    private RoleEntity findRoleByCode(Long enterpriseId, String code) {
        return roleMapper.selectOneByQuery(QueryWrapper.create()
                .where(ROLE.ENTERPRISE_ID.eq(enterpriseId))
                .and(ROLE.ROLE_CODE.eq(code))
                .and(ROLE.DELETED_AT.isNull()));
    }

    private List<RoleEntity> selectRoles(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return roleMapper.selectListByQuery(QueryWrapper.create()
                .where(ROLE.ID.in(roleIds))
                .and(ROLE.DELETED_AT.isNull())
                .orderBy(ROLE.BUILT_IN.desc(), ROLE.ROLE_NAME.asc()));
    }

    private void insertUserRoles(Long userId, List<Long> roleIds, Long enterpriseId) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        userRoleMapper.insertBatch(roleIds.stream()
                .map(roleId -> newUserRole(userId, roleId, enterpriseId))
                .collect(Collectors.toList()));
    }

    private UserView loadView(Long userId) {
        UserEntity user = userMapper.selectOneById(userId);
        List<UserView> views = toViews(Collections.singletonList(user));
        return views.get(0);
    }

    private List<UserView> toViews(List<UserEntity> users) {
        if (users == null || users.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> orgIds = users.stream()
                .map(UserEntity::getOrgId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, OrgEntity> orgMap = orgIds.isEmpty()
                ? Collections.emptyMap()
                : orgMapper.selectListByQuery(QueryWrapper.create()
                                .where(ORG.ID.in(orgIds))
                                .and(ORG.DELETED_AT.isNull()))
                        .stream()
                        .collect(Collectors.toMap(OrgEntity::getId, org -> org));

        List<Long> userIds = users.stream().map(UserEntity::getId).collect(Collectors.toList());
        Map<Long, Set<Long>> roleIdsByUser = new HashMap<Long, Set<Long>>();
        List<UserRoleEntity> relations = userRoleMapper.selectListByQuery(
                QueryWrapper.create().where(USER_ROLE.USER_ID.in(userIds)));
        relations.forEach(relation -> roleIdsByUser
                .computeIfAbsent(relation.getUserId(), key -> new HashSet<Long>())
                .add(relation.getRoleId()));
        List<Long> roleIds = relations.stream()
                .map(UserRoleEntity::getRoleId)
                .distinct()
                .collect(Collectors.toList());
        List<RoleEntity> roles = selectRoles(roleIds);

        Map<Long, List<RoleEntity>> rolesByUser = new LinkedHashMap<Long, List<RoleEntity>>();
        for (Long userId : userIds) {
            Set<Long> assignedRoleIds = roleIdsByUser.getOrDefault(userId, Collections.emptySet());
            rolesByUser.put(userId, roles.stream()
                    .filter(role -> assignedRoleIds.contains(role.getId()))
                    .collect(Collectors.toList()));
        }
        return users.stream()
                .map(user -> toView(user, orgMap.get(user.getOrgId()),
                        rolesByUser.getOrDefault(user.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
    }

    private UserView toView(UserEntity user, OrgEntity org, List<RoleEntity> roles) {
        return new UserView(
                user.getId(),
                user.getEnterpriseId(),
                user.getOrgId(),
                org == null ? null : org.getOrgName(),
                user.getUsername(),
                user.getDisplayName(),
                user.getPhone(),
                user.getStatus(),
                user.isMustChangePassword(),
                roles.stream().map(RoleEntity::getId).collect(Collectors.toList()),
                roles.stream().map(RoleEntity::getRoleName).collect(Collectors.toList()),
                user.getCreatedAt());
    }

    private OrgUserEntity newOrgUser(Long userId, Long orgId, Long enterpriseId) {
        OrgUserEntity relation = new OrgUserEntity();
        relation.setUserId(userId);
        relation.setOrgId(orgId);
        relation.setEnterpriseId(enterpriseId);
        relation.setPrimary(true);
        return relation;
    }

    private UserRoleEntity newUserRole(Long userId, Long roleId, Long enterpriseId) {
        UserRoleEntity relation = new UserRoleEntity();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        relation.setEnterpriseId(enterpriseId);
        return relation;
    }

    private boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
