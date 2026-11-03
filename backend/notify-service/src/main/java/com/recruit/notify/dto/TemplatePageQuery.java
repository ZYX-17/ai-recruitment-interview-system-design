package com.recruit.notify.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 通知模板分页查询。
 */
@Data
public class TemplatePageQuery {

    @Min(1)
    private int pageNum = 1;

    @Min(1)
    @Max(100)
    private int pageSize = 20;

    private String templateCode;

    private String channel;

    private String status;
}
