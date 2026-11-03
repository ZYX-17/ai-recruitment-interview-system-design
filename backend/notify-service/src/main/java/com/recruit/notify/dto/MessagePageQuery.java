package com.recruit.notify.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 站内信分页查询。
 */
@Data
public class MessagePageQuery {

    @Min(1)
    private int pageNum = 1;

    @Min(1)
    @Max(100)
    private int pageSize = 20;

    /** 是否已读；空表示不过滤。 */
    private Boolean read;

    private String status;

    private String channel;
}
