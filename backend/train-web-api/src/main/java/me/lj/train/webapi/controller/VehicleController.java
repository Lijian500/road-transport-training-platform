package me.lj.train.webapi.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import me.lj.train.api.admin.VehicleModels.*;
import me.lj.train.api.admin.VehicleService;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.Result;
import me.lj.train.webapi.security.RequirePermission;
import me.lj.train.webapi.support.RpcResultSupport;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 企业车辆管理REST入口。 */
@RestController
@RequestMapping("/api/admin/vehicles")
public class VehicleController {
    @DubboReference(check = false, timeout = 5000, retries = 0)
    private VehicleService service;

    /** 人员新增和编辑独立查询可绑定的车辆。 */
    @GetMapping("/options")
    @RequirePermission({"admin:user:create", "admin:user:update"})
    public Result<PageResult<VehicleOption>> options(@RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String keyword) {
        return Result.ok(RpcResultSupport.unwrap(service.options(pageNumber, pageSize, keyword)));
    }

    /** 查看本企业车辆的绑定人员。 */
    @GetMapping("/{id}/students")
    @RequirePermission("admin:vehicle:view")
    public Result<PageResult<VehicleStudentView>> students(@PathVariable Long id,
            @RequestParam(defaultValue = "1") int pageNumber, @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(RpcResultSupport.unwrap(service.students(id, pageNumber, pageSize)));
    }

    /** 分页查询本企业车辆。 */
    @GetMapping
    @RequirePermission("admin:vehicle:view")
    public Result<PageResult<VehicleView>> page(@RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long orgId, @RequestParam(required = false) String status) {
        return Result.ok(RpcResultSupport.unwrap(service.page(new VehicleQuery(pageNumber, pageSize, keyword, orgId, status))));
    }

    /** 车辆管理专用部门选项。 */
    @GetMapping("/departments")
    @RequirePermission({"admin:vehicle:view", "admin:vehicle:create", "admin:vehicle:update"})
    public Result<List<VehicleDepartmentView>> departments() {
        return Result.ok(RpcResultSupport.unwrap(service.departments()));
    }

    /** 新增本企业车辆。 */
    @PostMapping
    @RequirePermission("admin:vehicle:create")
    public Result<VehicleView> create(@Valid @RequestBody VehicleRequest request) {
        return Result.ok(RpcResultSupport.unwrap(service.create(request.toCommand(null))));
    }

    /** 编辑车辆信息，企业字段不可由客户端指定。 */
    @PutMapping("/{id}")
    @RequirePermission("admin:vehicle:update")
    public Result<VehicleView> update(@PathVariable Long id, @Valid @RequestBody VehicleRequest request) {
        return Result.ok(RpcResultSupport.unwrap(service.update(request.toCommand(id))));
    }

    /** 启停车辆。 */
    @PutMapping("/{id}/status")
    @RequirePermission("admin:vehicle:status")
    public Result<?> status(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        RpcResultSupport.ensureSuccess(service.changeStatus(id, request.status()));
        return Result.ok();
    }

    /** 车辆编辑请求。 */
    public record VehicleRequest(@NotBlank @Size(min = 7, max = 8, message = "车牌号须为7或8位") String plateNumber,
            @NotBlank @Size(max = 64) String vehicleType, Long orgId, @Size(max = 255) String remark) {
        /** 转为RPC写入契约。 */
        SaveVehicleCommand toCommand(Long id) { return new SaveVehicleCommand(id, plateNumber, vehicleType, orgId, remark); }
    }
    /** 启停请求。 */
    public record StatusRequest(@NotBlank String status) { }
}
