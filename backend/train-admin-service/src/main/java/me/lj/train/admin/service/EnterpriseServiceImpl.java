package me.lj.train.admin.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateWrapper;
import me.lj.train.admin.constant.AdminConstants;
import me.lj.train.admin.constant.AdminPermissions;
import me.lj.train.admin.mapper.AddressMapper;
import me.lj.train.admin.mapper.OrgMapper;
import me.lj.train.admin.mapper.OrgUserMapper;
import me.lj.train.admin.mapper.PermissionMapper;
import me.lj.train.admin.mapper.RoleMapper;
import me.lj.train.admin.mapper.RolePermissionMapper;
import me.lj.train.admin.mapper.UserMapper;
import me.lj.train.admin.mapper.UserRoleMapper;
import me.lj.train.admin.model.entity.AddressEntity;
import me.lj.train.admin.model.entity.OrgEntity;
import me.lj.train.admin.model.entity.OrgUserEntity;
import me.lj.train.admin.model.entity.PermissionEntity;
import me.lj.train.admin.model.entity.RoleEntity;
import me.lj.train.admin.model.entity.RolePermissionEntity;
import me.lj.train.admin.model.entity.UserEntity;
import me.lj.train.admin.model.entity.UserRoleEntity;
import me.lj.train.admin.support.AdminGuard;
import me.lj.train.admin.support.AuthorizationCacheService;
import me.lj.train.api.admin.AdminModels.AddressPathNode;
import me.lj.train.api.admin.AdminModels.ChangeStatusCommand;
import me.lj.train.api.admin.AdminModels.CreateEnterpriseCommand;
import me.lj.train.api.admin.AdminModels.EnterpriseAdministratorView;
import me.lj.train.api.admin.AdminModels.EnterpriseQuery;
import me.lj.train.api.admin.AdminModels.EnterpriseView;
import me.lj.train.api.admin.AdminModels.ResetEnterpriseAdministratorPasswordCommand;
import me.lj.train.api.admin.AdminModels.UpdateEnterpriseCommand;
import me.lj.train.api.admin.EnterpriseService;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.page.PageRequest;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.core.util.IdGenerator;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static me.lj.train.admin.model.table.AddressTableDef.ADDRESS;
import static me.lj.train.admin.model.table.OrgTableDef.ORG;
import static me.lj.train.admin.model.table.PermissionTableDef.PERMISSION;
import static me.lj.train.admin.model.table.UserTableDef.USER;

/**
 * 企业及行管组织管理RPC实现，直接编排MyBatis-Flex Mapper。
 */
@DubboService(timeout = 5000, retries = 0)
public class EnterpriseServiceImpl extends AdminServiceSupport implements EnterpriseService {

    private final OrgMapper orgMapper;
    private final AddressMapper addressMapper;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final OrgUserMapper orgUserMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthorizationCacheService cacheService;

    public EnterpriseServiceImpl(
            PlatformTransactionManager transactionManager,
            OrgMapper orgMapper,
            AddressMapper addressMapper,
            UserMapper userMapper,
            RoleMapper roleMapper,
            PermissionMapper permissionMapper,
            RolePermissionMapper rolePermissionMapper,
            OrgUserMapper orgUserMapper,
            UserRoleMapper userRoleMapper,
            PasswordEncoder passwordEncoder,
            AuthorizationCacheService cacheService) {
        super(transactionManager);
        this.orgMapper = orgMapper;
        this.addressMapper = addressMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.orgUserMapper = orgUserMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.cacheService = cacheService;
    }

    @Override
    public Result<PageResult<EnterpriseView>> page(EnterpriseQuery query) {
        return execute(() -> {
            AdminGuard.requirePlatformPermission(AdminPermissions.ENTERPRISE_VIEW);
            PageRequest request = query.toPageRequest();
            String keyword = trim(query.keyword());
            String status = trim(query.status());
            String organizationNature = hasText(query.organizationNature())
                    ? normalizeOrganizationNature(query.organizationNature())
                    : null;
            QueryWrapper wrapper = QueryWrapper.create()
                    .where(ORG.ORG_TYPE.eq(AdminConstants.ORG_ENTERPRISE))
                    .and(ORG.DELETED_AT.isNull())
                    .and(ORG.ORG_NAME.like(keyword)
                            .or(ORG.ORG_CODE.like(keyword))
                            .when(hasText(keyword)))
                    .and(ORG.STATUS.eq(status).when(hasText(status)))
                    .and(ORG.ORGANIZATION_NATURE.eq(organizationNature)
                            .when(hasText(organizationNature)))
                    .orderBy(ORG.CREATED_AT.desc());
            Page<OrgEntity> page = orgMapper.paginate(
                    request.getPageNumber(), request.getPageSize(), wrapper);
            List<EnterpriseView> records = toViews(page.getRecords());
            return PageResult.of(records, page.getTotalRow(), request);
        });
    }

