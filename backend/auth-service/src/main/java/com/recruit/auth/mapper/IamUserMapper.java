package com.recruit.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.auth.entity.IamUser;
import com.recruit.auth.mapper.row.AdminUserRow;
import com.recruit.auth.mapper.row.UserRoleRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 用户表 Mapper。
 *
 * <p>{@code username} 有唯一索引，登录查询最多命中一行。</p>
 */
public interface IamUserMapper extends BaseMapper<IamUser> {

    /**
     * 按登录名查用户。
     *
     * @param username 登录名
     * @return 用户；不存在返回 null
     */
    @Select("SELECT * FROM iam_user WHERE username = #{username} LIMIT 1")
    IamUser selectByUsername(@Param("username") String username);

    /**
     * 令牌版本 +1（登出全部/改密/禁用/踢线）。
     *
     * <p>用 {@code token_version = token_version + 1} 由数据库原子递增，避免读改写丢失更新。</p>
     *
     * @param id 用户 ID
     * @return 影响行数；0 表示用户不存在
     */
    @Update("UPDATE iam_user SET token_version = token_version + 1, "
            + "updated_at = CURRENT_TIMESTAMP(3) WHERE id = #{id}")
    int incrementTokenVersion(@Param("id") Long id);

    /**
     * 乐观锁递增业务版本（分配角色等只改关联表的写操作使用）。
     *
     * @param id      用户 ID
     * @param version 旧版本号
     * @return 影响行数；0 表示版本冲突或用户不存在
     */
    @Update("UPDATE iam_user SET version = version + 1, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND version = #{version}")
    int incrementVersion(@Param("id") Long id, @Param("version") Integer version);

