package com.recruit.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.auth.entity.IamUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
