package com.recruit.resume.support;

/**
 * local_message.status 取值。
 */
public final class OutboxStatus {

    /** 待投递。 */
    public static final String PENDING = "PENDING";

    /** 已确认投递成功。 */
    public static final String SENT = "SENT";

    /** 投递失败，等待退避重试。 */
    public static final String FAILED = "FAILED";

    /** 重试耗尽，转人工/补偿任务。 */
    public static final String DEAD = "DEAD";

    private OutboxStatus() {
    }
}