    /**
     * 更新密码哈希（只在改密时调用，不整行覆盖）。
     *
     * @param id           用户 ID
     * @param passwordHash BCrypt 哈希
     * @return 影响行数；0 表示用户不存在
     */
    @Update("UPDATE iam_user SET password_hash = #{passwordHash}, "
            + "updated_at = CURRENT_TIMESTAMP(3) WHERE id = #{id}")
    int updatePasswordHash(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    /**
     * 统计登录名是否已存在（新增用户前置校验）。
     *
     * @param username 登录名
     * @return 命中行数
     */
    @Select("SELECT COUNT(1) FROM iam_user WHERE username = #{username}")
    long countByUsername(@Param("username") String username);

    /**
     * 统计手机号哈希是否已被占用。
     *
     * @param phoneHash 手机号 SHA-256
     * @return 命中行数
     */
    @Select("SELECT COUNT(1) FROM iam_user WHERE phone_hash = #{phoneHash}")
    long countByPhoneHash(@Param("phoneHash") String phoneHash);

    /**
     * 管理员列表/详情投影：分页取用户，并带出部门名。
     *
     * <p>数据范围由参数控制：{@code scopeRootDeptId} 非空时只返回该部门及子部门，
     * {@code onlyUserId} 非空时只返回本人（SELF 范围）。</p>
     *
     * @param keyword         关键字，可为 null
     * @param status          状态，可为 null
     * @param deptId          部门过滤，可为 null
     * @param scopeRootDeptId 数据范围根部门，可为 null
     * @param onlyUserId      只查本人时传当前用户 ID，否则 null
     * @param limit           每页条数
     * @param offset          偏移量
     * @return 当前页投影
     */
    @Select("<script>"
            + "SELECT u.id, u.username, u.display_name, u.user_type, u.status, u.phone_enc, "
            + "u.dept_id, u.created_at, u.version, d.dept_name "
            + "FROM iam_user u LEFT JOIN iam_department d ON d.id = u.dept_id "
            + "WHERE 1 = 1 "
            + "<if test=\"keyword != null and keyword != ''\">"
            + " AND (u.username LIKE CONCAT('%', #{keyword}, '%') "
            + "OR u.display_name LIKE CONCAT('%', #{keyword}, '%'))"
            + "</if>"
            + "<if test=\"status != null and status != ''\"> AND u.status = #{status}</if>"
            + "<if test=\"deptId != null\"> AND u.dept_id = #{deptId}</if>"
            + "<if test=\"scopeRootDeptId != null\">"
            + " AND (u.dept_id = #{scopeRootDeptId} OR u.dept_id IN ("
            + "SELECT id FROM iam_department WHERE CONCAT(',', ancestors, ',') "
            + "LIKE CONCAT('%,', #{scopeRootDeptId}, ',%')))"
            + "</if>"
            + "<if test=\"onlyUserId != null\"> AND u.id = #{onlyUserId}</if>"
            + " ORDER BY u.id DESC LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<AdminUserRow> selectAdminPage(@Param("keyword") String keyword,
                                       @Param("status") String status,
                                       @Param("deptId") Long deptId,
                                       @Param("scopeRootDeptId") Long scopeRootDeptId,
                                       @Param("onlyUserId") Long onlyUserId,
                                       @Param("limit") long limit,
                                       @Param("offset") long offset);

    /**
     * 管理员列表总数，过滤条件与 {@link #selectAdminPage} 保持一致。
     *
     * @param keyword         关键字，可为 null
     * @param status          状态，可为 null
     * @param deptId          部门过滤，可为 null
     * @param scopeRootDeptId 数据范围根部门，可为 null
     * @param onlyUserId      只查本人时传当前用户 ID，否则 null
     * @return 总行数
     */
    @Select("<script>"
            + "SELECT COUNT(1) FROM iam_user u WHERE 1 = 1 "
            + "<if test=\"keyword != null and keyword != ''\">"
            + " AND (u.username LIKE CONCAT('%', #{keyword}, '%') "
            + "OR u.display_name LIKE CONCAT('%', #{keyword}, '%'))"
            + "</if>"
            + "<if test=\"status != null and status != ''\"> AND u.status = #{status}</if>"
            + "<if test=\"deptId != null\"> AND u.dept_id = #{deptId}</if>"
            + "<if test=\"scopeRootDeptId != null\">"
            + " AND (u.dept_id = #{scopeRootDeptId} OR u.dept_id IN ("
            + "SELECT id FROM iam_department WHERE CONCAT(',', ancestors, ',') "
            + "LIKE CONCAT('%,', #{scopeRootDeptId}, ',%')))"
            + "</if>"
            + "<if test=\"onlyUserId != null\"> AND u.id = #{onlyUserId}</if>"
            + "</script>")
    long countAdminPage(@Param("keyword") String keyword,
                        @Param("status") String status,
                        @Param("deptId") Long deptId,
                        @Param("scopeRootDeptId") Long scopeRootDeptId,
                        @Param("onlyUserId") Long onlyUserId);

    /**
     * 按 ID 取用户详情投影（含部门名）。
     *
     * @param id 用户 ID
     * @return 投影；不存在返回 null
     */
    @Select("SELECT u.id, u.username, u.display_name, u.user_type, u.status, u.phone_enc, "
            + "u.dept_id, u.created_at, u.version, d.dept_name "
            + "FROM iam_user u LEFT JOIN iam_department d ON d.id = u.dept_id "
            + "WHERE u.id = #{id}")
    AdminUserRow selectAdminRowById(@Param("id") Long id);

    /**
     * 按数据范围校验某用户是否可见（详情接口用）。
     *
     * @param id              目标用户 ID
     * @param scopeRootDeptId 数据范围根部门，可为 null
     * @param onlyUserId      只允许本人时传当前用户 ID，否则 null
     * @return 可见行数
     */
    @Select("<script>"
            + "SELECT COUNT(1) FROM iam_user u WHERE u.id = #{id} "
            + "<if test=\"scopeRootDeptId != null\">"
            + " AND (u.dept_id = #{scopeRootDeptId} OR u.dept_id IN ("
            + "SELECT id FROM iam_department WHERE CONCAT(',', ancestors, ',') "
            + "LIKE CONCAT('%,', #{scopeRootDeptId}, ',%')))"
            + "</if>"
            + "<if test=\"onlyUserId != null\"> AND u.id = #{onlyUserId}</if>"
            + "</script>")
    long countVisibleById(@Param("id") Long id,
                          @Param("scopeRootDeptId") Long scopeRootDeptId,
                          @Param("onlyUserId") Long onlyUserId);

    /**
     * 按 ID 批量查用户角色。
     *
     * @param userIds 用户 ID 列表，不能为空
     * @return 角色行；调用方按用户分组
     */
    @Select("<script>"
            + "SELECT ur.user_id, r.id AS role_id, r.role_code, r.role_name "
            + "FROM iam_user_role ur JOIN iam_role r ON r.id = ur.role_id "
            + "WHERE ur.user_id IN "
            + "<foreach collection=\"userIds\" item=\"id\" open=\"(\" separator=\",\" close=\")\">"
            + "#{id}</foreach> ORDER BY r.id"
            + "</script>")
    List<UserRoleRow> selectRolesByUserIds(@Param("userIds") List<Long> userIds);

    /**
     * 乐观锁更新白名单字段。
     *
     * @param id          用户 ID
     * @param displayName 显示名
     * @param deptId      部门 ID，可为 null
     * @param status      状态
     * @param version     旧版本号
     * @return 影响行数；0 表示版本冲突或用户不存在
     */
    @Update("UPDATE iam_user SET display_name = #{displayName}, dept_id = #{deptId}, "
            + "status = #{status}, version = version + 1, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND version = #{version}")
    int updateProfile(@Param("id") Long id,
                      @Param("displayName") String displayName,
                      @Param("deptId") Long deptId,
                      @Param("status") String status,
                      @Param("version") Integer version);

    /**
     * 软删：置 DISABLED 并递增业务版本，保留行与关联数据。
     *
     * <p>{@code V1_0} 没有 {@code deleted} 列，删除语义按 {@code status=DISABLED} 落地；
     * 会话撤销由调用方在同一事务内完成。</p>
     *
     * @param id 用户 ID
     * @return 影响行数
     */
    @Update("UPDATE iam_user SET status = 'DISABLED', version = version + 1, "
            + "updated_at = CURRENT_TIMESTAMP(3) WHERE id = #{id}")
    int softDelete(@Param("id") Long id);

    /**
     * 判断用户是否绑定 SUPER_ADMIN 角色（含停用角色，用于删除保护）。
     *
     * @param userId 用户 ID
     * @return 绑定行数
     */
    @Select("SELECT COUNT(1) FROM iam_user_role ur JOIN iam_role r ON r.id = ur.role_id "
            + "WHERE ur.user_id = #{userId} AND r.role_code = 'SUPER_ADMIN'")
    long countSuperAdminRoles(@Param("userId") Long userId);

    /**
     * 统计除指定用户外仍可用的 SUPER_ADMIN 数量。
     *
     * @param excludeUserId 排除的用户 ID
     * @return 剩余可用超管数量
     */
    @Select("SELECT COUNT(DISTINCT u.id) FROM iam_user u "
            + "JOIN iam_user_role ur ON ur.user_id = u.id "
            + "JOIN iam_role r ON r.id = ur.role_id "
            + "WHERE r.role_code = 'SUPER_ADMIN' AND u.status = 'ACTIVE' AND u.id <> #{excludeUserId}")
    long countOtherActiveSuperAdmins(@Param("excludeUserId") Long excludeUserId);
}
