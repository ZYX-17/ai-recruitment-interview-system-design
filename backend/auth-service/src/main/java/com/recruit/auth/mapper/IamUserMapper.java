package com.recruit.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.auth.entity.IamUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
     * 更新密码哈希（只在改密时调用，不整行覆盖）。
     *
     * @param id           用户 ID
     * @param passwordHash BCrypt 哈希
     * @return 影响行数；0 表示用户不存在
     */
    @Update("UPDATE iam_user SET password_hash = #{passwordHash}, "
            + "updated_at = CURRENT_TIMESTAMP(3) WHERE id = #{id}")
    int updatePasswordHash(@Param("id") Long id, @Param("passwordHash") String passwordHash);
}
