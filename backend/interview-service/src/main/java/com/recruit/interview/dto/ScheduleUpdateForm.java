package com.recruit.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 改期/改面试官入参（{@code PUT /api/interview/schedules/{id}}）。
 *
 * <p>整体覆盖面试官与时间，不做增量合并；{@code resourceId} 省略时保留原资源，
 * 避免前端只改时间却把会议室清空。</p>
 */
@Data
public class ScheduleUpdateForm {

    @NotEmpty
    @Size(max = 20)
    private List<@NotBlank String> interviewerIds;

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

    @NotNull
    private Integer version;
}
