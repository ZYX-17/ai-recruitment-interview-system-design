package com.recruit.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 岗位流程模板（版本化）。
 *
 * <p>同一岗位只有一个 ACTIVE；历史版本置 INACTIVE 只读，保证在途投递与已提交评价可追溯。</p>
 */
@Data
@TableName("job_stage_template")
public class JobStageTemplate {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long jobId;

    private Integer templateVer;

    /** [{stageNo,stageName,mode,rounds}]。 */
    private String stageFlow;

    /** [{dimCode,dimName,weight,fullScore,vetoThreshold}]。 */
    private String scoreDims;

    private String status;

    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;
}
