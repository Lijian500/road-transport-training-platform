package me.lj.train.admin.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateWrapper;
import me.lj.train.admin.constant.AdminConstants;
import me.lj.train.admin.constant.AdminPermissions;
import me.lj.train.admin.mapper.PermissionMapper;
import me.lj.train.admin.mapper.RoleMapper;
import me.lj.train.admin.mapper.RolePermissionMapper;
import me.lj.train.admin.mapper.UserRoleMapper;
import me.lj.train.admin.model.entity.PermissionEntity;
import me.lj.train.admin.model.entity.RoleEntity;
import me.lj.train.admin.model.entity.RolePermissionEntity;
import me.lj.train.admin.model.entity.UserRoleEntity;
import me.lj.train.admin.support.AdminGuard;
import me.lj.train.admin.support.AuthorizationCacheService;
import me.lj.train.api.admin.AdminModels.AssignPermissionsCommand;
import me.lj.train.api.admin.AdminModels.ChangeStatusCommand;
import me.lj.train.api.admin.AdminModels.CreateRoleCommand;
import me.lj.train.api.admin.AdminModels.PermissionView;
import me.lj.train.api.admin.AdminModels.RoleQuery;
import me.lj.train.api.admin.AdminModels.RoleView;
import me.lj.train.api.admin.AdminModels.UpdateRoleCommand;
import me.lj.train.api.admin.RolePermissionService;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.page.PageRequest;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.core.util.IdGenerator;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static me.lj.train.admin.model.table.PermissionTableDef.PERMISSION;
import static me.lj.train.admin.model.table.RolePermissionTableDef.ROLE_PERMISSION;
import static me.lj.train.admin.model.table.RoleTableDef.ROLE;
import static me.lj.train.admin.model.table.UserRoleTableDef.USER_ROLE;

/**
 * 角色权限RPC实现，直接编排MyBatis-Flex Mapper。
 */
@DubboService(timeout = 5000, retries = 0)
public class RolePermissionServiceImpl extends AdminServiceSupport implements RolePermissionService {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final AuthorizationCacheService cacheService;

    public RolePermissionServiceImpl(
            PlatformTransactionManager transactionManager,
            RoleMapper roleMapper,
            PermissionMapper permissionMapper,
            RolePermissionMapper rolePermissionMapper,
            UserRoleMapper userRoleMapper,
            AuthorizationCacheService cacheService) {
        super(transactionManager);
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.cacheService = cacheService;
    }

    @Override
    public Result<PageResult<RoleView>> page(RoleQuery query) {
        return execute(() -> {
            Long enterpriseId = AdminGuard.requireEnterprisePermission(AdminPermissions.ROLE_VIEW);
            PageRequest request = query.toPageRequest();
            String keyword = trim(query.keyword());
            String status = trim(query.status());
            QueryWrapper wrapper = QueryWrapper.create()
                    .where(ROLE.ENTERPRISE_ID.eq(enterpriseId))
                    .and(ROLE.ROLE_NAME.like(keyword)
                            .or(ROLE.ROLE_CODE.like(keyword))
                            .when(hasText(keyword)))
                    .and(ROLE.STATUS.eq(status).when(hasText(status)))
                    .orderBy(ROLE.BUILT_IN.desc(), ROLE.CREATED_AT.asc());
            Page<RoleEntity> page = roleMapper.paginate(
                    request.getPageNumber(), request.getPageSize(), wrapper);
            return PageResult.of(toViews(page.getRecords()), page.getTotalRow(), request);
        });
    }

    @Override
    public Result<List<RoleView>> listEnabled() {
        return execute(() -> {
            Long enterpriseId = AdminGuard.requireEnterprisePermission(AdminPermissions.ROLE_VIEW);
            List<RoleEntity> roles = roleMapper.selectListByQuery(QueryWrapper.create()
                    .where(ROLE.ENTERPRISE_ID.eq(enterpriseId))
                    .and(ROLE.STATUS.eq(AdminConstants.STATUS_ENABLED))
                    .orderBy(ROLE.BUILT_IN.desc(), ROLE.ROLE_NAME.asc()));
            return toViews(roles);
        });
    }

    @Override
    public Result<RoleView> create(CreateRoleCommand command) {
        return executeTransactional(() -> {
            Long enterpriseId = AdminGuard.requireEnterprisePermission(AdminPermissions.ROLE_CREATE);
            LoginUser operator = UserContext.require();
            String code = AdminGuard.normalizeCode(command.code(), "角色编码");
            if (findByCode(enterpriseId, code) != null) {
                throw new BusinessException(AppErrorCode.ROLE_CODE_EXISTS);
            }
            RoleEntity role = new RoleEntity();
            role.setId(IdGenerator.nextId());
            role.setEnterpriseId(enterpriseId);
            role.setRoleCode(code);
            role.setRoleName(AdminGuard.requireText(command.name(), "角色名称"));
            role.setDescription(trim(command.description()));
            role.setStatus(AdminConstants.STATUS_ENABLED);
            role.setBuiltIn(false);
            role.setCreatedBy(operator.getUserId());
            role.setUpdatedBy(operator.getUserId());
            roleMapper.insertSelective(role);
            return toView(roleMapper.selectOneById(role.getId()), Collections.emptyList());
        });
    }

