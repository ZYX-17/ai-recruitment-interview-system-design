package com.recruit.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 门户岗位分页查询（I-10，公开只读）。
 */
@Data
public class PortalJobPageQuery {

    @Min(1)
    private int pageNum = 1;

    @Min(1)
    @Max(100)
    private int pageSize = 20;

    /** 岗位编码/名称模糊词。 */
    private String keyword;
}
