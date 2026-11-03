package com.recruit.interview.mapper.row;

import lombok.Data;

/**
 * 面试官工作量聚合行。
 */
@Data
public class WorkloadRow {

    private String interviewerId;

    private Long total;

    private Long completed;

    private Long noShow;
}
