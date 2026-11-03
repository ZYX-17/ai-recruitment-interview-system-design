package com.recruit.interview.dto;

/**
 * 漏斗阶段。
 *
 * @param stageNo   阶段编码
 * @param stageName 阶段名称
 * @param count     到达该阶段的去重投递数
 */
public record FunnelStageView(String stageNo, String stageName, long count) {
}
