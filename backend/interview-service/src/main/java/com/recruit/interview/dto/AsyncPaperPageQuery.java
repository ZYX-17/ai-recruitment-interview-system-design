package com.recruit.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 异步试卷分页查询。
 */
@Data
public class AsyncPaperPageQuery {

    @Min(1)
    private int pageNum = 1;

    @Min(1)
    @Max(100)
    private int pageSize = 20;

    private String status;

    private String jobId;

    private String applicationId;

    private String keyword;
}
