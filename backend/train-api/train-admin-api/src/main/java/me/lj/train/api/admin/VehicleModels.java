package me.lj.train.api.admin;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 车辆基础管理RPC模型，所属企业由服务端上下文决定。 */
public final class VehicleModels {
    private VehicleModels() { }
    /** 车辆分页筛选。 */
    public record VehicleQuery(int pageNumber, int pageSize, String keyword, Long orgId, String status) implements Serializable { }
    /** 新增及编辑共用的车辆信息。 */
    public record SaveVehicleCommand(Long id, String plateNumber, String vehicleType, Long orgId, String remark) implements Serializable { }
    /** 车辆列表与编辑回显。 */
    public record VehicleView(Long id, String plateNumber, String vehicleType, Long orgId,
            String orgName, String status, String remark, LocalDateTime createdAt) implements Serializable { }
    /** 车辆管理可选的本企业部门。 */
    public record VehicleDepartmentView(Long id, String name) implements Serializable { }
    /** 人员编辑中可选的车辆，只暴露必要信息。 */
    public record VehicleOption(Long id, String plateNumber, String status) implements Serializable { }
    /** 车辆绑定人员的最小信息。 */
    public record VehicleStudentView(Long id, String username, String displayName, String orgName,
            String status) implements Serializable { }
}
