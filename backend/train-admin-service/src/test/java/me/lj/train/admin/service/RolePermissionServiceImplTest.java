package me.lj.train.admin.service;

import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.admin.constant.AdminConstants;
import me.lj.train.admin.constant.AdminPermissions;
import me.lj.train.admin.mapper.PermissionMapper;
import me.lj.train.admin.mapper.RoleMapper;
import me.lj.train.admin.mapper.RolePermissionMapper;
import me.lj.train.admin.mapper.UserRoleMapper;
import me.lj.train.admin.model.entity.RoleEntity;
import me.lj.train.admin.support.AuthorizationCacheService;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RolePermissionServiceImplTest {

    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private PermissionMapper permissionMapper;
    @Mock
    private RolePermissionMapper rolePermissionMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private AuthorizationCacheService cacheService;

    private RolePermissionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RolePermissionServiceImpl(
                transactionManager,
                roleMapper,
                permissionMapper,
                rolePermissionMapper,
                userRoleMapper,
                cacheService);
        LoginUser operator = new LoginUser();
        operator.setUserId(1L);
        operator.setEnterpriseId(20L);
        operator.setPermissions(Collections.singletonList(AdminPermissions.ROLE_DELETE));
        UserContext.set(operator);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldSoftDeleteUnassignedCustomRoleAndKeepPermissionRelations() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        when(roleMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(customRole());
        when(roleMapper.listPermissionCodesByRoleId(30L)).thenReturn(Collections.emptyList());

        Result<?> result = service.delete(30L);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<RoleEntity> updateCaptor = ArgumentCaptor.forClass(RoleEntity.class);
        verify(roleMapper).updateByCondition(updateCaptor.capture(), any());
        RoleEntity update = updateCaptor.getValue();
        assertThat(update.getStatus()).isEqualTo(AdminConstants.STATUS_DISABLED);
        assertThat(update.getDeletedBy()).isEqualTo(1L);
        assertThat(update.getDeletedAt()).isNotNull();
        assertThat(update.getUpdatedBy()).isEqualTo(1L);
        verify(rolePermissionMapper, never()).deleteByQuery(any(QueryWrapper.class));
        verify(roleMapper, never()).deleteById(30L);
        verify(transactionManager).commit(transactionStatus);
    }

    private RoleEntity customRole() {
        RoleEntity role = new RoleEntity();
        role.setId(30L);
        role.setEnterpriseId(20L);
        role.setRoleCode("CUSTOM_ROLE");
        role.setStatus(AdminConstants.STATUS_ENABLED);
        role.setBuiltIn(false);
        return role;
    }
}
