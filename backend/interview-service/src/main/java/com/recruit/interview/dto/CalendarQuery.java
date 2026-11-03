package com.recruit.interview.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 日历查询。
 *
 * <p>时间范围按业务日期（Asia/Shanghai）解释；省略时默认今天起 30 天，
 * 单次最多 366 天，避免全表扫描。</p>
 */
@Data
public class CalendarQuery {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;

    private String interviewerId;

    private String deptId;
}
