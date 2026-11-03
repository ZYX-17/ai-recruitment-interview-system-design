package com.recruit.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 门户投递入参（I-09）。
 *
 * <p>候选人身份取自网关注入的当前用户，不接受请求体指定 {@code personId}；
 * {@code resumeId} 仅由前端保留，面试域只存 {@code person_id} 与姓名快照。</p>
 */
@Data
public class PortalApplicationCreateForm {

    @NotBlank
    private String jobId;

    @Size(max = 32)
    private String resumeId;

    @Size(max = 500)
    private String remark;
}
