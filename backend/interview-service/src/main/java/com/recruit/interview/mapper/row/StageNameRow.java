package com.recruit.interview.mapper.row;

import lombok.Data;

/**
 * 流程模板中的阶段定义（用于漏斗顺序与名称）。
 */
@Data
public class StageNameRow {

    private String stageNo;

    private String stageName;

    private Integer ord;
}
