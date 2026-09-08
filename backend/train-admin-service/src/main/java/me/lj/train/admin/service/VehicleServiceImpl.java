package me.lj.train.admin.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateWrapper;
import me.lj.train.admin.mapper.OrgMapper;
import me.lj.train.admin.mapper.VehicleMapper;
import me.lj.train.admin.model.entity.OrgEntity;
import me.lj.train.admin.model.entity.VehicleEntity;
import me.lj.train.admin.support.AdminGuard;
import me.lj.train.api.admin.VehicleModels.*;
import me.lj.train.api.admin.VehicleService;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.page.PageRequest;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.core.util.IdGenerator;
import me.lj.train.common.security.context.UserContext;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static me.lj.train.admin.constant.AdminPermissions.*;
import static me.lj.train.admin.model.table.VehicleTableDef.VEHICLE;
import static me.lj.train.admin.model.table.OrgTableDef.ORG;

/** 按企业隔离车辆CRUD，唯一索引处理并发重复车牌。 */
@DubboService(timeout = 5000, retries = 0)
public class VehicleServiceImpl extends AdminServiceSupport implements VehicleService {
    private final VehicleMapper mapper;
    private final OrgMapper orgMapper;

    public VehicleServiceImpl(PlatformTransactionManager transactions, VehicleMapper mapper, OrgMapper orgMapper) {
        super(transactions);
        this.mapper = mapper;
        this.orgMapper = orgMapper;
    }

    /** 数据库分页查询并批量补齐部门名称。 */
    @Override public Result<PageResult<VehicleView>> page(VehicleQuery query) {
        return execute(() -> {
            Long enterpriseId = AdminGuard.requireEnterprisePermission(VEHICLE_VIEW);
            if (query == null) throw new BusinessException(AppErrorCode.PARAM_INVALID);
            String keyword = text(query.keyword(), "关键字", 64, false);
            String status = text(query.status(), "状态", 16, false);
            if (status != null) AdminGuard.normalizeStatus(status);
            if (query.orgId() != null) requireDepartment(query.orgId(), enterpriseId);
            PageRequest request = new PageRequest(query.pageNumber(), query.pageSize());
            Page<VehicleEntity> page = mapper.paginate(request.getPageNumber(), request.getPageSize(), QueryWrapper.create()
                    .where(VEHICLE.ENTERPRISE_ID.eq(enterpriseId))
                    .and(VEHICLE.PLATE_NUMBER.like(keyword).when(keyword != null))
                    .and(VEHICLE.ORG_ID.eq(query.orgId()).when(query.orgId() != null))
                    .and(VEHICLE.STATUS.eq(status).when(status != null)).orderBy(VEHICLE.CREATED_AT.desc(), VEHICLE.ID.desc()));
            List<Long> ids = page.getRecords().stream().map(VehicleEntity::getOrgId).filter(java.util.Objects::nonNull).distinct().toList();
            Map<Long, String> names = ids.isEmpty() ? Map.of() : orgMapper.selectListByQuery(QueryWrapper.create()
                    .where(ORG.ENTERPRISE_ID.eq(enterpriseId)).and(ORG.ID.in(ids))).stream()
                    .collect(Collectors.toMap(OrgEntity::getId, OrgEntity::getOrgName));
            return PageResult.of(page.getRecords().stream().map(row -> toView(row,
                    row.getOrgId() == null ? null : names.get(row.getOrgId()))).toList(), page.getTotalRow(), request);
        });
    }

    /** 新车归属于当前企业，默认启用。 */
    @Override public Result<VehicleView> create(SaveVehicleCommand command) {
        return executeTransactional(() -> save(command, false));
    }

    /** 编辑车牌、类型、部门和备注。 */
    @Override public Result<VehicleView> update(SaveVehicleCommand command) {
        return executeTransactional(() -> save(command, true));
    }

    /** 启停操作不删除车辆。 */
    @Override public Result<?> changeStatus(Long id, String status) {
        return executeVoidTransactional(() -> {
            Long enterpriseId = AdminGuard.requireEnterprisePermission(VEHICLE_STATUS);
            VehicleEntity row = requireVehicle(id, enterpriseId);
            VehicleEntity update = UpdateWrapper.of(VehicleEntity.class).set(VEHICLE.STATUS, AdminGuard.normalizeStatus(status))
                    .set(VEHICLE.UPDATED_BY, UserContext.require().getUserId()).toEntity();
            mapper.updateByCondition(update, VEHICLE.ID.eq(row.getId()).and(VEHICLE.ENTERPRISE_ID.eq(enterpriseId)));
        });
    }

