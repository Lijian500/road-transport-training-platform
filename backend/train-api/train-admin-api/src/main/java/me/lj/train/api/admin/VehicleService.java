package me.lj.train.api.admin;

import me.lj.train.api.admin.VehicleModels.*;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.Result;
import java.util.List;

/** 本企业车辆基础管理RPC。 */
public interface VehicleService {
    /** 分页查询车辆。 */
    Result<PageResult<VehicleView>> page(VehicleQuery query);
    /** 新增车辆。 */
    Result<VehicleView> create(SaveVehicleCommand command);
    /** 编辑车辆。 */
    Result<VehicleView> update(SaveVehicleCommand command);
    /** 启停车辆，保留历史数据。 */
    Result<?> changeStatus(Long id, String status);
    /** 列出车辆管理可选部门，不额外要求部门管理权限。 */
    Result<List<VehicleDepartmentView>> departments();
    /** 按关键字分页查找本企业车辆，供人员绑定选用。 */
    Result<PageResult<VehicleOption>> options(int pageNumber, int pageSize, String keyword);
    /** 分页查看车辆绑定的人员。 */
    Result<PageResult<VehicleStudentView>> students(Long id, int pageNumber, int pageSize);
}
