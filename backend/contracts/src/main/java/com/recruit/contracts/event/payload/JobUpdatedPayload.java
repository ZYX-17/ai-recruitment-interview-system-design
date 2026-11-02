package com.recruit.contracts.event.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * {@code job.updated} 的负载（更新/暂停/关闭）。
 *
 * @param jobId         岗位 ID
 * @param version       新版本号
 * @param status        当前状态
 * @param changedFields 发生变化的字段名列表，消费方可据此决定是否重建索引
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobUpdatedPayload(
        String jobId,
        Integer version,
        String status,
        List<String> changedFields) {
}
