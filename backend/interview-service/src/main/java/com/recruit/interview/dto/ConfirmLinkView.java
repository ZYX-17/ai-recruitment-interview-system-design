package com.recruit.interview.dto;

import java.time.Instant;

/**
 * 内部接口返回的一次性确认链接（不落日志、不进 MQ）。
 *
 * @param url      确认链接
 * @param expireAt 链接缓存过期时间
 */
public record ConfirmLinkView(String url, Instant expireAt) {
}
