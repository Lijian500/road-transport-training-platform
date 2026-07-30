package me.lj.train.admin.service;

import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.admin.constant.AdminConstants;
import me.lj.train.admin.constant.AdminPermissions;
import me.lj.train.admin.mapper.OrgMapper;
import me.lj.train.admin.mapper.OrgUserMapper;
import me.lj.train.admin.mapper.RoleMapper;
import me.lj.train.admin.mapper.UserMapper;
import me.lj.train.admin.mapper.UserRoleMapper;
import me.lj.train.admin.model.entity.OrgEntity;
import me.lj.train.admin.model.entity.RoleEntity;
import me.lj.train.admin.model.entity.UserEntity;
import me.lj.train.admin.support.AuthorizationCacheService;
import me.lj.train.api.admin.AdminModels.AssignRolesCommand;
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
                cacheService);
        LoginUser operator = new LoginUser();
        operator.setUserId(1L);
        operator.setEnterpriseId(20L);
        operator.setPermissions(Collections.singletonList(AdminPermissions.USER_ASSIGN_ROLE));
        UserContext.set(operator);
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
