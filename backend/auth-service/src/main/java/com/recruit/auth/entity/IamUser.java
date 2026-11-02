package com.recruit.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户（员工与候选人共用）。
 *
 * <p>对应 Flyway {@code V1_0__init.sql} 的 {@code iam_user}，字段与列一一对应；
 * 枚举列用代码字符串（不做 MySQL ENUM），时间列为 UTC {@code DATETIME(3)}。</p>
 */
@Data
@TableName("iam_user")
public class IamUser {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 登录名，全局唯一（库排序规则大小写不敏感）。 */
    private String username;

    /** BCrypt 哈希；候选人首次可由邀约创建后补。 */
    private String passwordHash;

    /** EMPLOYEE / CANDIDATE。 */
    private String userType;

    private String displayName;

    /** ACTIVE / LOCKED / DISABLED。 */
    private String status;

    /** 令牌版本；登出全部/改密/禁用时递增，网关与 Redis auth:tv 比对。 */
    private Integer tokenVersion;

    /** 手机号密文。 */
    private String phoneEnc;

    /** 手机号 SHA-256，用于查重。 */
    private String phoneHash;

    private Long deptId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 乐观锁版本（P2 更新接口使用）。 */
    private Integer version;
}