    @Override
    public Result<EnterpriseView> create(CreateEnterpriseCommand command) {
        return executeTransactional(() -> {
            LoginUser operator = AdminGuard.requirePlatformPermission(AdminPermissions.ENTERPRISE_CREATE);
            String code = AdminGuard.normalizeCode(command.code(), "组织编码");
            String name = AdminGuard.requireText(command.name(), "组织名称");
            String organizationNature = normalizeOrganizationNature(command.organizationNature());
            AddressEntity area = requireArea(command.areaId(), organizationNature);
            String username = AdminGuard.normalizeUsername(command.adminUsername());
            if (!PasswordPolicy.isValid(command.temporaryPassword())) {
                throw new BusinessException(AppErrorCode.PASSWORD_POLICY_INVALID);
            }
            if (findEnterpriseByCode(code) != null) {
                throw new BusinessException(AppErrorCode.ENTERPRISE_CODE_EXISTS);
            }
            if (findUserByUsername(username) != null) {
                throw new BusinessException(AppErrorCode.USERNAME_EXISTS);
            }

            long enterpriseId = IdGenerator.nextId();
            OrgEntity enterprise = new OrgEntity();
            enterprise.setId(enterpriseId);
            enterprise.setEnterpriseId(enterpriseId);
            enterprise.setOrgType(AdminConstants.ORG_ENTERPRISE);
            enterprise.setOrganizationNature(organizationNature);
            enterprise.setAreaId(area.getId());
            enterprise.setOrgCode(code);
            enterprise.setOrgName(name);
            enterprise.setContactName(trim(command.contactName()));
            enterprise.setContactPhone(trim(command.contactPhone()));
            enterprise.setAddress(trim(command.address()));
            enterprise.setStatus(AdminConstants.STATUS_ENABLED);
            enterprise.setCreatedBy(operator.getUserId());
            enterprise.setUpdatedBy(operator.getUserId());
            orgMapper.insertSelective(enterprise);

            boolean regulator = AdminConstants.ORGANIZATION_NATURE_REGULATOR.equals(organizationNature);
            String administratorName = regulator ? "行管管理员" : "企业管理员";
            RoleEntity adminRole = createBuiltInRole(
                    enterpriseId, AdminConstants.ROLE_ENTERPRISE_ADMIN, administratorName,
                    administratorName + "内置角色", operator.getUserId());
            List<Long> adminPermissionIds = permissionMapper.selectListByQuery(QueryWrapper.create()
                            .where(PERMISSION.PERMISSION_SCOPE.in("ENTERPRISE", "COMMON")))
                    .stream()
                    .filter(permission -> permission.getPermissionCode().startsWith("admin:"))
                    .map(PermissionEntity::getId)
                    .collect(Collectors.toList());
            insertRolePermissions(adminRole.getId(), adminPermissionIds);

            if (!regulator) {
                RoleEntity studentRole = createBuiltInRole(
                        enterpriseId, AdminConstants.ROLE_STUDENT, "学员", "企业内置学员角色",
                        operator.getUserId());
                List<Long> studentPermissionIds = permissionMapper.selectListByQuery(QueryWrapper.create()
                                .where(PERMISSION.PERMISSION_CODE.likeRight("student:")))
                        .stream()
                        .map(PermissionEntity::getId)
                        .collect(Collectors.toList());
                if (!studentPermissionIds.isEmpty()) {
                    insertRolePermissions(studentRole.getId(), studentPermissionIds);
                }
            }

            UserEntity admin = new UserEntity();
            admin.setId(IdGenerator.nextId());
            admin.setEnterpriseId(enterpriseId);
            admin.setOrgId(enterpriseId);
            admin.setUsername(username);
            admin.setPasswordHash(passwordEncoder.encode(command.temporaryPassword()));
            admin.setDisplayName(AdminGuard.requireText(command.adminDisplayName(), "管理员姓名"));
            admin.setPhone(trim(command.adminPhone()));
            admin.setStatus(AdminConstants.STATUS_ENABLED);
            admin.setLoginVersion(1L);
            admin.setMustChangePassword(true);
            admin.setPlatformAdmin(false);
            admin.setCreatedBy(operator.getUserId());
            admin.setUpdatedBy(operator.getUserId());
            userMapper.insertSelective(admin);
            orgUserMapper.insertSelective(newOrgUser(admin.getId(), enterpriseId));
            userRoleMapper.insertSelective(newUserRole(admin.getId(), adminRole.getId(), enterpriseId));
            cacheService.syncLoginVersion(admin);
            return toViews(Collections.singletonList(requireEnterprise(enterpriseId))).get(0);
        });
    }

