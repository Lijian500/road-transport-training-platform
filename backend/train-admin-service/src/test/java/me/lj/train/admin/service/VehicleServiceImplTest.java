package me.lj.train.admin.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.admin.mapper.OrgMapper;
import me.lj.train.admin.mapper.UserMapper;
import me.lj.train.admin.mapper.VehicleMapper;
import me.lj.train.admin.model.entity.VehicleEntity;
import me.lj.train.api.admin.VehicleModels.*;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 车辆权限、企业隔离、并发唯一性和无部门查询测试。 */
@ExtendWith(MockitoExtension.class)
class VehicleServiceImplTest {
    @Mock private PlatformTransactionManager transactions;
    @Mock private TransactionStatus transaction;
    @Mock private VehicleMapper vehicles;
    @Mock private OrgMapper orgs;
    @Mock private UserMapper users;
    private VehicleServiceImpl service;

    /** 设置企业车辆管理员上下文。 */
    @BeforeEach void setUp() {
        service = new VehicleServiceImpl(transactions, vehicles, orgs, users);
        LoginUser user = new LoginUser(); user.setEnterpriseId(20L); user.setUserId(10L);
        user.setPermissions(List.of("admin:vehicle:view", "admin:vehicle:create", "admin:vehicle:update", "admin:vehicle:status"));
        UserContext.set(user);
    }
    /** 清理线程上下文。 */
    @AfterEach void tearDown() { UserContext.clear(); }

    @Test void shouldListVehicleWithoutDepartment() {
        VehicleEntity row = vehicle();
        when(vehicles.paginate(anyInt(), anyInt(), any(QueryWrapper.class))).thenReturn(new Page<>(List.of(row), 1, 10, 1));
        var result = service.page(new VehicleQuery(1, 10, null, null, null));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getRecords().get(0).orgName()).isNull();
        verifyNoInteractions(orgs);
    }

    @Test void shouldRejectMissingPermissionBeforeQuerying() {
        UserContext.require().setPermissions(List.of());
        assertThat(service.page(new VehicleQuery(1, 10, null, null, null)).getCode()).isEqualTo(AppErrorCode.FORBIDDEN.getCode());
        verifyNoInteractions(vehicles, orgs);
    }

    @Test void shouldRejectCrossEnterpriseStatusChange() {
        beginTransaction();
        VehicleEntity foreign = vehicle(); foreign.setEnterpriseId(21L);
        when(vehicles.selectOneByQuery(any(QueryWrapper.class))).thenReturn(foreign);
        assertThat(service.changeStatus(1L, "DISABLED").getCode()).isEqualTo(AppErrorCode.DATA_SCOPE_VIOLATION.getCode());
        verify(vehicles, never()).updateByCondition(any(VehicleEntity.class), any());
    }

    @Test void shouldRejectForeignOrDeletedDepartment() {
        beginTransaction();
        when(orgs.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
        var result = service.create(new SaveVehicleCommand(null, "沪A12345", "货车", 30L, null));
        assertThat(result.getCode()).isEqualTo(AppErrorCode.PARAM_INVALID.getCode());
        verifyNoInteractions(vehicles);
    }

    @Test void shouldConvertConcurrentPlateConflictAndRollback() {
        beginTransaction();
        when(vehicles.insertSelective(any(VehicleEntity.class))).thenThrow(new DuplicateKeyException("duplicate"));
        var result = service.create(new SaveVehicleCommand(null, "沪A12345", "货车", null, null));
        assertThat(result.getCode()).isEqualTo(AppErrorCode.PARAM_INVALID.getCode());
        assertThat(result.getMessage()).contains("车牌号");
        verify(transactions).rollback(transaction);
    }

    /** 模拟写入事务。 */
    private void beginTransaction() { when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(transaction); }

    /** 普通与新能源车牌长度、结构均执行后端校验。 */
    @ParameterizedTest
    @ValueSource(strings = {"川A1234", "川A1234567", "AA12345", "川112345", "川A12 45", "川A1234!"})
    void shouldRejectInvalidPlate(String plate) {
        beginTransaction();
        assertThat(service.create(new SaveVehicleCommand(null, plate, "货车", null, null)).getCode())
                .isEqualTo(AppErrorCode.PARAM_INVALID.getCode());
        verify(vehicles, never()).insertSelective(any(VehicleEntity.class));
    }

    /** 两种合法长度均可新增，输入字母统一大写。 */
    @ParameterizedTest
    @ValueSource(strings = {"川a12345", "川ad12345"})
    void shouldAcceptRegularAndNewEnergyPlate(String plate) {
        beginTransaction();
        when(vehicles.selectOneByQuery(any(QueryWrapper.class))).thenReturn(vehicle());
        assertThat(service.create(new SaveVehicleCommand(null, plate, "货车", null, null)).isSuccess()).isTrue();
        verify(vehicles).insertSelective(argThat(row -> row.getPlateNumber().equals(plate.toUpperCase(java.util.Locale.ROOT))));
    }

    /** 查看车辆绑定人员同样要求车辆归属当前企业。 */
    @Test void shouldRejectForeignVehicleStudents() {
        VehicleEntity foreign = vehicle();
        foreign.setEnterpriseId(21L);
        when(vehicles.selectOneByQuery(any(QueryWrapper.class))).thenReturn(foreign);
        assertThat(service.students(1L, 1, 10).getCode()).isEqualTo(AppErrorCode.DATA_SCOPE_VIOLATION.getCode());
        verifyNoInteractions(users);
    }
    /** 构造未分配部门的车辆。 */
    private VehicleEntity vehicle() {
        VehicleEntity row = new VehicleEntity(); row.setId(1L); row.setEnterpriseId(20L);
        row.setPlateNumber("沪A12345"); row.setVehicleType("货车"); row.setStatus("ENABLED");
        return row;
    }
}
