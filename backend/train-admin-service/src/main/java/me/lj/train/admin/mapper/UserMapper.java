package me.lj.train.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import me.lj.train.admin.model.entity.UserEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户数据访问。
 */
public interface UserMapper extends BaseMapper<UserEntity> {

    /**
     * 统计根组织当前启用的管理员人数。
     */
    int countEnabledEnterpriseAdmins(@Param("enterpriseId") Long enterpriseId);

    /**
     * 查询根组织所有管理员账号。
     */
    List<UserEntity> listEnterpriseAdministrators(@Param("enterpriseId") Long enterpriseId);
}
