package com.recruit.auth.dto;

import java.time.LocalDateTime;

/**
 * 字典项视图（类型与编码只读）。
 *
 * @param id        字典项 ID
 * @param dictType  字典类型
 * @param dictCode  枚举码
 * @param dictLabel 展示文案
 * @param sort      排序
 * @param status    ACTIVE / DISABLED
 * @param remark    备注
 * @param createdAt 创建时间
 * @param version   乐观锁版本
 */
public record DictView(String id,
                       String dictType,
                       String dictCode,
                       String dictLabel,
                       int sort,
                       String status,
                       String remark,
                       LocalDateTime createdAt,
                       int version) {
}
