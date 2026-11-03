package com.recruit.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * Outbox 管理页查询（X-12）。
 */
@Data
public class OutboxPageQuery {

    @Min(1)
    private int pageNum = 1;

    @Min(1)
    @Max(100)
    private int pageSize = 20;

    private String status;

    private String eventType;
}
