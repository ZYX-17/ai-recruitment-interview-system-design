package com.recruit.notify.service;

/**
 * 一次投递的结果（不含正文，避免日志泄露）。
 *
 * @param success   是否成功
 * @param provider  提供商（SMTP / MAIL_MOCK / INAPP）
 * @param requestId 请求 ID
 * @param error     失败原因
 * @param costMs    耗时
 */
public record DeliveryOutcome(
        boolean success,
        String provider,
        String requestId,
        String error,
        int costMs) {

    /**
     * 成功结果。
     */
    public static DeliveryOutcome ok(String provider, String requestId, long startNanos) {
        return new DeliveryOutcome(true, provider, requestId, null, elapsedMs(startNanos));
    }

    /**
     * 失败结果。
     */
    public static DeliveryOutcome fail(String provider, String error, long startNanos) {
        return new DeliveryOutcome(false, provider, null, error, elapsedMs(startNanos));
    }

    private static int elapsedMs(long startNanos) {
        return (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - startNanos) / 1_000_000L);
    }
}
