package com.recruit.interview.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 异步作答回看视图（管理端）。
 *
 * @param id             作答 ID
 * @param questionId     题目 ID
 * @param seqNo          题目序号
 * @param questionText   题目文本
 * @param weight         题目权重
 * @param videoFileId    视频文件 ID
 * @param audioFileId    音频文件 ID
 * @param answerSec      实际作答时长（秒）
 * @param transcriptText 逐题转写
 * @param aiScore        AI 分
 * @param manualScore    人工复核分
 * @param manualComment  人工评语
 * @param status         SUBMITTED/TRANSCRIBED/AI_SCORED/REVIEWED
 * @param submittedAt    提交时间
 * @param version        乐观锁版本
 */
public record AsyncAnswerView(
        String id,
        String questionId,
        Integer seqNo,
        String questionText,
        BigDecimal weight,
        String videoFileId,
        String audioFileId,
        Integer answerSec,
        String transcriptText,
        BigDecimal aiScore,
        BigDecimal manualScore,
        String manualComment,
        String status,
        Instant submittedAt,
        Integer version) {
}
