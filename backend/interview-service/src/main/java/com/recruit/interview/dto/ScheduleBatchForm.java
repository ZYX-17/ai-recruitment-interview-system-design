package com.recruit.interview.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量排期入参（{@code POST /api/interview/schedules/batch}）。
 *
 * <p>整批事务：全部成功或全部失败，单批最多 50 条。</p>
 */
@Data
public class ScheduleBatchForm {

    @NotEmpty
    @Size(max = 50)
    private List<@Valid ScheduleCreateForm> items;
}
