package com.recruit.resume.support;

/**
 * resume_parse_task.status 取值。
 */
public final class ParseTaskStatus {

    public static final String PENDING = "PENDING";
    public static final String RUNNING = "RUNNING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
    /** 重试超过 3 次，转人工补偿。 */
    public static final String DEAD = "DEAD";

    private ParseTaskStatus() {
    }
}
