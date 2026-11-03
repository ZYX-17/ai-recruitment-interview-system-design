package com.recruit.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 排期冲突预检入参（{@code POST /api/interview/schedules/conflict-check}）。
 *
 * <p>只读：不写库、不锁行；与写接口使用同一套取整与槽位算法。</p>
 */
@Data
public class ConflictCheckForm {

    @NotBlank
    private String candidateId;

    @NotEmpty
    @Size(max = 20)
    private List<@NotBlank String> interviewerIds;

    private String resourceId;

    @NotNull
    private Instant startTime;

    @NotNull
    private Instant endTime;
}
