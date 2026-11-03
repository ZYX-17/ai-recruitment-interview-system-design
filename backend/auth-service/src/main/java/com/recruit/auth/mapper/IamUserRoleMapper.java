package com.recruit.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.auth.entity.IamUserRole;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户-角色关联 Mapper。
 *
 * <p>常规单表读写走 {@link BaseMapper}；复杂查询集中在本接口，避免 SQL 散落在 Service。</p>
 */
public interface IamUserRoleMapper extends BaseMapper<IamUserRole> {

    /**
     * 删除用户的全部角色关联（分配角色时先清后插）。
     *
     * @param userId 用户 ID
     * @return 删除行数
     */
    @Delete("DELETE FROM iam_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 统计绑定该角色的用户数（删除角色前置校验）。
     *
     * @param roleId 角色 ID
     * @return 绑定用户数
     */
    @Select("SELECT COUNT(1) FROM iam_user_role WHERE role_id = #{roleId}")
    long countByRoleId(@Param("roleId") Long roleId);

    /**
     * 查绑定该角色的用户 ID（角色权限变更后逐个失效权限缓存）。
     *
     * @param roleId 角色 ID
     * @return 用户 ID 列表
     */
    @Select("SELECT DISTINCT user_id FROM iam_user_role WHERE role_id = #{roleId}")
    List<Long> selectUserIdsByRoleId(@Param("roleId") Long roleId);
}
