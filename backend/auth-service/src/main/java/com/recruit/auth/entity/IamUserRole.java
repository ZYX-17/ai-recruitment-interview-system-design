package com.recruit.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户-角色关联。
 *
 * <p>对应 Flyway {@code V1_0__init.sql} 的 {@code iam_user_role}，字段与列一一对应；
 * 枚举列用代码字符串（不做 MySQL ENUM），时间列为 UTC {@code DATETIME(3)}。</p>
 */
@Data
@TableName("iam_user_role")
public class IamUserRole {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long roleId;
    private LocalDateTime createdAt;
}
