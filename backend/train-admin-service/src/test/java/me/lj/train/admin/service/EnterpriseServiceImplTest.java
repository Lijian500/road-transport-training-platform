package me.lj.train.admin.service;

import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.admin.constant.AdminConstants;
import me.lj.train.admin.mapper.OrgMapper;
import me.lj.train.admin.mapper.OrgUserMapper;
import me.lj.train.admin.mapper.PermissionMapper;
import me.lj.train.admin.mapper.RoleMapper;
import me.lj.train.admin.mapper.RolePermissionMapper;
import me.lj.train.admin.mapper.UserMapper;
import me.lj.train.admin.mapper.UserRoleMapper;
import me.lj.train.admin.model.entity.OrgEntity;
import me.lj.train.admin.model.entity.RoleEntity;
import me.lj.train.admin.model.entity.UserEntity;
import me.lj.train.admin.support.AuthorizationCacheService;
import me.lj.train.api.admin.AdminModels.EnterpriseAdministratorView;
import me.lj.train.api.admin.AdminModels.ResetEnterpriseAdministratorPasswordCommand;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
}
