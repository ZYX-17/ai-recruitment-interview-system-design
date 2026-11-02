package com.recruit.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.auth.entity.IamRole;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色表 Mapper。
 */
public interface IamRoleMapper extends BaseMapper<IamRole> {

    /**
     * 查用户当前生效的角色码（只返回 ACTIVE 角色）。
     *
     * @param userId 用户 ID
     * @return 角色码列表；无角色返回空列表
     */
    @Select("SELECT r.role_code FROM iam_role r "
            + "JOIN iam_user_role ur ON ur.role_id = r.id "
            + "WHERE ur.user_id = #{userId} AND r.status = 'ACTIVE' "
            + "ORDER BY r.role_code")
    List<String> selectActiveRoleCodesByUserId(@Param("userId") Long userId);
}
