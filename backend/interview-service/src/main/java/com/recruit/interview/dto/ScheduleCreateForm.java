package com.recruit.interview.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 新增排期入参（{@code POST /api/interview/schedules}）。
 *
 * <p>{@code candidateId} 可省略，默认取投递上的候选人；若显式传入必须与投递一致，
 * 防止把 A 的面试排到 B 的日历上。{@code resourceId} 为会议室/设备等第三方资源。</p>
 */
@Data
public class ScheduleCreateForm {

    @NotBlank
    private String applicationId;

    @Min(1)
    private Integer roundNo;

    @NotEmpty
    @Size(max = 20)
    private List<@NotBlank String> interviewerIds;

    private String candidateId;

    private String resourceId;

    @NotNull
    private Instant startTime;

    @NotNull
    private Instant endTime;

    @NotBlank
    @Size(max = 16)
    private String mode;

    @NotBlank
    @Size(max = 24)
    private String roomType;

    @Size(max = 500)
    private String note;
}
