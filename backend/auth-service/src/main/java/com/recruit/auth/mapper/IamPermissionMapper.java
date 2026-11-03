package com.recruit.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.auth.entity.IamPermission;
import com.recruit.auth.mapper.row.PermissionBriefRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    /**
     * 取启用权限的最小投影（ID/类型/父节点），用于父级完整性校验。
     *
     * @param ids 权限 ID 列表，不能为空
     * @return 权限投影
     */
    @Select("<script>SELECT id, type, parent_id FROM iam_permission "
            + "WHERE status = 'ACTIVE' AND id IN "
            + "<foreach collection=\"ids\" item=\"id\" open=\"(\" separator=\",\" close=\")\">"
            + "#{id}</foreach></script>")
    List<PermissionBriefRow> selectActiveByIds(@Param("ids") List<Long> ids);

    /**
     * 全部权限（管理端权限树，含停用节点）。
     *
     * @return 权限行，按 sort、id 排序
     */
    @Select("SELECT * FROM iam_permission ORDER BY sort, id")
    List<IamPermission> selectAll();

    /**
     * 全部启用的权限码（SUPER_ADMIN 全量授权）。
     *
     * @return 权限码列表
     */
    @Select("SELECT permission_code FROM iam_permission WHERE status = 'ACTIVE' ORDER BY permission_code")
    List<String> selectAllActivePermissionCodes();

    /**
     * 全部启用的 MENU（SUPER_ADMIN 动态菜单）。
     *
     * @return 菜单行，按 sort、id 排序
     */
    @Select("SELECT * FROM iam_permission WHERE status = 'ACTIVE' AND type = 'MENU' ORDER BY sort, id")
    List<IamPermission> selectActiveMenus();

    /**
     * 当前用户经角色可见的启用 MENU。
     *
     * @param userId 用户 ID
     * @return 菜单行，按 sort、id 排序
     */
    @Select("SELECT DISTINCT p.* FROM iam_permission p "
            + "JOIN iam_role_permission rp ON rp.permission_id = p.id "
            + "JOIN iam_role r ON r.id = rp.role_id AND r.status = 'ACTIVE' "
            + "JOIN iam_user_role ur ON ur.role_id = r.id "
            + "WHERE ur.user_id = #{userId} AND p.status = 'ACTIVE' AND p.type = 'MENU' "
            + "ORDER BY p.sort, p.id")
    List<IamPermission> selectActiveMenusByUserId(@Param("userId") Long userId);

    /**
     * 按权限码查权限（新增前置查重）。
     *
     * @param permissionCode 权限码
     * @return 权限；不存在返回 null
     */
    @Select("SELECT * FROM iam_permission WHERE permission_code = #{permissionCode} LIMIT 1")
    IamPermission selectByCode(@Param("permissionCode") String permissionCode);

    /**
     * 统计直接子节点数量（停用/删除前置校验）。
     *
     * @param parentId 父权限 ID
     * @return 子节点数
     */
    @Select("SELECT COUNT(1) FROM iam_permission WHERE parent_id = #{parentId}")
    long countByParentId(@Param("parentId") Long parentId);

    /**
     * 统计被角色引用的次数（删除前置校验）。
     *
     * @param permissionId 权限 ID
     * @return 引用数
     */
    @Select("SELECT COUNT(1) FROM iam_role_permission WHERE permission_id = #{permissionId}")
    long countRoleReferences(@Param("permissionId") Long permissionId);

    /**
     * 乐观锁更新权限白名单字段（权限码与类型不可改）。
     *
     * @return 影响行数；0 表示版本冲突或权限不存在
     */
    @Update("UPDATE iam_permission SET name = #{name}, parent_id = #{parentId}, "
            + "route_path = #{routePath}, component = #{component}, icon = #{icon}, "
            + "api_pattern = #{apiPattern}, sort = #{sort}, status = #{status}, version = version + 1, "
            + "updated_at = CURRENT_TIMESTAMP(3) WHERE id = #{id} AND version = #{version}")
    int updatePermission(@Param("id") Long id,
                         @Param("name") String name,
                         @Param("parentId") Long parentId,
                         @Param("routePath") String routePath,
                         @Param("component") String component,
                         @Param("icon") String icon,
                         @Param("apiPattern") String apiPattern,
                         @Param("sort") Integer sort,
                         @Param("status") String status,
                         @Param("version") Integer version);
}