    @Override
    public Result<EnterpriseView> update(UpdateEnterpriseCommand command) {
        return executeTransactional(() -> {
            LoginUser operator = AdminGuard.requirePlatformPermission(AdminPermissions.ENTERPRISE_UPDATE);
            OrgEntity enterprise = requireEnterprise(command.id());
            String organizationNature = existingOrganizationNature(enterprise);
            AddressEntity area = requireArea(command.areaId(), organizationNature);
            UpdateWrapper<OrgEntity> update = UpdateWrapper.of(OrgEntity.class)
                    .set(ORG.ORG_NAME, AdminGuard.requireText(command.name(), "组织名称"))
                    .set(ORG.AREA_ID, area.getId())
                    .set(ORG.CONTACT_NAME, trim(command.contactName()))
                    .set(ORG.CONTACT_PHONE, trim(command.contactPhone()))
                    .set(ORG.ADDRESS, trim(command.address()))
                    .set(ORG.UPDATED_BY, operator.getUserId());
            orgMapper.updateByCondition(update.toEntity(), ORG.ID.eq(enterprise.getId()));
            return toViews(Collections.singletonList(requireEnterprise(enterprise.getId()))).get(0);
        });
    }

    @Override
    public Result<?> changeStatus(ChangeStatusCommand command) {
        return executeVoidTransactional(() -> {
            LoginUser operator = AdminGuard.requirePlatformPermission(AdminPermissions.ENTERPRISE_STATUS);
            OrgEntity enterprise = requireEnterprise(command.id());
            String status = AdminGuard.normalizeStatus(command.status());
            if (status.equals(enterprise.getStatus())) {
                cacheService.syncEnterpriseUsers(enterprise.getId());
                return;
            }
            UpdateWrapper<OrgEntity> orgUpdate = UpdateWrapper.of(OrgEntity.class)
                    .set(ORG.STATUS, status)
                    .set(ORG.UPDATED_BY, operator.getUserId());
            orgMapper.updateByCondition(orgUpdate.toEntity(), ORG.ID.eq(enterprise.getId()));
            UpdateWrapper<UserEntity> userUpdate = UpdateWrapper.of(UserEntity.class)
                    .set(USER.LOGIN_VERSION, USER.LOGIN_VERSION.add(1))
                    .set(USER.UPDATED_BY, operator.getUserId());
            userMapper.updateByCondition(
                    userUpdate.toEntity(), USER.ENTERPRISE_ID.eq(enterprise.getId()));
            cacheService.syncEnterpriseUsers(enterprise.getId());
        });
    }

    @Override
    public Result<List<EnterpriseAdministratorView>> listAdministrators(Long enterpriseId) {
        return execute(() -> {
            AdminGuard.requirePlatformPermission(AdminPermissions.ENTERPRISE_VIEW);
            OrgEntity enterprise = requireEnterprise(enterpriseId);
            return userMapper.listEnterpriseAdministrators(enterprise.getId()).stream()
                    .map(this::toAdministratorView)
                    .collect(Collectors.toList());
        });
    }

    @Override
    public Result<?> resetAdministratorPassword(ResetEnterpriseAdministratorPasswordCommand command) {
        return executeVoidTransactional(() -> {
            LoginUser operator = AdminGuard.requirePlatformPermission(AdminPermissions.ENTERPRISE_UPDATE);
            OrgEntity enterprise = requireEnterprise(command.getEnterpriseId());
            UserEntity administrator = requireEnterpriseAdministrator(enterprise.getId(), command.getUserId());
            if (!PasswordPolicy.isValid(command.getTemporaryPassword())) {
                throw new BusinessException(AppErrorCode.PASSWORD_POLICY_INVALID);
            }
            UpdateWrapper<UserEntity> update = UpdateWrapper.of(UserEntity.class)
                    .set(USER.PASSWORD_HASH, passwordEncoder.encode(command.getTemporaryPassword()))
                    .set(USER.MUST_CHANGE_PASSWORD, true)
                    .set(USER.LOGIN_VERSION, USER.LOGIN_VERSION.add(1))
                    .set(USER.UPDATED_BY, operator.getUserId());
            userMapper.updateByCondition(update.toEntity(), USER.ID.eq(administrator.getId()));
            UserEntity changedAdministrator = userMapper.selectOneById(administrator.getId());
            cacheService.syncLoginVersion(changedAdministrator);
            cacheService.invalidateAuthorization(administrator.getId());
        });
    }

