package com.recruit.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典项（前端下拉与枚举展示）。
 *
 * <p>对应 Flyway {@code V1_3__dict.sql} 的 {@code iam_dict}；{@code (dict_type, dict_code)} 唯一，
 * 类型与编码创建后不可改，管理端只维护展示文案、排序与启停。</p>
 */
@Data
@TableName("iam_dict")
public class IamDict {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 字典类型，如 user_status。 */
    private String dictType;

    /** 枚举码，如 ACTIVE。 */
    private String dictCode;

    private String dictLabel;
    private Integer sort;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
}
