package com.recruit.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 投递申请（{@code application}），投递是面试流程的聚合根。
 *
 * <p>{@code stageFlow} 在创建时从岗位 ACTIVE 模板复制，岗位模板后续变更不影响在途投递；
 * {@code version} 用于乐观锁，所有流转必须带版本。</p>
 */
@Data
@TableName("application")
public class Application {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long jobId;

    private String jobTitle;

    private Long personId;

    private String personName;

    private String currentStage;

    private Integer roundNo;

    /** 岗位模板复制来的阶段配置 JSON。 */
    private String stageFlow;

    private BigDecimal matchScore;

    private String status;

    private String rejectReason;

    private String source;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;
}
