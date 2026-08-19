package me.lj.train.admin.service;

import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.admin.constant.AdminConstants;
import me.lj.train.admin.constant.AdminPermissions;
import me.lj.train.admin.mapper.OrgMapper;
import me.lj.train.admin.mapper.RoleMapper;
import me.lj.train.admin.mapper.UserMapper;
import me.lj.train.admin.mapper.UserRoleMapper;
import me.lj.train.admin.model.entity.OrgEntity;
import me.lj.train.admin.model.entity.RoleEntity;
import me.lj.train.admin.model.entity.UserEntity;
import me.lj.train.admin.model.entity.UserRoleEntity;
import me.lj.train.api.admin.TrainingParticipantModels.ValidateParticipantsCommand;
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
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingParticipantServiceImplTest {

    @Mock private PlatformTransactionManager transactionManager;
    @Mock private UserMapper userMapper;
    @Mock private RoleMapper roleMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private OrgMapper orgMapper;

    private TrainingParticipantServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TrainingParticipantServiceImpl(
                transactionManager, userMapper, roleMapper, userRoleMapper, orgMapper);
        LoginUser operator = new LoginUser();
        operator.setUserId(10L);
        operator.setEnterpriseId(20L);
        operator.setPermissions(Collections.singletonList(AdminPermissions.PLAN_PUBLISH));
        UserContext.set(operator);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldValidateEnabledStudentAndReturnOrganizationSnapshot() {
        RoleEntity role = new RoleEntity();
        role.setId(30L);
        role.setEnterpriseId(20L);
        role.setRoleCode(AdminConstants.ROLE_STUDENT);
        role.setStatus(AdminConstants.STATUS_ENABLED);
        UserRoleEntity relation = new UserRoleEntity();
        relation.setUserId(40L);
        relation.setRoleId(30L);
        relation.setEnterpriseId(20L);
        UserEntity user = new UserEntity();
        user.setId(40L);
        user.setEnterpriseId(20L);
        user.setOrgId(50L);
        user.setUsername("student");
        user.setDisplayName("张三");
        user.setStatus(AdminConstants.STATUS_ENABLED);
        OrgEntity org = new OrgEntity();
        org.setId(50L);
        org.setEnterpriseId(20L);
        org.setOrgName("安全部");
        when(roleMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(role);
        when(userRoleMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(relation));
        when(userMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(user));
        when(orgMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(org));

        Result<?> result = service.validate(
                new ValidateParticipantsCommand(Collections.singletonList(40L)));

        assertThat(result.isSuccess()).isTrue();
        me.lj.train.api.admin.TrainingParticipantModels.ParticipantView participant =
                (me.lj.train.api.admin.TrainingParticipantModels.ParticipantView)
                        ((java.util.List<?>) result.getData()).get(0);
        assertThat(participant.displayName()).isEqualTo("张三");
        assertThat(participant.orgName()).isEqualTo("安全部");
    }

    @Test
    void shouldRejectUserWithoutStudentRole() {
        RoleEntity role = new RoleEntity();
        role.setId(30L);
        role.setEnterpriseId(20L);
        role.setRoleCode(AdminConstants.ROLE_STUDENT);
        role.setStatus(AdminConstants.STATUS_ENABLED);
        when(roleMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(role);
        when(userRoleMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(userMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        Result<?> result = service.validate(
                new ValidateParticipantsCommand(Collections.singletonList(40L)));

        assertThat(result.getCode()).isEqualTo(AppErrorCode.PLAN_PARTICIPANT_INVALID.getCode());
    }
}
