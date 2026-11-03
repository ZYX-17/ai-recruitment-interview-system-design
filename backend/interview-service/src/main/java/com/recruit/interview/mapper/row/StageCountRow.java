package com.recruit.interview.mapper.row;

import lombok.Data;

/**
 * 阶段到达数（按 stage_no 聚合）。
 */
@Data
public class StageCountRow {

    private String stageNo;

    private Long total;
}
