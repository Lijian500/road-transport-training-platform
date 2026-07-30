package me.lj.train.admin.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateWrapper;
import me.lj.train.admin.constant.AdminConstants;
import me.lj.train.admin.constant.AdminPermissions;
import me.lj.train.admin.mapper.OrgMapper;
import me.lj.train.admin.mapper.OrgUserMapper;
import me.lj.train.admin.mapper.PermissionMapper;
import me.lj.train.admin.mapper.RoleMapper;
import me.lj.train.admin.mapper.RolePermissionMapper;
import me.lj.train.admin.mapper.UserMapper;
import me.lj.train.admin.mapper.UserRoleMapper;
import me.lj.train.admin.model.entity.OrgEntity;
import me.lj.train.admin.model.entity.OrgUserEntity;
import me.lj.train.admin.model.entity.PermissionEntity;
import me.lj.train.admin.model.entity.RoleEntity;
import me.lj.train.admin.model.entity.RolePermissionEntity;
import me.lj.train.admin.model.entity.UserEntity;
import me.lj.train.admin.model.entity.UserRoleEntity;
import me.lj.train.admin.support.AdminGuard;
import me.lj.train.admin.support.AuthorizationCacheService;
import me.lj.train.api.admin.AdminModels.ChangeStatusCommand;
import me.lj.train.api.admin.AdminModels.CreateEnterpriseCommand;
import me.lj.train.api.admin.AdminModels.EnterpriseQuery;
import me.lj.train.api.admin.AdminModels.EnterpriseView;
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

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static me.lj.train.admin.model.table.OrgTableDef.ORG;
import static me.lj.train.admin.model.table.PermissionTableDef.PERMISSION;
import static me.lj.train.admin.model.table.UserTableDef.USER;

/**
 * 企业管理RPC实现，直接编排MyBatis-Flex Mapper。
 */
@DubboService(timeout = 5000, retries = 0)
public class EnterpriseServiceImpl extends AdminServiceSupport implements EnterpriseService {

    private final OrgMapper orgMapper;
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
            QueryWrapper wrapper = QueryWrapper.create()
                    .where(ORG.ORG_TYPE.eq(AdminConstants.ORG_ENTERPRISE))
                    .and(ORG.ORG_NAME.like(keyword)
                            .or(ORG.ORG_CODE.like(keyword))
                            .when(hasText(keyword)))
                    .and(ORG.STATUS.eq(status).when(hasText(status)))
                    .orderBy(ORG.CREATED_AT.desc());
            Page<OrgEntity> page = orgMapper.paginate(
                    request.getPageNumber(), request.getPageSize(), wrapper);
            List<EnterpriseView> records = page.getRecords().stream()
                    .map(this::toView)
                    .collect(Collectors.toList());
            return PageResult.of(records, page.getTotalRow(), request);
        });
    }

    @Override
    public Result<EnterpriseView> create(CreateEnterpriseCommand command) {
        return executeTransactional(() -> {
            LoginUser operator = AdminGuard.requirePlatformPermission(AdminPermissions.ENTERPRISE_CREATE);
            String code = AdminGuard.normalizeCode(command.code(), "企业编码");
            String name = AdminGuard.requireText(command.name(), "企业名称");
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
            enterprise.setOrgCode(code);
            enterprise.setOrgName(name);
            enterprise.setContactName(trim(command.contactName()));
            enterprise.setContactPhone(trim(command.contactPhone()));
            enterprise.setAddress(trim(command.address()));
            enterprise.setStatus(AdminConstants.STATUS_ENABLED);
            enterprise.setCreatedBy(operator.getUserId());
            enterprise.setUpdatedBy(operator.getUserId());
            orgMapper.insertSelective(enterprise);

            RoleEntity adminRole = createBuiltInRole(
                    enterpriseId, AdminConstants.ROLE_ENTERPRISE_ADMIN, "企业管理员", "企业内置管理员角色",
                    operator.getUserId());
            List<Long> adminPermissionIds = permissionMapper.selectListByQuery(QueryWrapper.create()
                            .where(PERMISSION.PERMISSION_SCOPE.in("ENTERPRISE", "COMMON")))
                    .stream()
                    .filter(permission -> permission.getPermissionCode().startsWith("admin:"))
                    .map(PermissionEntity::getId)
                    .collect(Collectors.toList());
            insertRolePermissions(adminRole.getId(), adminPermissionIds);

            RoleEntity studentRole = createBuiltInRole(
                    enterpriseId, AdminConstants.ROLE_STUDENT, "学员", "企业内置学员角色",
                    operator.getUserId());
            PermissionEntity studentPermission = permissionMapper.selectOneByQuery(QueryWrapper.create()
                    .where(PERMISSION.PERMISSION_CODE.eq("student:workspace:view")));
            if (studentPermission != null) {
                insertRolePermissions(studentRole.getId(), Collections.singletonList(studentPermission.getId()));
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
            return toView(requireEnterprise(enterpriseId));
        });
    }

    @Override
    public Result<EnterpriseView> update(UpdateEnterpriseCommand command) {
        return executeTransactional(() -> {
            LoginUser operator = AdminGuard.requirePlatformPermission(AdminPermissions.ENTERPRISE_UPDATE);
            OrgEntity enterprise = requireEnterprise(command.id());
            UpdateWrapper<OrgEntity> update = UpdateWrapper.of(OrgEntity.class)
                    .set(ORG.ORG_NAME, AdminGuard.requireText(command.name(), "企业名称"))
                    .set(ORG.CONTACT_NAME, trim(command.contactName()))
                    .set(ORG.CONTACT_PHONE, trim(command.contactPhone()))
                    .set(ORG.ADDRESS, trim(command.address()))
                    .set(ORG.UPDATED_BY, operator.getUserId());
            orgMapper.updateByCondition(update.toEntity(), ORG.ID.eq(enterprise.getId()));
            return toView(requireEnterprise(enterprise.getId()));
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
                .and(ORG.ORG_CODE.eq(code)));
    }

    private UserEntity findUserByUsername(String username) {
        return userMapper.selectOneByQuery(QueryWrapper.create().where(USER.USERNAME.eq(username)));
    }

    private OrgEntity requireEnterprise(Long id) {
        OrgEntity enterprise = id == null ? null : orgMapper.selectOneByQuery(QueryWrapper.create()
                .where(ORG.ID.eq(id))
                .and(ORG.ORG_TYPE.eq(AdminConstants.ORG_ENTERPRISE)));
        if (enterprise == null) {
            throw new BusinessException(AppErrorCode.ENTERPRISE_NOT_FOUND);
        }
        return enterprise;
    }

    private EnterpriseView toView(OrgEntity entity) {
        return new EnterpriseView(
                entity.getId(),
                entity.getOrgCode(),
                entity.getOrgName(),
                entity.getContactName(),
                entity.getContactPhone(),
                entity.getAddress(),
                entity.getStatus(),
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
