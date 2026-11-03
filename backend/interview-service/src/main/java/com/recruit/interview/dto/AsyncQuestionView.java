package com.recruit.interview.dto;

import java.math.BigDecimal;

/**
 * 异步题目视图。
 *
 * @param id           题目 ID
 * @param seqNo        序号
 * @param questionText 题目文本
 * @param prepareSec   准备时长（秒）
 * @param answerSec    作答上限（秒）
 * @param weight       权重
 */
public record AsyncQuestionView(
        String id,
        Integer seqNo,
        String questionText,
        Integer prepareSec,
        Integer answerSec,
        BigDecimal weight) {
}
