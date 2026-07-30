package me.lj.train.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import me.lj.train.admin.model.entity.RoleEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色及角色权限数据访问。
 */
public interface RoleMapper extends BaseMapper<RoleEntity> {

    /**
     * 查询用户拥有的角色。
     */
    List<RoleEntity> listByUserId(@Param("userId") Long userId);

    /**
     * 查询用户拥有的权限编码。
     */
    List<String> listPermissionCodesByUserId(@Param("userId") Long userId);

    /**
     * 查询角色拥有的权限编码。
     */
    List<String> listPermissionCodesByRoleId(@Param("roleId") Long roleId);

    /**
     * 批量加载角色权限编码，用于角色分配时避免逐角色查询。
     */
    List<String> listPermissionCodesByRoleIds(@Param("roleIds") List<Long> roleIds);
}
