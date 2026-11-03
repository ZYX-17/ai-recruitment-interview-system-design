package com.recruit.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 投递分页查询。
 */
@Data
public class ApplicationPageQuery {

    @Min(1)
    private int pageNum = 1;

    @Min(1)
    @Max(100)
    private int pageSize = 20;

    private String keyword;

    private String status;

    private String jobId;

    private String personId;
}
