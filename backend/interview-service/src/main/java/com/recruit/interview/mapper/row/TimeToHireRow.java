package com.recruit.interview.mapper.row;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 招聘周期聚合行。
 */
@Data
public class TimeToHireRow {

    private Long sampleCount;

    private BigDecimal avgDays;

    private BigDecimal minDays;

    private BigDecimal maxDays;
}
