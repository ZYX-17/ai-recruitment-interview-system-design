package com.recruit.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 岗位（{@code job}）。
 *
 * <p>状态：DRAFT → PUBLISHED → PAUSED/CLOSED；CLOSED 不可再投递。
 * {@code jobCode} 唯一，对外 ID 一律按字符串输出。</p>
 */
@Data
@TableName("job")
public class Job {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String jobCode;

    private String title;

    private Long deptId;

    private String deptName;

    private String jdText;

    /** 硬性要求 JSON（学历/年限/技能）。 */
    private String requirementsJson;

    private Integer headcount;

    private String salaryRange;

    private String status;

    private Long ownerHrId;

    private LocalDateTime publishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;
}
