package me.lj.train.admin.service;

import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.admin.constant.AdminConstants;
import me.lj.train.admin.constant.AdminPermissions;
import me.lj.train.admin.mapper.OrgMapper;
import me.lj.train.admin.mapper.UserMapper;
import me.lj.train.admin.model.entity.OrgEntity;
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
class OrgServiceImplTest {

    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;
    @Mock
    private OrgMapper orgMapper;
    @Mock
    private UserMapper userMapper;

    private OrgServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrgServiceImpl(transactionManager, orgMapper, userMapper);
        LoginUser operator = new LoginUser();
        operator.setUserId(1L);
        operator.setEnterpriseId(20L);
        operator.setPermissions(Collections.singletonList(AdminPermissions.ORG_DELETE));
        UserContext.set(operator);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldSoftDeleteEmptyDepartment() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        when(orgMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(department());

        Result<?> result = service.delete(30L);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<OrgEntity> updateCaptor = ArgumentCaptor.forClass(OrgEntity.class);
        verify(orgMapper).updateByCondition(updateCaptor.capture(), any());
        OrgEntity update = updateCaptor.getValue();
        assertThat(update.getStatus()).isEqualTo(AdminConstants.STATUS_DISABLED);
        assertThat(update.getDeletedBy()).isEqualTo(1L);
        assertThat(update.getDeletedAt()).isNotNull();
        assertThat(update.getUpdatedBy()).isEqualTo(1L);
        verify(orgMapper, never()).deleteById(30L);
        verify(transactionManager).commit(transactionStatus);
    }

    private OrgEntity department() {
        OrgEntity department = new OrgEntity();
        department.setId(30L);
        department.setEnterpriseId(20L);
        department.setOrgType(AdminConstants.ORG_DEPARTMENT);
        department.setStatus(AdminConstants.STATUS_ENABLED);
        return department;
    }
}
