package com.recruit.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.auth.entity.IamUserRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

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
}
