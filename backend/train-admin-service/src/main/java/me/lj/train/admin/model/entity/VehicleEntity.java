package me.lj.train.admin.model.entity;

import com.mybatisflex.annotation.Table;

/** 企业车辆基础信息，通过启停保留车辆历史。 */
@Table("train_vehicle")
public class VehicleEntity extends AuditEntity {
    private Long enterpriseId;
    private String plateNumber;
    private String vehicleType;
    private Long orgId;
    private String status;
    private String remark;

    public Long getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(Long value) { enterpriseId = value; }
    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String value) { plateNumber = value; }
    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String value) { vehicleType = value; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long value) { orgId = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getRemark() { return remark; }
    public void setRemark(String value) { remark = value; }
}
