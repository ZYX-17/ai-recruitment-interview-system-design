package com.recruit.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 修改岗位入参（{@code PUT /api/interview/jobs/{id}}）。
 *
 * <p>{@code jobCode} 不可改：它已进入 {@code job.published} 事件与 resume 快照。</p>
 */
@Data
public class JobUpdateForm {

    @Size(max = 128)
    private String title;

    private String deptId;

    @Size(max = 64)
    private String deptName;

    private String jdText;

    private Map<String, Object> requirements;

    @Min(1)
    @Max(100000)
    private Integer headcount;

    @Size(max = 64)
    private String salaryRange;

    private String ownerHrId;

    @NotNull
    private Integer version;
}