    private RoleEntity createBuiltInRole(
            Long enterpriseId,
            String code,
            String name,
            String description,
            Long operatorId) {
        RoleEntity role = new RoleEntity();
        role.setId(IdGenerator.nextId());
        role.setEnterpriseId(enterpriseId);
        role.setRoleCode(code);
        role.setRoleName(name);
        role.setDescription(description);
        role.setStatus(AdminConstants.STATUS_ENABLED);
        role.setBuiltIn(true);
        role.setCreatedBy(operatorId);
        role.setUpdatedBy(operatorId);
        roleMapper.insertSelective(role);
        return role;
    }

    private void insertRolePermissions(Long roleId, List<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return;
        }
        rolePermissionMapper.insertBatch(permissionIds.stream()
                .map(permissionId -> newRolePermission(roleId, permissionId))
                .collect(Collectors.toList()));
    }

    private OrgEntity findEnterpriseByCode(String code) {
        return orgMapper.selectOneByQuery(QueryWrapper.create()
                .where(ORG.ORG_TYPE.eq(AdminConstants.ORG_ENTERPRISE))
                .and(ORG.DELETED_AT.isNull())
                .and(ORG.ORG_CODE.eq(code)));
    }

    private UserEntity findUserByUsername(String username) {
        return userMapper.selectOneByQuery(QueryWrapper.create().where(USER.USERNAME.eq(username)));
    }

    private OrgEntity requireEnterprise(Long id) {
        OrgEntity enterprise = id == null ? null : orgMapper.selectOneByQuery(QueryWrapper.create()
                .where(ORG.ID.eq(id))
                .and(ORG.ORG_TYPE.eq(AdminConstants.ORG_ENTERPRISE))
                .and(ORG.DELETED_AT.isNull()));
        if (enterprise == null) {
            throw new BusinessException(AppErrorCode.ENTERPRISE_NOT_FOUND);
        }
        return enterprise;
    }

    private UserEntity requireEnterpriseAdministrator(Long enterpriseId, Long userId) {
        UserEntity administrator = userId == null ? null : userMapper.selectOneById(userId);
        if (administrator == null) {
            throw new BusinessException(AppErrorCode.USER_NOT_FOUND);
        }
        if (!enterpriseId.equals(administrator.getEnterpriseId())) {
            throw new BusinessException(AppErrorCode.DATA_SCOPE_VIOLATION);
        }
        boolean enterpriseAdministrator = roleMapper.listByUserId(administrator.getId()).stream()
                .anyMatch(role -> enterpriseId.equals(role.getEnterpriseId())
                        && AdminConstants.ROLE_ENTERPRISE_ADMIN.equals(role.getRoleCode()));
        if (!enterpriseAdministrator) {
            throw new BusinessException(AppErrorCode.FORBIDDEN, "只能重置组织管理员密码");
        }
        return administrator;
    }

    /**
     * 批量组装组织与行政区域路径，避免分页列表逐行查询地址。
     */
    private List<EnterpriseView> toViews(List<OrgEntity> entities) {
        Map<Long, List<AddressPathNode>> paths = loadAreaPaths(entities);
        return entities.stream()
                .map(entity -> toView(entity, paths.get(entity.getAreaId())))
                .collect(Collectors.toList());
    }

    /**
     * 将组织实体转换为管理端视图。
     */
    private EnterpriseView toView(OrgEntity entity, List<AddressPathNode> areaPath) {
        List<AddressPathNode> safePath = areaPath == null
                ? Collections.emptyList()
                : areaPath;
        String areaName = safePath.isEmpty() ? null : safePath.get(safePath.size() - 1).name();
        return new EnterpriseView(
                entity.getId(),
                entity.getOrgCode(),
                entity.getOrgName(),
                existingOrganizationNature(entity),
                entity.getAreaId(),
                areaName,
                safePath,
                entity.getContactName(),
                entity.getContactPhone(),
                entity.getAddress(),
                entity.getStatus(),
                entity.getCreatedAt());
    }

    /**
     * 批量加载选中地址及其最多两级上级路径。
     */
    private Map<Long, List<AddressPathNode>> loadAreaPaths(List<OrgEntity> entities) {
        Set<Long> areaIds = entities.stream()
                .map(OrgEntity::getAreaId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (areaIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<AddressEntity> selectedAreas = addressMapper.selectListByQuery(QueryWrapper.create()
                .where(ADDRESS.ID.in(areaIds)));
        Map<Long, AddressEntity> selectedById = selectedAreas.stream()
                .collect(Collectors.toMap(AddressEntity::getId, area -> area));
        Map<String, AddressEntity> areasByCode = new HashMap<String, AddressEntity>();
        selectedAreas.forEach(area -> areasByCode.put(area.getAreaCode(), area));

        Set<String> missingParentCodes = parentCodes(selectedAreas, areasByCode);
        for (int depth = 0; depth < 2 && !missingParentCodes.isEmpty(); depth++) {
            List<AddressEntity> parents = addressMapper.selectListByQuery(QueryWrapper.create()
                    .where(ADDRESS.AREA_CODE.in(missingParentCodes)));
            parents.forEach(area -> areasByCode.put(area.getAreaCode(), area));
            missingParentCodes = parentCodes(parents, areasByCode);
        }

        Map<Long, List<AddressPathNode>> paths = new LinkedHashMap<Long, List<AddressPathNode>>();
        areaIds.forEach(areaId -> paths.put(areaId, buildAreaPath(selectedById.get(areaId), areasByCode)));
        return paths;
    }

    /**
     * 找出尚未加载的非根级父行政代码。
     */
    private Set<String> parentCodes(
            List<AddressEntity> areas,
            Map<String, AddressEntity> loadedAreas) {
        return areas.stream()
                .map(AddressEntity::getParentCode)
                .filter(this::hasText)
                .filter(parentCode -> !"0".equals(parentCode))
                .filter(parentCode -> !loadedAreas.containsKey(parentCode))
                .collect(Collectors.toSet());
    }

    /**
     * 根据已加载的地址映射组装从省到当前节点的路径。
     */
    private List<AddressPathNode> buildAreaPath(
            AddressEntity selectedArea,
            Map<String, AddressEntity> areasByCode) {
        if (selectedArea == null) {
            return Collections.emptyList();
        }
        List<AddressPathNode> reversedPath = new ArrayList<AddressPathNode>();
        Set<String> visitedCodes = new HashSet<String>();
        AddressEntity current = selectedArea;
        while (current != null && reversedPath.size() < 3 && visitedCodes.add(current.getAreaCode())) {
            reversedPath.add(new AddressPathNode(
                    current.getId(), current.getLevel(), current.getAreaCode(), current.getName()));
            current = "0".equals(current.getParentCode())
                    ? null
                    : areasByCode.get(current.getParentCode());
        }
        Collections.reverse(reversedPath);
        return reversedPath;
    }

    /**
     * 校验组织性质及对应的行政区域层级。
     */
    private AddressEntity requireArea(Long areaId, String organizationNature) {
        AddressEntity area = areaId == null ? null : addressMapper.selectOneById(areaId);
        if (area == null || area.getLevel() < 1 || area.getLevel() > 3) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "行政区域不存在或层级不正确");
        }
        if (AdminConstants.ORGANIZATION_NATURE_ENTERPRISE.equals(organizationNature)
                && area.getLevel() != 3) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "企业组织必须选择到区县级");
        }
        return area;
    }

    /**
     * 标准化并校验组织性质。
     */
    private String normalizeOrganizationNature(String value) {
        String nature = AdminGuard.requireText(value, "组织类型").toUpperCase(Locale.ROOT);
        if (!AdminConstants.ORGANIZATION_NATURE_ENTERPRISE.equals(nature)
                && !AdminConstants.ORGANIZATION_NATURE_REGULATOR.equals(nature)) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "组织类型仅支持企业或行管");
        }
        return nature;
    }

    /**
     * 读取根组织性质，兼容迁移前构造的历史对象。
     */
    private String existingOrganizationNature(OrgEntity entity) {
        return hasText(entity.getOrganizationNature())
                ? normalizeOrganizationNature(entity.getOrganizationNature())
                : AdminConstants.ORGANIZATION_NATURE_ENTERPRISE;
    }

    private EnterpriseAdministratorView toAdministratorView(UserEntity entity) {
        return new EnterpriseAdministratorView(
                entity.getId(),
                entity.getUsername(),
                entity.getDisplayName(),
                entity.getPhone(),
                entity.getStatus(),
                entity.isMustChangePassword(),
                entity.getCreatedAt());
    }

    private OrgUserEntity newOrgUser(Long userId, Long enterpriseId) {
        OrgUserEntity relation = new OrgUserEntity();
        relation.setUserId(userId);
        relation.setOrgId(enterpriseId);
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
