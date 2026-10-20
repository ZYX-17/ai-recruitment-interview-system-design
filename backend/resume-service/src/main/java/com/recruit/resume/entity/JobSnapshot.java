package com.recruit.resume.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 岗位快照（recruit_resume.job_snapshot）。
 *
 * <p>由 interview-service 的 job.published/job.updated 事件维护；B 的匹配只读本表，
 * 不跨库读 interview。</p>
 */
@Data
@TableName("job_snapshot")
public class JobSnapshot {

    /** 与 interview 库 job.id 保持一致，由事件携带。 */
    @TableId(type = IdType.INPUT)
    private Long id;

    private Integer jobVersion;

    private String title;

    private String deptName;

    /** 岗位要求 JSON（学历/年限/技能等）。 */
    private String requirementsJson;

    /** OPEN/CLOSED 等，由来源事件决定。 */
    private String status;

    /** 最后处理的事件 ID，用于幂等与排查。 */
    private String sourceEventId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
