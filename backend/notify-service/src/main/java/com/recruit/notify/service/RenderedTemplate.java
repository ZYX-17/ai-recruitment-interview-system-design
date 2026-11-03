package com.recruit.notify.service;

/**
 * 渲染后的模板内容。
 *
 * @param title   标题
 * @param content 正文
 */
public record RenderedTemplate(String title, String content) {
}
