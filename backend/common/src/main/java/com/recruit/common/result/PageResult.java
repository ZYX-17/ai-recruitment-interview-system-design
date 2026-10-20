package com.recruit.common.result;

import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 统一分页响应。字段契约固定为 {list,total,pageNum,pageSize,pages}，各服务不得另造字段名。
 *
 * <p>只做分页结果承载，不负责查询参数校验；Controller/Query 负责 pageNum/pageSize 的入参约束。</p>
 */
@Getter
public final class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final List<T> list;
    private final long total;
    private final long pageNum;
    private final long pageSize;
    private final long pages;

    private PageResult(List<T> list, long total, long pageNum, long pageSize) {
        if (total < 0) {
            throw new IllegalArgumentException("total 不能小于 0");
        }
        if (pageNum < 1) {
            throw new IllegalArgumentException("pageNum 必须从 1 开始");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize 必须大于 0");
        }
        this.list = list == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(list));
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.pages = total == 0 ? 0 : total / pageSize + (total % pageSize == 0 ? 0 : 1);
    }

    public static <T> PageResult<T> of(List<T> list, long total, long pageNum, long pageSize) {
        return new PageResult<>(list, total, pageNum, pageSize);
    }

    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        return new PageResult<>(Collections.emptyList(), 0, pageNum, pageSize);
    }

    public static <T> PageResult<T> empty() {
        return empty(1, 20);
    }
}
