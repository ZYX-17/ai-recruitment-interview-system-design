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

    /**
     * 校验角色 ID 是否全部存在且启用。
     *
     * @param ids 角色 ID 列表，不能为空
     * @return 命中的启用角色数量
     */
    @Select("<script>SELECT COUNT(1) FROM iam_role WHERE status = 'ACTIVE' AND id IN "
            + "<foreach collection=\"ids\" item=\"id\" open=\"(\" separator=\",\" close=\")\">"
            + "#{id}</foreach></script>")
    long countActiveByIds(@Param("ids") List<Long> ids);

    /**
     * 判断给定角色 ID 中是否包含启用状态的 SUPER_ADMIN。
     *
     * <p>用于分配角色时保护“最后一个超级管理员”：先看角色是否被移除，再查是否还有别人。</p>
     *
     * @param ids 角色 ID 列表，不能为空
     * @return 命中行数
     */
    @Select("<script>SELECT COUNT(1) FROM iam_role WHERE role_code = 'SUPER_ADMIN' "
            + "AND status = 'ACTIVE' AND id IN "
            + "<foreach collection=\"ids\" item=\"id\" open=\"(\" separator=\",\" close=\")\">"
            + "#{id}</foreach></script>")
    long countSuperAdminInIds(@Param("ids") List<Long> ids);

    /**
     * 查用户当前生效角色声明的数据范围。
     *
     * @param userId 用户 ID
     * @return 数据范围列表（ALL/DEPARTMENT/ASSIGNED/SELF）；无角色返回空列表
     */
    @Select("SELECT r.data_scope FROM iam_role r JOIN iam_user_role ur ON ur.role_id = r.id "
            + "WHERE ur.user_id = #{userId} AND r.status = 'ACTIVE'")
    List<String> selectActiveDataScopesByUserId(@Param("userId") Long userId);
}
