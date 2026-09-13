-- 单个可空外键字段保证每人最多一辆车；索引不唯一，允许多人绑定同一车辆。
ALTER TABLE sys_user
    ADD COLUMN vehicle_id BIGINT NULL COMMENT '绑定车辆ID' AFTER org_id,
    ADD KEY idx_user_enterprise_vehicle (enterprise_id, vehicle_id);
