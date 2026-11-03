package com.recruit.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.auth.entity.IamDepartment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 部门 Mapper。
 *
 * <p>{@code ancestors} 是根哨兵 {@code 0} 起始的祖级路径（如 {@code 0,20000}），
 * 新增/移动必须维护，否则 DEPARTMENT 数据范围会漏子树。</p>
 */
public interface IamDepartmentMapper extends BaseMapper<IamDepartment> {

    /**
     * 全部部门（管理端部门树，含停用节点）。
     *
     * @return 部门行，按 sort、id 排序
     */
    @Select("SELECT * FROM iam_department ORDER BY sort, id")
    List<IamDepartment> selectAll();

    /**
     * 自身及全部子孙部门 ID（DEPARTMENT 数据范围使用）。
     *
     * <p>依赖 {@code ancestors} 祖级路径，避免递归 CTE 在低版本 MySQL 不可用。</p>
     *
     * @param deptId 部门 ID
     * @return 自身 + 子孙部门 ID
     */
    @Select("SELECT id FROM iam_department WHERE id = #{deptId} "
            + "OR CONCAT(',', ancestors, ',') LIKE CONCAT('%,', #{deptId}, ',%')")
    List<Long> selectSubtreeIds(@Param("deptId") Long deptId);

    /**
     * 统计直接子部门数量。
     *
     * @param parentId 父部门 ID
     * @return 子部门数
     */
    @Select("SELECT COUNT(1) FROM iam_department WHERE parent_id = #{parentId}")
    long countByParentId(@Param("parentId") Long parentId);

    /**
     * 统计归属该部门的用户数（删除前置校验）。
     *
     * @param deptId 部门 ID
     * @return 用户数
     */
    @Select("SELECT COUNT(1) FROM iam_user WHERE dept_id = #{deptId}")
    long countUsersByDeptId(@Param("deptId") Long deptId);

    /**
     * 同一父级下按名称查重。
     *
     * @param deptName 部门名称
     * @param parentId 父部门 ID；null 表示根级
     * @return 匹配数量
     */
    @Select("<script>SELECT COUNT(1) FROM iam_department WHERE dept_name = #{deptName} AND "
            + "<choose><when test=\"parentId != null\">parent_id = #{parentId}</when>"
            + "<otherwise>parent_id IS NULL</otherwise></choose></script>")
    long countByDeptNameAndParentId(@Param("deptName") String deptName,
                                    @Param("parentId") Long parentId);

    /**
     * 同一父级下按名称查重（排除自身，修改时使用）。
     *
     * @param deptName 部门名称
     * @param parentId 父部门 ID；null 表示根级
     * @param excludeId 排除的部门 ID
     * @return 匹配数量
     */
    @Select("<script>SELECT COUNT(1) FROM iam_department WHERE dept_name = #{deptName} "
            + "AND id != #{excludeId} AND "
            + "<choose><when test=\"parentId != null\">parent_id = #{parentId}</when>"
            + "<otherwise>parent_id IS NULL</otherwise></choose></script>")
    long countByDeptNameAndParentIdExcludingId(@Param("deptName") String deptName,
                                               @Param("parentId") Long parentId,
                                               @Param("excludeId") Long excludeId);

    /**
     * 乐观锁更新部门白名单字段。
     *
     * @return 影响行数；0 表示版本冲突或部门不存在
     */
    @Update("UPDATE iam_department SET dept_name = #{deptName}, parent_id = #{parentId}, "
            + "ancestors = #{ancestors}, sort = #{sort}, status = #{status}, version = version + 1, "
            + "updated_at = CURRENT_TIMESTAMP(3) WHERE id = #{id} AND version = #{version}")
    int updateDept(@Param("id") Long id,
                   @Param("deptName") String deptName,
                   @Param("parentId") Long parentId,
                   @Param("ancestors") String ancestors,
                   @Param("sort") Integer sort,
                   @Param("status") String status,
                   @Param("version") Integer version);

    /**
     * 移动部门后批量重写子孙的祖级路径。
     *
     * <p>子孙原路径 = 旧自身路径 + {@code ,id} + 剩余后缀；把这段前缀替换为新自身路径即可。</p>
     *
     * @param id           被移动的部门 ID
     * @param oldAncestors 移动前的自身祖级路径
     * @param newAncestors 移动后的自身祖级路径
     * @return 影响行数
     */
    @Update("UPDATE iam_department SET ancestors = CONCAT(#{newAncestors}, "
            + "SUBSTRING(ancestors, CHAR_LENGTH(#{oldAncestors}) + 1)), "
            + "version = version + 1, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id != #{id} AND CONCAT(',', ancestors, ',') LIKE CONCAT('%,', #{id}, ',%')")
    int updateDescendantAncestors(@Param("id") Long id,
                                  @Param("oldAncestors") String oldAncestors,
                                  @Param("newAncestors") String newAncestors);

    /**
     * 取自身及全部子孙中最长的祖级路径长度，移动前校验不会超过列宽。
     *
     * @param id 部门 ID
     * @return 最长路径字符数；无记录返回 0
     */
    @Select("SELECT COALESCE(MAX(CHAR_LENGTH(ancestors)), 0) FROM iam_department "
            + "WHERE id = #{id} OR CONCAT(',', ancestors, ',') LIKE CONCAT('%,', #{id}, ',%')")
    int selectMaxAncestorsLength(@Param("id") Long id);
}
