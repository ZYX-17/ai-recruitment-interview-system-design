package com.recruit.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.auth.entity.IamRole;
import com.recruit.auth.mapper.row.AdminRoleRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
     * 角色分页投影，附带绑定用户数。
     *
     * @param keyword 角色编码/角色名关键字，可为 null
     * @param status  状态过滤，可为 null
     * @param limit   每页条数
     * @param offset  偏移量
     * @return 当前页角色
     */
    @Select("<script>"
            + "SELECT r.id, r.role_code, r.role_name, r.data_scope, r.is_system_role, r.status, "
            + "r.remark, r.created_at, r.version, "
            + "(SELECT COUNT(1) FROM iam_user_role ur WHERE ur.role_id = r.id) AS user_count "
            + "FROM iam_role r WHERE 1 = 1 "
            + "<if test=\"keyword != null and keyword != ''\">"
            + " AND (r.role_code LIKE CONCAT('%', #{keyword}, '%') "
            + "OR r.role_name LIKE CONCAT('%', #{keyword}, '%'))"
            + "</if>"
            + "<if test=\"status != null and status != ''\"> AND r.status = #{status}</if>"
            + " ORDER BY r.id LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<AdminRoleRow> selectRolePage(@Param("keyword") String keyword,
                                      @Param("status") String status,
                                      @Param("limit") long limit,
                                      @Param("offset") long offset);

    /**
     * 角色分页总数，过滤条件与 {@link #selectRolePage} 保持一致。
     *
     * @param keyword 角色编码/角色名关键字，可为 null
     * @param status  状态过滤，可为 null
     * @return 总行数
     */
    @Select("<script>"
            + "SELECT COUNT(1) FROM iam_role r WHERE 1 = 1 "
            + "<if test=\"keyword != null and keyword != ''\">"
            + " AND (r.role_code LIKE CONCAT('%', #{keyword}, '%') "
            + "OR r.role_name LIKE CONCAT('%', #{keyword}, '%'))"
            + "</if>"
            + "<if test=\"status != null and status != ''\"> AND r.status = #{status}</if>"
            + "</script>")
    long countRolePage(@Param("keyword") String keyword, @Param("status") String status);

    /**
     * 按 ID 取角色详情投影（含绑定用户数）。
     *
     * @param id 角色 ID
     * @return 投影；不存在返回 null
     */
    @Select("SELECT r.id, r.role_code, r.role_name, r.data_scope, r.is_system_role, r.status, "
            + "r.remark, r.created_at, r.version, "
            + "(SELECT COUNT(1) FROM iam_user_role ur WHERE ur.role_id = r.id) AS user_count "
            + "FROM iam_role r WHERE r.id = #{id}")
    AdminRoleRow selectAdminRoleById(@Param("id") Long id);

    /**
     * 按角色编码查角色（新增前置查重）。
     *
     * @param roleCode 角色编码
     * @return 角色；不存在返回 null
     */
    @Select("SELECT * FROM iam_role WHERE role_code = #{roleCode} LIMIT 1")
    IamRole selectByRoleCode(@Param("roleCode") String roleCode);

    /**
     * 乐观锁更新角色白名单字段。
     *
     * @param id        角色 ID
     * @param roleName  角色名
     * @param dataScope 数据范围
     * @param status    状态
     * @param remark    备注
     * @param version   旧版本号
     * @return 影响行数；0 表示版本冲突或角色不存在
     */
    @Update("UPDATE iam_role SET role_name = #{roleName}, data_scope = #{dataScope}, "
            + "status = #{status}, remark = #{remark}, version = version + 1, "
            + "updated_at = CURRENT_TIMESTAMP(3) WHERE id = #{id} AND version = #{version}")
    int updateRole(@Param("id") Long id,
                   @Param("roleName") String roleName,
                   @Param("dataScope") String dataScope,
                   @Param("status") String status,
                   @Param("remark") String remark,
                   @Param("version") Integer version);

    /**
     * 只递增版本号（分配权限时做乐观锁校验）。
     *
     * @param id      角色 ID
     * @param version 旧版本号
     * @return 影响行数；0 表示版本冲突或角色不存在
     */
    @Update("UPDATE iam_role SET version = version + 1, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND version = #{version}")
    int incrementVersion(@Param("id") Long id, @Param("version") Integer version);

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
