package com.recruit.contracts.event.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * {@code job.published} 的负载（interview → resume 快照同步）。
 *
 * <p>只带必要快照；resume 需要完整 JD 时回查 interview API。</p>
 *
 * @param jobId       岗位 ID
 * @param jobCode     岗位编码
 * @param title       岗位名称
 * @param deptId      所属部门 ID
 * @param version     岗位版本号（更新时递增）
 * @param status      岗位状态，例如 PUBLISHED
 * @param publishedAt 发布时间
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobPublishedPayload(
        String jobId,
        String jobCode,
        String title,
        String deptId,
        Integer version,
        String status,
        Instant publishedAt) {
}
