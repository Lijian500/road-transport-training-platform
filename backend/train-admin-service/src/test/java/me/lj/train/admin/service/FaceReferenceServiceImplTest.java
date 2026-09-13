package me.lj.train.admin.service;

import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.admin.constant.AdminPermissions;
import me.lj.train.admin.mapper.UserMapper;
import me.lj.train.admin.model.entity.UserEntity;
import me.lj.train.api.admin.FaceReferenceModels.BindFaceReferenceCommand;
import me.lj.train.api.admin.FaceReferenceModels.FaceReferenceChangeView;
import me.lj.train.api.admin.FaceReferenceModels.FaceReferenceView;
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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FaceReferenceServiceImplTest {

    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;
    @Mock private UserMapper userMapper;

    private FaceReferenceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FaceReferenceServiceImpl(transactionManager, userMapper);
        UserContext.set(operator(Collections.singletonList(AdminPermissions.FACE_CHECK_MANAGE)));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldAllowStudentToReadOwnReferenceWithoutAdminPermission() {
        UserContext.set(operator(Collections.emptyList()));
        UserEntity user = user(10L, 20L);
        user.setFaceReferenceObjectId(100L);
        user.setFaceReferenceUpdatedAt(LocalDateTime.now());
        when(userMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(user);

        Result<FaceReferenceView> result = service.get(10L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().enrolled()).isTrue();
        assertThat(result.getData().storageObjectId()).isEqualTo(100L);
    }

    @Test
    void shouldAllowOwnReferenceWriteButRejectOtherUsersWithoutPermission() {
        UserContext.set(operator(Collections.emptyList()));
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        when(userMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(user(10L, 20L));
        assertThat(service.bind(new BindFaceReferenceCommand(10L, 101L)).isSuccess()).isTrue();
        assertThat(service.bind(new BindFaceReferenceCommand(11L, 101L)).getCode())
                .isEqualTo(AppErrorCode.FORBIDDEN.getCode());
    }

    @Test
    void shouldAllowPlatformAdministratorToMaintainOwnReference() {
        LoginUser platform = operator(Collections.emptyList());
        platform.setEnterpriseId(null);
        platform.setPlatformAdmin(true);
        UserContext.set(platform);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        when(userMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(user(10L, null));
        assertThat(service.bind(new BindFaceReferenceCommand(10L, 101L)).isSuccess()).isTrue();
        assertThat(service.get(10L).isSuccess()).isTrue();
    }

    @Test
    void shouldReturnPreviousObjectWhenReplacingReference() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        UserEntity user = user(11L, 20L);
        user.setFaceReferenceObjectId(100L);
        when(userMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(user);

        Result<FaceReferenceChangeView> result = service.bind(
                new BindFaceReferenceCommand(11L, 101L));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().previousStorageObjectId()).isEqualTo(100L);
        assertThat(result.getData().current().storageObjectId()).isEqualTo(101L);
        verify(userMapper).updateByCondition(any(UserEntity.class), any());
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void shouldRejectCrossEnterpriseReferenceBinding() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        when(userMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(user(11L, 21L));

        Result<FaceReferenceChangeView> result = service.bind(
                new BindFaceReferenceCommand(11L, 101L));

        assertThat(result.getCode()).isEqualTo(AppErrorCode.DATA_SCOPE_VIOLATION.getCode());
        verify(transactionManager).rollback(transactionStatus);
    }

    private LoginUser operator(java.util.List<String> permissions) {
        LoginUser user = new LoginUser();
        user.setUserId(10L);
        user.setEnterpriseId(20L);
        user.setPermissions(permissions);
        return user;
    }

    private UserEntity user(Long id, Long enterpriseId) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEnterpriseId(enterpriseId);
        return user;
    }
}