    @Override
    public Result<RoleView> update(UpdateRoleCommand command) {
        return executeTransactional(() -> {
            Long enterpriseId = AdminGuard.requireEnterprisePermission(AdminPermissions.ROLE_UPDATE);
            RoleEntity role = requireRole(command.id(), enterpriseId);
            checkMutable(role);
            checkCanManage(role);
            UpdateWrapper<RoleEntity> update = UpdateWrapper.of(RoleEntity.class)
                    .set(ROLE.ROLE_NAME, AdminGuard.requireText(command.name(), "角色名称"))
                    .set(ROLE.DESCRIPTION, trim(command.description()))
                    .set(ROLE.UPDATED_BY, UserContext.require().getUserId());
            roleMapper.updateByCondition(update.toEntity(), ROLE.ID.eq(role.getId()));
            cacheService.invalidateUsers(listRoleUserIds(role.getId()));
            RoleEntity changed = roleMapper.selectOneById(role.getId());
            return toView(changed, listPermissionIds(role.getId()));
        });
    }

    @Override
    public Result<?> changeStatus(ChangeStatusCommand command) {
        return executeVoidTransactional(() -> {
            Long enterpriseId = AdminGuard.requireEnterprisePermission(AdminPermissions.ROLE_STATUS);
            RoleEntity role = requireRole(command.id(), enterpriseId);
            checkMutable(role);
            checkCanManage(role);
            UpdateWrapper<RoleEntity> update = UpdateWrapper.of(RoleEntity.class)
                    .set(ROLE.STATUS, AdminGuard.normalizeStatus(command.status()))
                    .set(ROLE.UPDATED_BY, UserContext.require().getUserId());
            roleMapper.updateByCondition(update.toEntity(), ROLE.ID.eq(role.getId()));
            cacheService.invalidateUsers(listRoleUserIds(role.getId()));
        });
    }

    @Override
    public Result<?> delete(Long id) {
        return executeVoidTransactional(() -> {
            Long enterpriseId = AdminGuard.requireEnterprisePermission(AdminPermissions.ROLE_DELETE);
            RoleEntity role = requireRole(id, enterpriseId);
            checkMutable(role);
            checkCanManage(role);
            if (userRoleMapper.selectCountByQuery(
                    QueryWrapper.create().where(USER_ROLE.ROLE_ID.eq(id))) > 0) {
                throw new BusinessException(AppErrorCode.DATA_IN_USE);
            }
            rolePermissionMapper.deleteByQuery(
                    QueryWrapper.create().where(ROLE_PERMISSION.ROLE_ID.eq(id)));
            roleMapper.deleteById(id);
        });
    }

    @Override
    public Result<List<PermissionView>> permissionTree() {
        return execute(() -> {
            AdminGuard.requireEnterprisePermission(AdminPermissions.PERMISSION_VIEW);
            List<PermissionEntity> permissions = permissionMapper.selectListByQuery(QueryWrapper.create()
                    .where(PERMISSION.PERMISSION_SCOPE.in("ENTERPRISE", "COMMON"))
                    .orderBy(PERMISSION.SORT_ORDER.asc()));
            return buildTree(permissions);
        });
    }

    @Override
    public Result<?> assignPermissions(AssignPermissionsCommand command) {
        return executeVoidTransactional(() -> {
            Long enterpriseId = AdminGuard.requireEnterprisePermission(AdminPermissions.ROLE_ASSIGN_PERMISSION);
            LoginUser operator = UserContext.require();
            RoleEntity role = requireRole(command.roleId(), enterpriseId);
            checkMutable(role);
            checkCanManage(role);
            List<Long> permissionIds = command.permissionIds() == null
                    ? new ArrayList<Long>()
                    : command.permissionIds().stream()
                            .filter(id -> id != null)
                            .distinct()
                            .collect(Collectors.toList());
            if (!permissionIds.isEmpty()) {
                List<PermissionEntity> permissions = permissionMapper.selectListByQuery(QueryWrapper.create()
                        .where(PERMISSION.ID.in(permissionIds))
                        .and(PERMISSION.PERMISSION_SCOPE.in("ENTERPRISE", "COMMON")));
                if (permissions.size() != permissionIds.size()) {
                    throw new BusinessException(AppErrorCode.DATA_SCOPE_VIOLATION);
                }
                boolean canGrantAll = permissions.stream()
                        .allMatch(permission -> operator.hasPermission(permission.getPermissionCode()));
                if (!canGrantAll) {
                    throw new BusinessException(AppErrorCode.FORBIDDEN, "不能授予自己未拥有的权限");
                }
            }
            List<Long> affectedUserIds = listRoleUserIds(role.getId());
            rolePermissionMapper.deleteByQuery(
                    QueryWrapper.create().where(ROLE_PERMISSION.ROLE_ID.eq(role.getId())));
            if (!permissionIds.isEmpty()) {
                rolePermissionMapper.insertBatch(permissionIds.stream()
                        .map(permissionId -> newRolePermission(role.getId(), permissionId))
                        .collect(Collectors.toList()));
            }
            cacheService.invalidateUsers(affectedUserIds);
        });
    }

