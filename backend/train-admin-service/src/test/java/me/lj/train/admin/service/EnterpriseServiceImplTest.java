package me.lj.train.admin.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.admin.constant.AdminConstants;
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
import me.lj.train.admin.model.entity.PermissionEntity;
import me.lj.train.admin.model.entity.RoleEntity;
import me.lj.train.admin.model.entity.RolePermissionEntity;
import me.lj.train.admin.model.entity.UserEntity;
import me.lj.train.admin.support.AuthorizationCacheService;
import me.lj.train.api.admin.AdminModels.CreateEnterpriseCommand;
import me.lj.train.api.admin.AdminModels.EnterpriseAdministratorView;
import me.lj.train.api.admin.AdminModels.EnterpriseQuery;
import me.lj.train.api.admin.AdminModels.EnterpriseView;
import me.lj.train.api.admin.AdminModels.ResetEnterpriseAdministratorPasswordCommand;
import me.lj.train.api.admin.AdminModels.UpdateEnterpriseCommand;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnterpriseServiceImplTest {

    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;
    @Mock
    private OrgMapper orgMapper;
    @Mock
    private AddressMapper addressMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private PermissionMapper permissionMapper;
    @Mock
    private RolePermissionMapper rolePermissionMapper;
    @Mock
    private OrgUserMapper orgUserMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthorizationCacheService cacheService;

    private EnterpriseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EnterpriseServiceImpl(
                transactionManager,
                orgMapper,
                addressMapper,
                userMapper,
                roleMapper,
                permissionMapper,
                rolePermissionMapper,
                orgUserMapper,
                userRoleMapper,
                passwordEncoder,
                cacheService);
        LoginUser operator = new LoginUser();
        operator.setUserId(1L);
        operator.setPlatformAdmin(true);
        operator.setPermissions(Collections.singletonList("*"));
        UserContext.set(operator);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldListEnterpriseAdministratorsForPlatformAdministrator() {
        OrgEntity enterprise = enterprise();
        UserEntity administrator = administrator();
        when(orgMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(enterprise);
        when(userMapper.listEnterpriseAdministrators(20L))
                .thenReturn(Collections.singletonList(administrator));

        Result<List<EnterpriseAdministratorView>> result = service.listAdministrators(20L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).singleElement().satisfies(view -> {
            assertThat(view.getId()).isEqualTo(10L);
            assertThat(view.getUsername()).isEqualTo("enterprise-admin");
            assertThat(view.getDisplayName()).isEqualTo("企业管理员");
        });
        verify(userMapper).listEnterpriseAdministrators(20L);
    }

    @Test
    void shouldResetEnterpriseAdministratorPasswordAndInvalidateSession() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        OrgEntity enterprise = enterprise();
        UserEntity administrator = administrator();
        UserEntity changedAdministrator = administrator();
        changedAdministrator.setLoginVersion(2L);
        RoleEntity administratorRole = administratorRole();
        when(orgMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(enterprise);
        when(userMapper.selectOneById(10L)).thenReturn(administrator, changedAdministrator);
        when(roleMapper.listByUserId(10L)).thenReturn(Collections.singletonList(administratorRole));
        when(passwordEncoder.encode("Password2")).thenReturn("encoded-password");

        Result<?> result = service.resetAdministratorPassword(
                new ResetEnterpriseAdministratorPasswordCommand(20L, 10L, "Password2"));

        assertThat(result.isSuccess()).isTrue();
        verify(passwordEncoder).encode("Password2");
        verify(userMapper).updateByCondition(any(UserEntity.class), any());
        verify(cacheService).syncLoginVersion(changedAdministrator);
        verify(cacheService).invalidateAuthorization(10L);
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void shouldRejectPasswordResetForNonAdministrator() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        when(orgMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(enterprise());
        when(userMapper.selectOneById(10L)).thenReturn(administrator());
        when(roleMapper.listByUserId(10L)).thenReturn(Collections.emptyList());

        Result<?> result = service.resetAdministratorPassword(
                new ResetEnterpriseAdministratorPasswordCommand(20L, 10L, "Password2"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(AppErrorCode.FORBIDDEN.getCode());
        verify(userMapper, never()).updateByCondition(any(UserEntity.class), any());
        verify(transactionManager).rollback(transactionStatus);
        verifyNoInteractions(passwordEncoder, cacheService);
    }

    @Test
    void shouldRejectEnterpriseWhenAreaIsNotDistrict() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        when(addressMapper.selectOneById(100L)).thenReturn(area(100L, 2, "110100000000"));

        Result<?> result = service.create(new CreateEnterpriseCommand(
                "ENT-001", "示例企业", AdminConstants.ORGANIZATION_NATURE_ENTERPRISE, 100L,
                null, null, null, "admin", "管理员", null, "Password1"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(AppErrorCode.PARAM_INVALID.getCode());
        verify(orgMapper, never()).insertSelective(any(OrgEntity.class));
        verify(transactionManager).rollback(transactionStatus);
    }

    @Test
    void shouldRejectMissingOrIllegalArea() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        when(addressMapper.selectOneById(999L)).thenReturn(null);

        Result<?> result = service.create(new CreateEnterpriseCommand(
                "ENT-001", "示例企业", AdminConstants.ORGANIZATION_NATURE_ENTERPRISE, 999L,
                null, null, null, "admin", "管理员", null, "Password1"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(AppErrorCode.PARAM_INVALID.getCode());
        verify(orgMapper, never()).insertSelective(any(OrgEntity.class));
        verify(transactionManager).rollback(transactionStatus);
    }

    @Test
    void shouldRejectUnknownOrganizationNature() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);

        Result<?> result = service.create(new CreateEnterpriseCommand(
                "ORG-001", "未知组织", "UNKNOWN", 100L,
                null, null, null, "admin", "管理员", null, "Password1"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(AppErrorCode.PARAM_INVALID.getCode());
        verifyNoInteractions(addressMapper);
        verify(transactionManager).rollback(transactionStatus);
    }

    @Test
    void shouldCreateRegulatorWithoutStudentRole() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        when(addressMapper.selectOneById(100L)).thenReturn(area(100L, 1, "110000000000"));
        when(passwordEncoder.encode("Password1")).thenReturn("encoded-password");
        when(permissionMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        OrgEntity regulator = enterprise();
        regulator.setOrganizationNature(AdminConstants.ORGANIZATION_NATURE_REGULATOR);
        regulator.setAreaId(100L);
        when(orgMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null, regulator);
        when(addressMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(area(100L, 1, "110000000000")));

        Result<?> result = service.create(new CreateEnterpriseCommand(
                "REG-001", "示例行管", AdminConstants.ORGANIZATION_NATURE_REGULATOR, 100L,
                null, null, null, "reg-admin", "行管管理员", null, "Password1"));

        assertThat(result.isSuccess()).isTrue();
        verify(roleMapper, times(1)).insertSelective(any(RoleEntity.class));
        verify(orgMapper).insertSelective(any(OrgEntity.class));
        verify(userMapper).insertSelective(any(UserEntity.class));
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void shouldCreateEnterpriseWithAdministratorAndStudentRoles() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        AddressEntity district = area(102L, 3, "110101000000");
        when(addressMapper.selectOneById(102L)).thenReturn(district);
        when(passwordEncoder.encode("Password1")).thenReturn("encoded-password");
        when(permissionMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        when(permissionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
        OrgEntity enterprise = enterprise();
        enterprise.setOrganizationNature(AdminConstants.ORGANIZATION_NATURE_ENTERPRISE);
        enterprise.setAreaId(102L);
        when(orgMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null, enterprise);
        when(addressMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(district));

        Result<?> result = service.create(new CreateEnterpriseCommand(
                "ENT-001", "示例企业", AdminConstants.ORGANIZATION_NATURE_ENTERPRISE, 102L,
                null, null, null, "ent-admin", "企业管理员", null, "Password1"));

        assertThat(result.isSuccess()).isTrue();
        verify(roleMapper, times(2)).insertSelective(any(RoleEntity.class));
        verify(userMapper).insertSelective(any(UserEntity.class));
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void shouldGrantNewEnterpriseAdministratorAllAdminPermissionsIncludingCourse() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        AddressEntity district = area(102L, 3, "110101000000");
        when(addressMapper.selectOneById(102L)).thenReturn(district);
        when(passwordEncoder.encode("Password1")).thenReturn("encoded-password");
        PermissionEntity courseView = permission(800L, "admin:course:view");
        PermissionEntity coursewareManage = permission(805L, "admin:courseware:manage");
        PermissionEntity studentWorkspace = permission(700L, "student:workspace:view");
        when(permissionMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Arrays.asList(courseView, coursewareManage, studentWorkspace));
        when(permissionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
        OrgEntity enterprise = enterprise();
        enterprise.setOrganizationNature(AdminConstants.ORGANIZATION_NATURE_ENTERPRISE);
        enterprise.setAreaId(102L);
        when(orgMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null, enterprise);
        when(addressMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(district));

        Result<?> result = service.create(new CreateEnterpriseCommand(
                "ENT-COURSE", "课程企业", AdminConstants.ORGANIZATION_NATURE_ENTERPRISE, 102L,
                null, null, null, "course-admin", "课程管理员", null, "Password1"));

        assertThat(result.isSuccess()).isTrue();
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<List<RolePermissionEntity>> captor =
                ArgumentCaptor.forClass((Class) List.class);
        verify(rolePermissionMapper).insertBatch(captor.capture());
        assertThat(captor.getValue())
                .extracting(RolePermissionEntity::getPermissionId)
                .containsExactlyInAnyOrder(800L, 805L);
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void shouldAllowRegulatorToSelectProvinceCityOrDistrict() {
        for (int level = 1; level <= 3; level++) {
            when(addressMapper.selectOneById((long) level))
                    .thenReturn(area((long) level, level, "CODE-" + level));

            Result<?> result = service.create(new CreateEnterpriseCommand(
                    "REG-" + level, "行管" + level,
                    AdminConstants.ORGANIZATION_NATURE_REGULATOR, (long) level,
                    null, null, null, "reg-" + level, "行管管理员", null, "bad"));

            assertThat(result.getCode()).as("level=%s", level)
                    .isEqualTo(AppErrorCode.PASSWORD_POLICY_INVALID.getCode());
        }
        verify(addressMapper, times(3)).selectOneById(any(Long.class));
    }

    @Test
    void shouldKeepOrganizationNatureAndAllowAreaChangeOnUpdate() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        OrgEntity regulator = enterprise();
        regulator.setOrganizationNature(AdminConstants.ORGANIZATION_NATURE_REGULATOR);
        regulator.setAreaId(100L);
        AddressEntity city = area(101L, 2, "110100000000");
        when(orgMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(regulator, regulator);
        when(addressMapper.selectOneById(101L)).thenReturn(city);
        when(addressMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(city), Collections.emptyList());

        Result<?> result = service.update(new UpdateEnterpriseCommand(
                20L, "更新后的行管", 101L, null, null, null));

        assertThat(result.isSuccess()).isTrue();
        verify(orgMapper).updateByCondition(any(OrgEntity.class), any());
        verify(addressMapper).selectOneById(101L);
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void shouldRequireHistoricalEnterpriseToCompleteDistrictOnUpdate() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        OrgEntity historicalEnterprise = enterprise();
        historicalEnterprise.setOrganizationNature(null);
        historicalEnterprise.setAreaId(null);
        when(orgMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(historicalEnterprise);
        when(addressMapper.selectOneById(101L))
                .thenReturn(area(101L, 2, "110100000000"));

        Result<?> result = service.update(new UpdateEnterpriseCommand(
                20L, "历史企业", 101L, null, null, null));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(AppErrorCode.PARAM_INVALID.getCode());
        verify(orgMapper, never()).updateByCondition(any(OrgEntity.class), any());
        verify(transactionManager).rollback(transactionStatus);
    }

    @Test
    void shouldAssembleAreaPathsInBatchesForOrganizationList() {
        OrgEntity first = enterprise();
        first.setId(20L);
        first.setAreaId(102L);
        first.setOrganizationNature(AdminConstants.ORGANIZATION_NATURE_ENTERPRISE);
        OrgEntity second = enterprise();
        second.setId(21L);
        second.setAreaId(202L);
        second.setOrganizationNature(AdminConstants.ORGANIZATION_NATURE_ENTERPRISE);
        Page<OrgEntity> page = new Page<OrgEntity>(Arrays.asList(first, second), 1, 10, 2);
        when(orgMapper.paginate(anyInt(), anyInt(), any(QueryWrapper.class)))
                .thenReturn(page);

        AddressEntity firstDistrict = area(102L, 3, "110101000000");
        firstDistrict.setParentCode("110100000000");
        AddressEntity secondDistrict = area(202L, 3, "120101000000");
        secondDistrict.setParentCode("120100000000");
        AddressEntity firstCity = area(101L, 2, "110100000000");
        firstCity.setParentCode("110000000000");
        AddressEntity secondCity = area(201L, 2, "120100000000");
        secondCity.setParentCode("120000000000");
        AddressEntity firstProvince = area(100L, 1, "110000000000");
        AddressEntity secondProvince = area(200L, 1, "120000000000");
        when(addressMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(
                Arrays.asList(firstDistrict, secondDistrict),
                Arrays.asList(firstCity, secondCity),
                Arrays.asList(firstProvince, secondProvince));

        Result<PageResult<EnterpriseView>> result = service.page(
                new EnterpriseQuery(1, 10, null, null,
                        AdminConstants.ORGANIZATION_NATURE_ENTERPRISE));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getRecords()).hasSize(2)
                .allSatisfy(view -> assertThat(view.areaPath()).hasSize(3));
        verify(addressMapper, times(3)).selectListByQuery(any(QueryWrapper.class));
    }

    @Test
    void shouldApplyOrganizationNatureFilterToListQuery() {
        Page<OrgEntity> page = new Page<OrgEntity>(Collections.emptyList(), 1, 10, 0);
        when(orgMapper.paginate(anyInt(), anyInt(), any(QueryWrapper.class))).thenReturn(page);

        Result<PageResult<EnterpriseView>> result = service.page(
                new EnterpriseQuery(1, 10, null, null,
                        AdminConstants.ORGANIZATION_NATURE_REGULATOR));

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<QueryWrapper> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(orgMapper).paginate(anyInt(), anyInt(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().toSQL())
                .contains("`organization_nature` = 'REGULATOR'");
        verifyNoInteractions(addressMapper);
    }

    private OrgEntity enterprise() {
        OrgEntity enterprise = new OrgEntity();
        enterprise.setId(20L);
        enterprise.setEnterpriseId(20L);
        enterprise.setOrgType(AdminConstants.ORG_ENTERPRISE);
        return enterprise;
    }

    private UserEntity administrator() {
        UserEntity administrator = new UserEntity();
        administrator.setId(10L);
        administrator.setEnterpriseId(20L);
        administrator.setUsername("enterprise-admin");
        administrator.setDisplayName("企业管理员");
        administrator.setStatus(AdminConstants.STATUS_ENABLED);
        administrator.setLoginVersion(1L);
        return administrator;
    }

    private RoleEntity administratorRole() {
        RoleEntity role = new RoleEntity();
        role.setEnterpriseId(20L);
        role.setRoleCode(AdminConstants.ROLE_ENTERPRISE_ADMIN);
        return role;
    }

    private AddressEntity area(Long id, int level, String areaCode) {
        AddressEntity area = new AddressEntity();
        area.setId(id);
        area.setLevel(level);
        area.setAreaCode(areaCode);
        area.setParentCode("0");
        area.setName("测试区域");
        return area;
    }

    private PermissionEntity permission(Long id, String code) {
        PermissionEntity permission = new PermissionEntity();
        permission.setId(id);
        permission.setPermissionCode(code);
        return permission;
    }
}
