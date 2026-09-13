package me.lj.train.admin.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateWrapper;
import org.mockito.ArgumentCaptor;
import me.lj.train.admin.constant.AdminConstants;
import me.lj.train.admin.constant.AdminPermissions;
import me.lj.train.admin.mapper.OrgMapper;
import me.lj.train.admin.mapper.OrgUserMapper;
import me.lj.train.admin.mapper.RoleMapper;
import me.lj.train.admin.mapper.UserMapper;
import me.lj.train.admin.mapper.VehicleMapper;
import me.lj.train.admin.mapper.UserRoleMapper;
import me.lj.train.admin.model.entity.OrgEntity;
import me.lj.train.admin.model.entity.RoleEntity;
import me.lj.train.admin.model.entity.UserEntity;
import me.lj.train.admin.model.entity.VehicleEntity;
import me.lj.train.api.admin.AdminModels.UpdateUserCommand;
import me.lj.train.admin.support.AuthorizationCacheService;
import me.lj.train.api.admin.AdminModels.AssignRolesCommand;
import me.lj.train.api.admin.AdminModels.CreateUserCommand;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;
    @Mock
    private UserMapper userMapper;
    @Mock
    private OrgMapper orgMapper;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private OrgUserMapper orgUserMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthorizationCacheService cacheService;

    @Mock
    private VehicleMapper vehicleMapper;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        service = new UserServiceImpl(
                transactionManager,
                userMapper,
                orgMapper,
                roleMapper,
                userRoleMapper,
                orgUserMapper,
                passwordEncoder,
                cacheService, vehicleMapper);
        LoginUser operator = new LoginUser();
        operator.setUserId(1L);
        operator.setEnterpriseId(20L);
        operator.setPermissions(Collections.singletonList(AdminPermissions.USER_ASSIGN_ROLE));
        UserContext.set(operator);
    }

    @Test
    void shouldUpdateOnlyCurrentProfileWithoutManagementPermission() {
        UserContext.require().setPermissions(Collections.emptyList());
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setDisplayName("新姓名");
        when(userMapper.selectOneById(1L)).thenReturn(user);
        assertThat(service.updateProfile("新姓名", "13800138000").isSuccess()).isTrue();
        verify(cacheService).invalidateAuthorization(1L);
        verify(userMapper).updateByCondition(any(UserEntity.class), any());
        verifyNoInteractions(orgMapper, orgUserMapper, roleMapper, vehicleMapper);
    }

    @Test
    void shouldRejectInvalidProfilePhoneBeforeWriting() {
        assertThat(service.updateProfile("姓名", "123").getCode()).isEqualTo(AppErrorCode.PARAM_INVALID.getCode());
        verifyNoInteractions(userMapper);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldReturnForbiddenAndRollbackWhenAssigningElevatedRole() {
        UserEntity target = enabledTargetUser();
        RoleEntity elevatedRole = enabledElevatedRole();
        when(userMapper.selectOneById(10L)).thenReturn(target);
        when(roleMapper.listPermissionCodesByUserId(10L)).thenReturn(Collections.emptyList());
        when(roleMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(elevatedRole));
        when(roleMapper.listPermissionCodesByRoleIds(Collections.singletonList(30L)))
                .thenReturn(Collections.singletonList(AdminPermissions.ROLE_DELETE));

        Result<?> result = service.assignRoles(
                new AssignRolesCommand(10L, Collections.singletonList(30L)));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(AppErrorCode.FORBIDDEN.getCode());
        assertThat(result.getData()).isNull();
        verify(userMapper).selectOneById(10L);
        verify(roleMapper).listPermissionCodesByUserId(10L);
        verify(roleMapper).selectListByQuery(any(QueryWrapper.class));
        verify(roleMapper).listPermissionCodesByRoleIds(Collections.singletonList(30L));
        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(transactionStatus);
        verify(userRoleMapper, never()).deleteByQuery(any(QueryWrapper.class));
        verifyNoInteractions(orgMapper, orgUserMapper, passwordEncoder, cacheService);
    }

    @Test
    void shouldRejectCrossEnterpriseRoleAssignment() {
        UserEntity target = enabledTargetUser();
        target.setEnterpriseId(21L);
        when(userMapper.selectOneById(10L)).thenReturn(target);

        Result<?> result = service.assignRoles(
                new AssignRolesCommand(10L, Collections.singletonList(30L)));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(AppErrorCode.DATA_SCOPE_VIOLATION.getCode());
        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(transactionStatus);
        verifyNoInteractions(roleMapper, userRoleMapper, orgMapper, orgUserMapper, passwordEncoder, cacheService);
    }

    @Test
    void shouldProtectLastAdministratorAfterLockingEnterprise() {
        UserEntity target = enabledTargetUser();
        RoleEntity adminRole = enabledElevatedRole();
        adminRole.setRoleCode(AdminConstants.ROLE_ENTERPRISE_ADMIN);
        RoleEntity studentRole = enabledElevatedRole();
        studentRole.setId(31L);
        studentRole.setRoleCode(AdminConstants.ROLE_STUDENT);
        OrgEntity enterprise = new OrgEntity();
        enterprise.setId(20L);
        enterprise.setEnterpriseId(20L);
        enterprise.setOrgType(AdminConstants.ORG_ENTERPRISE);
        when(userMapper.selectOneById(10L)).thenReturn(target);
        when(roleMapper.listPermissionCodesByUserId(10L)).thenReturn(Collections.emptyList());
        when(roleMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(studentRole));
        when(roleMapper.listPermissionCodesByRoleIds(Collections.singletonList(31L)))
                .thenReturn(Collections.emptyList());
        when(roleMapper.listByUserId(10L)).thenReturn(Collections.singletonList(adminRole));
        when(orgMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(enterprise);
        when(userMapper.countEnabledEnterpriseAdmins(20L)).thenReturn(1);

        Result<?> result = service.assignRoles(
                new AssignRolesCommand(10L, Collections.singletonList(31L)));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(AppErrorCode.LAST_ADMIN_PROTECTED.getCode());
        verify(orgMapper).selectOneByQuery(any(QueryWrapper.class));
        verify(userMapper).countEnabledEnterpriseAdmins(20L);
        verify(transactionManager).rollback(transactionStatus);
        verify(userRoleMapper, never()).deleteByQuery(any(QueryWrapper.class));
    }

    @Test
    void shouldRequireExplicitRoleWhenOrganizationHasNoStudentRole() {
        LoginUser operator = UserContext.require();
        operator.setPermissions(Collections.singletonList(AdminPermissions.USER_CREATE));
        OrgEntity root = new OrgEntity();
        root.setId(20L);
        root.setEnterpriseId(20L);
        root.setOrgType(AdminConstants.ORG_ENTERPRISE);
        when(userMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
        when(orgMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(root);
        when(roleMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);

        Result<?> result = service.create(new CreateUserCommand(
                "operator", "操作员", null, 20L, "Password1", Collections.emptyList(), null));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(AppErrorCode.PARAM_INVALID.getCode());
        verify(userMapper, never()).insertSelective(any(UserEntity.class));
        verify(transactionManager).rollback(transactionStatus);
    }

    /** 清空车牌必须写入null，而不是忽略字段导致无法解绑。 */
    @Test
    void shouldClearVehicleBinding() {
        prepareProfileUpdate(10L);
        Result<?> result = service.update(new UpdateUserCommand(10L, "学员", null, 30L, null));
        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<UserEntity> update = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).updateByCondition(update.capture(), any());
        assertThat(((UpdateWrapper<?>) update.getValue()).getUpdates()).containsEntry("vehicle_id", null);
        verifyNoInteractions(vehicleMapper);
    }

    /** 同一车辆可以被不同人员使用，不对车辆ID施加唯一绑定限制。 */
    @Test
    void shouldAllowMultipleUsersToBindSameVehicle() {
        prepareProfileUpdate(10L);
        VehicleEntity vehicle = bindingVehicle(20L, "ENABLED");
        when(vehicleMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(vehicle);
        assertThat(service.update(new UpdateUserCommand(10L, "学员甲", null, 30L, 50L)).isSuccess()).isTrue();
        prepareProfileUpdate(11L);
        assertThat(service.update(new UpdateUserCommand(11L, "学员乙", null, 30L, 50L)).isSuccess()).isTrue();
        ArgumentCaptor<UserEntity> update = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper, org.mockito.Mockito.times(2)).updateByCondition(update.capture(), any());
        update.getAllValues().forEach(row ->
                assertThat(((UpdateWrapper<?>) row).getUpdates()).containsEntry("vehicle_id", 50L));
    }

    /** 拒绝跨企业车辆，防止修改请求中的ID绕过可选列表。 */
    @Test
    void shouldRejectForeignVehicleBinding() {
        prepareProfileUpdate(10L);
        when(vehicleMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(bindingVehicle(21L, "ENABLED"));
        assertThat(service.update(new UpdateUserCommand(10L, "学员", null, 30L, 50L)).getCode())
                .isEqualTo(AppErrorCode.PARAM_INVALID.getCode());
        verify(userMapper, never()).updateByCondition(any(UserEntity.class), any());
    }

    /** 新绑定不能使用停用车辆。 */
    @Test
    void shouldRejectDisabledVehicleBinding() {
        prepareProfileUpdate(10L);
        when(vehicleMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(bindingVehicle(20L, "DISABLED"));
        assertThat(service.update(new UpdateUserCommand(10L, "学员", null, 30L, 50L)).getCode())
                .isEqualTo(AppErrorCode.PARAM_INVALID.getCode());
        verify(userMapper, never()).updateByCondition(any(UserEntity.class), any());
    }

    /** 准备人员编辑的最小业务依赖。 */
    private void prepareProfileUpdate(Long id) {
        UserContext.require().setPermissions(Collections.singletonList(AdminPermissions.USER_UPDATE));
        UserEntity user = enabledTargetUser();
        user.setId(id);
        user.setOrgId(30L);
        when(userMapper.selectOneById(id)).thenReturn(user);
        OrgEntity org = new OrgEntity();
        org.setId(30L);
        org.setEnterpriseId(20L);
        when(orgMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(org);
    }

    /** 构造指定归属和状态的绑定候选车辆。 */
    private VehicleEntity bindingVehicle(Long enterpriseId, String status) {
        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setId(50L);
        vehicle.setEnterpriseId(enterpriseId);
        vehicle.setStatus(status);
        return vehicle;
    }

    private UserEntity enabledTargetUser() {
        UserEntity user = new UserEntity();
        user.setId(10L);
        user.setEnterpriseId(20L);
        user.setStatus(AdminConstants.STATUS_ENABLED);
        return user;
    }

    private RoleEntity enabledElevatedRole() {
        RoleEntity role = new RoleEntity();
        role.setId(30L);
        role.setEnterpriseId(20L);
        role.setRoleCode("ELEVATED");
        role.setStatus(AdminConstants.STATUS_ENABLED);
        return role;
    }
}