    private RoleEntity findByCode(Long enterpriseId, String code) {
        return roleMapper.selectOneByQuery(QueryWrapper.create()
                .where(ROLE.ENTERPRISE_ID.eq(enterpriseId))
                .and(ROLE.ROLE_CODE.eq(code)));
    }

    private List<Long> listRoleUserIds(Long roleId) {
        return userRoleMapper.selectListByQuery(
                        QueryWrapper.create().where(USER_ROLE.ROLE_ID.eq(roleId)))
                .stream()
                .map(UserRoleEntity::getUserId)
                .collect(Collectors.toList());
    }

    private List<Long> listPermissionIds(Long roleId) {
        return rolePermissionMapper.selectListByQuery(
                        QueryWrapper.create().where(ROLE_PERMISSION.ROLE_ID.eq(roleId)))
                .stream()
                .map(RolePermissionEntity::getPermissionId)
                .collect(Collectors.toList());
    }

    private List<RoleView> toViews(List<RoleEntity> roles) {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> roleIds = roles.stream().map(RoleEntity::getId).collect(Collectors.toList());
        Map<Long, List<Long>> permissionIds = new LinkedHashMap<Long, List<Long>>();
        rolePermissionMapper.selectListByQuery(
                        QueryWrapper.create().where(ROLE_PERMISSION.ROLE_ID.in(roleIds)))
                .forEach(relation -> permissionIds
                        .computeIfAbsent(relation.getRoleId(), key -> new ArrayList<Long>())
                        .add(relation.getPermissionId()));
        return roles.stream()
                .map(role -> toView(
                        role,
                        permissionIds.getOrDefault(role.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
    }

    private List<PermissionView> buildTree(List<PermissionEntity> permissions) {
        Map<Long, List<PermissionEntity>> childrenMap = permissions.stream()
                .filter(permission -> permission.getParentId() != null)
                .collect(Collectors.groupingBy(PermissionEntity::getParentId));
        return permissions.stream()
                .filter(permission -> permission.getParentId() == null)
                .map(permission -> toTree(permission, childrenMap))
                .collect(Collectors.toList());
    }

    private PermissionView toTree(
            PermissionEntity permission,
            Map<Long, List<PermissionEntity>> childrenMap) {
        List<PermissionView> children = childrenMap
                .getOrDefault(permission.getId(), new ArrayList<PermissionEntity>())
                .stream()
                .map(child -> toTree(child, childrenMap))
                .collect(Collectors.toList());
        return new PermissionView(
                permission.getId(),
                permission.getParentId(),
                permission.getPermissionCode(),
                permission.getPermissionName(),
                permission.getPermissionType(),
                permission.getPermissionScope(),
                permission.getSortOrder(),
                children);
    }

    private RoleEntity requireRole(Long id, Long enterpriseId) {
        RoleEntity role = id == null ? null : roleMapper.selectOneById(id);
        if (role == null) {
            throw new BusinessException(AppErrorCode.ROLE_NOT_FOUND);
        }
        AdminGuard.checkEnterprise(role.getEnterpriseId(), enterpriseId);
        return role;
    }

    private void checkMutable(RoleEntity role) {
        if (role.isBuiltIn()) {
            throw new BusinessException(AppErrorCode.BUILTIN_DATA_READONLY);
        }
    }

    private void checkCanManage(RoleEntity role) {
        LoginUser operator = UserContext.require();
        boolean canManage = roleMapper.listPermissionCodesByRoleId(role.getId()).stream()
                .filter(permission -> permission.startsWith("admin:"))
                .allMatch(operator::hasPermission);
        if (!canManage) {
            throw new BusinessException(AppErrorCode.FORBIDDEN, "不能管理权限高于当前账号的角色");
        }
    }

    private RoleView toView(RoleEntity role, List<Long> permissionIds) {
        return new RoleView(
                role.getId(),
                role.getRoleCode(),
                role.getRoleName(),
                role.getDescription(),
                role.getStatus(),
                role.isBuiltIn(),
                permissionIds,
                role.getCreatedAt());
    }

    private RolePermissionEntity newRolePermission(Long roleId, Long permissionId) {
        RolePermissionEntity relation = new RolePermissionEntity();
        relation.setRoleId(roleId);
        relation.setPermissionId(permissionId);
        return relation;
    }

    private boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
