package com.recruit.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.auth.entity.IamRolePermission;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色-权限关联 Mapper。
 *
 * <p>常规单表读写走 {@link BaseMapper}；分配权限按“先清后插”全量替换。</p>
 */
public interface IamRolePermissionMapper extends BaseMapper<IamRolePermission> {

    /**
     * 删除角色的全部权限关联。
     *
     * @param roleId 角色 ID
     * @return 删除行数
     */
    @Delete("DELETE FROM iam_role_permission WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    /**
     * 查角色已分配的权限 ID。
     *
     * @param roleId 角色 ID
     * @return 权限 ID，按升序
     */
    @Select("SELECT permission_id FROM iam_role_permission WHERE role_id = #{roleId} "
            + "ORDER BY permission_id")
    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);
}
