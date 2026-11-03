package com.recruit.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 排期分页查询。
 *
 * <p>{@code dateFrom/dateTo} 是业务日期（Asia/Shanghai），服务端转成 UTC 时间区间；
 * {@code dateTo} 含当天。</p>
 */
@Data
public class SchedulePageQuery {

    @Min(1)
    private int pageNum = 1;

    @Min(1)
    @Max(100)
    private int pageSize = 20;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;

    private String interviewerId;

    private String status;
}
