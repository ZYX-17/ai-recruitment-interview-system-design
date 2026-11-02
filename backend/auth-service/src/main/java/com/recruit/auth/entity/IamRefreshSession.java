package com.recruit.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 刷新令牌会话登记（主存储在 Redis，本表用于审计与对账）。
 *
 * <p>对应 Flyway {@code V1_0__init.sql} 的 {@code iam_refresh_session}，字段与列一一对应；
 * 枚举列用代码字符串（不做 MySQL ENUM），时间列为 UTC {@code DATETIME(3)}。</p>
 */
@Data
@TableName("iam_refresh_session")
public class IamRefreshSession {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;

    /** 令牌族：重放时整族撤销。 */
    private String familyId;

    /** sha256(Refresh Token)。 */
    private String tokenHash;

    private String device;
    private String ip;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;

    /** ACTIVE / REVOKED / REPLAYED。 */
    private String status;

    private LocalDateTime createdAt;
}
