package com.recruit.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色（五角色由 seed 写入）。
 *
 * <p>对应 Flyway {@code V1_0__init.sql} 的 {@code iam_role}，字段与列一一对应；
 * 枚举列用代码字符串（不做 MySQL ENUM），时间列为 UTC {@code DATETIME(3)}。</p>
 */
@Data
@TableName("iam_role")
public class IamRole {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** SUPER_ADMIN / HR_ADMIN / RECRUITER / INTERVIEWER / CANDIDATE。 */
    private String roleCode;

    private String roleName;

    /** 系统内置角色不可删。 */
    private Boolean isSystemRole;

    /** ALL / DEPARTMENT / ASSIGNED / SELF。 */
    private String dataScope;

    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
}
