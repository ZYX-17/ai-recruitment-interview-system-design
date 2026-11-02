package com.recruit.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据范围（角色默认口径的细化覆盖）。
 *
 * <p>对应 Flyway {@code V1_0__init.sql} 的 {@code iam_data_scope}，字段与列一一对应；
 * 枚举列用代码字符串（不做 MySQL ENUM），时间列为 UTC {@code DATETIME(3)}。</p>
 */
@Data
@TableName("iam_data_scope")
public class IamDataScope {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 与 userId 二选一：按角色。 */
    private Long roleId;

    /** 与 roleId 二选一：按用户，优先级高于角色。 */
    private Long userId;

    /** ALL / DEPARTMENT / ASSIGNED / SELF。 */
    private String scopeType;

    /** 领域定制规则 JSON（字符串保存，避免 JSON 类型驱动差异）。 */
    private String bizRule;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
