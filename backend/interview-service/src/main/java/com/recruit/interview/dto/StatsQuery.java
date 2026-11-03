package com.recruit.interview.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 统计查询（P11）。
 * <p>时间范围按业务日期（Asia/Shanghai）解释：省略时默认最近 30 天，单次最多 366 天。
 * {@code jobId}/{@code interviewerId} 可选；数据范围在 Service 侧按当前用户注入。</p>
 */
@Data
public class StatsQuery {

    private String jobId;

    private String interviewerId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;
}
