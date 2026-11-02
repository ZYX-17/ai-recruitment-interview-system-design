package com.recruit.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.auth.entity.IamPermission;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限表 Mapper。
 */
public interface IamPermissionMapper extends BaseMapper<IamPermission> {

    /**
     * 查用户经角色获得的权限码（角色与权限都必须是 ACTIVE）。
     *
     * @param userId 用户 ID
     * @return 去重后的权限码列表
     */
    @Select("SELECT DISTINCT p.permission_code FROM iam_permission p "
            + "JOIN iam_role_permission rp ON rp.permission_id = p.id "
            + "JOIN iam_role r ON r.id = rp.role_id AND r.status = 'ACTIVE' "
            + "JOIN iam_user_role ur ON ur.role_id = r.id "
            + "WHERE ur.user_id = #{userId} AND p.status = 'ACTIVE' "
            + "ORDER BY p.permission_code")
    List<String> selectActivePermissionCodesByUserId(@Param("userId") Long userId);
}