    /** 车辆页面独立获取本企业部门选项。 */
    @Override public Result<List<VehicleDepartmentView>> departments() {
        return execute(() -> {
            Long enterpriseId = AdminGuard.requireEnterpriseAnyPermission(VEHICLE_VIEW, VEHICLE_CREATE, VEHICLE_UPDATE);
            return orgMapper.selectListByQuery(QueryWrapper.create().where(ORG.ENTERPRISE_ID.eq(enterpriseId))
                    .and(ORG.ORG_TYPE.eq("DEPARTMENT")).and(ORG.STATUS.eq("ENABLED")).and(ORG.DELETED_AT.isNull())
                    .orderBy(ORG.SORT_ORDER.asc(), ORG.ID.asc())).stream()
                    .map(row -> new VehicleDepartmentView(row.getId(), row.getOrgName())).toList();
        });
    }

    /** 共享写入校验，空部门和备注可被显式清除。 */
    private VehicleView save(SaveVehicleCommand command, boolean editing) {
        Long enterpriseId = AdminGuard.requireEnterprisePermission(editing ? VEHICLE_UPDATE : VEHICLE_CREATE);
        if (command == null) throw new BusinessException(AppErrorCode.PARAM_INVALID);
        VehicleEntity row = editing ? requireVehicle(command.id(), enterpriseId) : new VehicleEntity();
        String plate = text(command.plateNumber(), "车牌号", 16, true).toUpperCase(Locale.ROOT);
        if (plate.length() < 3 || plate.chars().anyMatch(Character::isWhitespace)) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "车牌号应为3至16位且不能包含空白");
        }
        String type = text(command.vehicleType(), "车辆类型", 64, true);
        String remark = text(command.remark(), "备注", 255, false);
        OrgEntity department = requireDepartment(command.orgId(), enterpriseId);
        Long operatorId = UserContext.require().getUserId();
        try {
            if (editing) {
                mapper.updateByCondition(UpdateWrapper.of(VehicleEntity.class)
                        .set(VEHICLE.PLATE_NUMBER, plate).set(VEHICLE.VEHICLE_TYPE, type).set(VEHICLE.ORG_ID, command.orgId())
                        .set(VEHICLE.REMARK, remark).set(VEHICLE.UPDATED_BY, operatorId).toEntity(),
                        VEHICLE.ID.eq(row.getId()).and(VEHICLE.ENTERPRISE_ID.eq(enterpriseId)));
            } else {
                row.setId(IdGenerator.nextId());
                row.setEnterpriseId(enterpriseId);
                row.setPlateNumber(plate);
                row.setVehicleType(type);
                row.setOrgId(command.orgId());
                row.setStatus("ENABLED");
                row.setRemark(remark);
                row.setCreatedBy(operatorId);
                row.setUpdatedBy(operatorId);
                mapper.insertSelective(row);
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "本企业已存在该车牌号");
        }
        return toView(requireVehicle(row.getId(), enterpriseId), department == null ? null : department.getOrgName());
    }

    /** 读写目标必须属于当前企业。 */
    private VehicleEntity requireVehicle(Long id, Long enterpriseId) {
        VehicleEntity row = id == null ? null : mapper.selectOneByQuery(QueryWrapper.create()
                .where(VEHICLE.ID.eq(id)).and(VEHICLE.ENTERPRISE_ID.eq(enterpriseId)));
        if (row == null) throw new BusinessException(AppErrorCode.RESOURCE_NOT_FOUND, "车辆不存在");
        AdminGuard.checkEnterprise(row.getEnterpriseId(), enterpriseId);
        return row;
    }

    /** 部门仅允许选用本企业未删除且启用的部门。 */
    private OrgEntity requireDepartment(Long id, Long enterpriseId) {
        if (id == null) return null;
        OrgEntity org = orgMapper.selectOneByQuery(QueryWrapper.create().where(ORG.ID.eq(id))
                .and(ORG.ENTERPRISE_ID.eq(enterpriseId)).and(ORG.ORG_TYPE.eq("DEPARTMENT"))
                .and(ORG.STATUS.eq("ENABLED")).and(ORG.DELETED_AT.isNull()));
        if (org == null) throw new BusinessException(AppErrorCode.PARAM_INVALID, "所属部门不可用");
        AdminGuard.checkEnterprise(org.getEnterpriseId(), enterpriseId);
        return org;
    }

    /** RPC同样执行长度校验，避免绕过REST校验。 */
    private String text(String value, String label, int max, boolean required) {
        String result = value == null || value.isBlank() ? null : value.trim();
        if (required && result == null || result != null && result.length() > max) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, label + "不能为空或超过" + max + "个字符");
        }
        return result;
    }

    /** 转换为车辆视图。 */
    private VehicleView toView(VehicleEntity row, String orgName) {
        return new VehicleView(row.getId(), row.getPlateNumber(), row.getVehicleType(), row.getOrgId(),
                orgName, row.getStatus(), row.getRemark(), row.getCreatedAt());
    }
}
