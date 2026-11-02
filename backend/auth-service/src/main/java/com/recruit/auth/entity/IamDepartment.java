package com.recruit.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 部门树。
 *
 * <p>对应 Flyway {@code V1_0__init.sql} 的 {@code iam_department}，字段与列一一对应；
 * 枚举列用代码字符串（不做 MySQL ENUM），时间列为 UTC {@code DATETIME(3)}。</p>
 */
@Data
@TableName("iam_department")
public class IamDepartment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long parentId;

    /** 祖级路径，如 0,100,101。 */
    private String ancestors;

    private String deptName;
    private Integer sort;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
}
