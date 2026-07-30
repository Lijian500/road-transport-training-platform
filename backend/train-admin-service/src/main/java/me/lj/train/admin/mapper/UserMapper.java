package me.lj.train.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import me.lj.train.admin.model.entity.UserEntity;
import org.apache.ibatis.annotations.Param;

/**
 * 用户数据访问。
 */
public interface UserMapper extends BaseMapper<UserEntity> {

    /**
     * 统计企业当前启用的管理员人数。
     */
    int countEnabledEnterpriseAdmins(@Param("enterpriseId") Long enterpriseId);
}
