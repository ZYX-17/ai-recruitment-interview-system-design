package com.recruit.interview.dto;

import java.time.Instant;

/**
 * 发布结果：试卷 + 一次性邀请链接（明文只在本次响应）。
 *
 * @param paper           试卷
 * @param inviteUrl       邀请链接；已发布过且未重新生成时为 null
 * @param inviteExpireAt  链接过期时间
 */
public record AsyncPublishView(AsyncPaperView paper, String inviteUrl, Instant inviteExpireAt) {
}
