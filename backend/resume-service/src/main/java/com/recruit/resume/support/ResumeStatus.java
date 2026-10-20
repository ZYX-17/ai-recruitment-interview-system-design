package com.recruit.resume.support;

/**
 * resume.parse_status 取值（与 docs/b/05-api.md 的 parse-status 契约一致）。
 */
public final class ResumeStatus {

    public static final String PENDING = "PENDING";
    public static final String PARSING = "PARSING";
    public static final String STRUCTURING = "STRUCTURING";
    public static final String INDEXING = "INDEXING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    private ResumeStatus() {
    }
}
