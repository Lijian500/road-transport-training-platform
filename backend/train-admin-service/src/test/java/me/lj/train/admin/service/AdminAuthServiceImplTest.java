package me.lj.train.admin.service;

import com.mybatisflex.core.query.QueryWrapper;
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
import me.lj.train.admin.support.AuthorizationCacheService;
import me.lj.train.api.admin.AdminModels.LoginAccount;
import me.lj.train.api.admin.AdminModels.LoginCommand;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceImplTest {

    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;
    @Mock
    private UserMapper userMapper;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private OrgMapper orgMapper;
    @Mock
    private PermissionMapper permissionMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthorizationCacheService cacheService;

    private AdminAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminAuthServiceImpl(
                transactionManager,
                userMapper,
                roleMapper,
                orgMapper,
                permissionMapper,
                userRoleMapper,
                passwordEncoder,
                cacheService);
    }

    @Test
    void shouldReturnRpcResultWithRolesAndPermissionsAfterSuccessfulLogin() {
        UserEntity user = enabledUser();
        OrgEntity enterprise = enabledEnterprise();
        RoleEntity role = enabledAdminRole();
        when(userMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("Password1", user.getPasswordHash())).thenReturn(true);
        when(orgMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(enterprise);
        when(roleMapper.listByUserId(10L)).thenReturn(Collections.singletonList(role));
        when(roleMapper.listPermissionCodesByUserId(10L))
                .thenReturn(Collections.singletonList("admin:user:view"));

        Result<LoginAccount> result = service.authenticate(new LoginCommand("admin", "Password1"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getCode()).isEqualTo(AppErrorCode.SUCCESS.getCode());
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().enterpriseName()).isEqualTo("示例企业");
        assertThat(result.getData().roles()).containsExactly(AdminConstants.ROLE_ENTERPRISE_ADMIN);
        assertThat(result.getData().permissions()).containsExactly("admin:user:view");
        verify(userMapper).selectOneByQuery(any(QueryWrapper.class));
        verify(passwordEncoder).matches("Password1", "hash");
        verify(cacheService).syncLoginVersion(user);
        verify(roleMapper).listByUserId(10L);
        verify(roleMapper).listPermissionCodesByUserId(10L);
    }

    @Test
    void shouldReturnAccountDisabledErrorForDisabledAccount() {
        UserEntity user = enabledUser();
        user.setStatus(AdminConstants.STATUS_DISABLED);
        when(userMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("Password1", user.getPasswordHash())).thenReturn(true);

        Result<LoginAccount> result = service.authenticate(new LoginCommand("admin", "Password1"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(AppErrorCode.ACCOUNT_DISABLED.getCode());
        assertThat(result.getData()).isNull();
        verify(cacheService, never()).syncLoginVersion(any(UserEntity.class));
        verifyNoInteractions(roleMapper, orgMapper, userRoleMapper);
    }

    @Test
    void shouldCreateSuperAdminAndCommitTransaction() {
        mockTransaction();
        mockBootstrapLock();
        when(passwordEncoder.encode("Password1")).thenReturn("encoded-password");

        service.bootstrapSuperAdmin("super-admin", "Password1", "平台管理员");

        ArgumentCaptor<RoleEntity> roleCaptor = ArgumentCaptor.forClass(RoleEntity.class);
        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        ArgumentCaptor<UserRoleEntity> relationCaptor = ArgumentCaptor.forClass(UserRoleEntity.class);
        verify(userMapper, times(2)).selectOneByQuery(any(QueryWrapper.class));
        verify(permissionMapper).selectOneByQuery(any(QueryWrapper.class));
        verify(roleMapper).selectOneByQuery(any(QueryWrapper.class));
        verify(roleMapper).insertSelective(roleCaptor.capture());
        verify(userMapper).insertSelective(userCaptor.capture());
        verify(userRoleMapper).insertSelective(relationCaptor.capture());
        verify(transactionManager).commit(transactionStatus);
        verify(transactionManager, never()).rollback(transactionStatus);

        RoleEntity role = roleCaptor.getValue();
        UserEntity user = userCaptor.getValue();
        UserRoleEntity relation = relationCaptor.getValue();
        assertThat(role.getId()).isNotNull();
        assertThat(role.getRoleCode()).isEqualTo(AdminConstants.ROLE_SUPER_ADMIN);
        assertThat(role.isBuiltIn()).isTrue();
        assertThat(user.getId()).isNotNull();
        assertThat(user.getUsername()).isEqualTo("super-admin");
        assertThat(user.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(user.isPlatformAdmin()).isTrue();
        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(user.getCreatedBy()).isEqualTo(user.getId());
        assertThat(relation.getUserId()).isEqualTo(user.getId());
        assertThat(relation.getRoleId()).isEqualTo(role.getId());
        verify(cacheService).syncLoginVersion(user);
    }

    @Test
    void shouldKeepExistingSameSuperAdminIdempotently() {
        mockTransaction();
        mockBootstrapLock();
        UserEntity existing = enabledUser();
        existing.setEnterpriseId(null);
        existing.setUsername("super-admin");
        existing.setPlatformAdmin(true);
        when(userMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(existing);

        service.bootstrapSuperAdmin("super-admin", "Password1", "平台管理员");
        service.bootstrapSuperAdmin("super-admin", "Password1", "平台管理员");

        verify(userMapper, times(2)).selectOneByQuery(any(QueryWrapper.class));
        verify(permissionMapper, times(2)).selectOneByQuery(any(QueryWrapper.class));
        verify(cacheService, times(2)).syncLoginVersion(existing);
        verify(transactionManager, times(2)).commit(transactionStatus);
        verify(transactionManager, never()).rollback(transactionStatus);
        verify(userMapper, never()).insertSelective(any(UserEntity.class));
        verifyNoInteractions(roleMapper, userRoleMapper, passwordEncoder);
    }

    @Test
    void shouldRollbackAndExposeInvalidBootstrapConfiguration() {
        mockTransaction();

        assertThatThrownBy(() -> service.bootstrapSuperAdmin("", "Password1", "平台管理员"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(AppErrorCode.PARAM_INVALID);

        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(transactionStatus);
        verifyNoInteractions(
                userMapper, roleMapper, permissionMapper, userRoleMapper, passwordEncoder, cacheService);
    }

    @Test
    void shouldRollbackAndExposeInvalidBootstrapPassword() {
        mockTransaction();

        assertThatThrownBy(() -> service.bootstrapSuperAdmin("super-admin", "weak", "平台管理员"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(AppErrorCode.PASSWORD_POLICY_INVALID);

        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(transactionStatus);
        verifyNoInteractions(
                userMapper, roleMapper, permissionMapper, userRoleMapper, passwordEncoder, cacheService);
    }

    @Test
    void shouldRollbackAndExposeMapperFailureDuringBootstrap() {
        mockTransaction();
        mockBootstrapLock();
        IllegalStateException mapperFailure = new IllegalStateException("模拟关联关系写入失败");
        when(passwordEncoder.encode("Password1")).thenReturn("encoded-password");
        doThrow(mapperFailure)
                .when(userRoleMapper)
                .insertSelective(any(UserRoleEntity.class));

        assertThatThrownBy(() ->
                service.bootstrapSuperAdmin("super-admin", "Password1", "平台管理员"))
                .isSameAs(mapperFailure);

        verify(roleMapper).insertSelective(any(RoleEntity.class));
        verify(userMapper).insertSelective(any(UserEntity.class));
        verify(userRoleMapper).insertSelective(any(UserRoleEntity.class));
        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(transactionStatus);
        verify(cacheService, never()).syncLoginVersion(any(UserEntity.class));
    }

    private void mockTransaction() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
    }

    private void mockBootstrapLock() {
        PermissionEntity permission = new PermissionEntity();
        permission.setId(100L);
        when(permissionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(permission);
    }

    private UserEntity enabledUser() {
        UserEntity user = new UserEntity();
        user.setId(10L);
        user.setEnterpriseId(20L);
        user.setUsername("admin");
        user.setPasswordHash("hash");
        user.setDisplayName("管理员");
        user.setStatus(AdminConstants.STATUS_ENABLED);
        user.setLoginVersion(1L);
        return user;
    }

    private OrgEntity enabledEnterprise() {
        OrgEntity enterprise = new OrgEntity();
        enterprise.setId(20L);
        enterprise.setEnterpriseId(20L);
        enterprise.setOrgName("示例企业");
        enterprise.setStatus(AdminConstants.STATUS_ENABLED);
        return enterprise;
    }

    private RoleEntity enabledAdminRole() {
        RoleEntity role = new RoleEntity();
        role.setId(30L);
        role.setEnterpriseId(20L);
        role.setRoleCode(AdminConstants.ROLE_ENTERPRISE_ADMIN);
        role.setStatus(AdminConstants.STATUS_ENABLED);
        return role;
    }
}
