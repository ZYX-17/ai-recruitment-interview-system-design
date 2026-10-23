package com.recruit.resume.support;

/**
 * Outbox 重试策略：失败后按 1/2/4/8/16 分钟退避，第 5 次转 DEAD。
 *
 * <p>抽成纯函数便于单测；发布器和确认回调共用同一套计算，避免两处口径不一致。</p>
 */
public final class OutboxRetry {

    /** 最大重试次数；达到后不再自动投递，转人工/补偿。 */
    public static final int MAX_RETRY = 5;

    private OutboxRetry() {
    }

    /**
     * @param retryCount 已失败次数（从 1 开始）
     * @return 是否应转 DEAD
     */
    public static boolean isDead(int retryCount) {
        return retryCount >= MAX_RETRY;
    }

    /**
     * 第 n 次失败后的等待分钟数：1、2、4、8、16。
     *
     * @param retryCount 已失败次数，必须 ≥1
     * @return 退避分钟数
     */
    public static long backoffMinutes(int retryCount) {
        if (retryCount < 1) {
            throw new IllegalArgumentException("retryCount 必须大于等于 1");
        }
        return 1L << Math.min(retryCount - 1, 4);
    }
}
