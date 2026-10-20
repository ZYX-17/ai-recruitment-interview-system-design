package com.recruit.common.result;

import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 统一分页响应对象。
 *
 * <p>为什么要有这个类：如果每个列表接口各自拼 {@code {data,total}}、{@code {rows,count}}，
 * 前端就要为每个接口写一套解析逻辑。这里把分页响应固定成一份契约，所有服务、所有列表接口
 * 都返回同样的 5 个字段：</p>
 *
 * <pre>
 * {
 *   "list":     [ ... ],   // 当前页的数据
 *   "total":    123,       // 满足条件的总记录数（不是当前页条数）
 *   "pageNum":  2,         // 当前页码，从 1 开始
 *   "pageSize": 20,        // 每页条数
 *   "pages":    7          // 总页数，由 total 和 pageSize 计算，前端不用再算
 * }
 * </pre>
 *
 * <p>职责边界：本类只负责“装结果”，不查数据库，也不负责校验请求参数。
 * pageNum/pageSize 的入参范围应由 Controller 的 {@code @Min/@Max} 或 Query 对象约束；
 * 这里再做一次兜底校验，是为了防止 Job、RPC 等内部调用传错值后，产生
 * “第 0 页”“每页 0 条”这类语义错误的数据。</p>
 *
 * @param <T> 当前页元素的类型
 */
@Getter
public final class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前页数据。构造时做防御性拷贝并包成只读列表，避免调用方拿到后修改分页结果。 */
    private final List<T> list;

    /** 满足查询条件的总记录数；不是当前页的条数。 */
    private final long total;

    /** 当前页码，从 1 开始。 */
    private final long pageNum;

    /** 每页条数，必须大于 0。 */
    private final long pageSize;

    /** 总页数，由 total 和 pageSize 计算得出；total=0 时为 0。 */
    private final long pages;

    /**
     * 私有构造：外部统一走 {@link #of(List, long, long, long)} 或 {@link #empty(long, long)}，
     * 避免出现“构造一半”的对象。
     *
     * @param list     当前页数据，允许为 null（按空列表处理）
     * @param total    总记录数，不能小于 0
     * @param pageNum  当前页码，从 1 开始
     * @param pageSize 每页条数，必须大于 0
     * @throws IllegalArgumentException 分页参数不合法时抛出。这属于调用方编码错误，
     *                                  所以不包装成业务异常，也不返回 4xx 给终端用户。
     */
    private PageResult(List<T> list, long total, long pageNum, long pageSize) {
        // total 为负数说明 SQL count 或上游计算结果有问题，直接失败，避免页面显示负数。
        if (total < 0) {
            throw new IllegalArgumentException("total 不能小于 0");
        }
        // 页码从 1 开始，和第 1 页的直觉一致；0 或负数通常意味着前端没传或算错。
        if (pageNum < 1) {
            throw new IllegalArgumentException("pageNum 必须从 1 开始");
        }
        // pageSize 为 0 会导致下面算 pages 时除零，必须拦住。
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize 必须大于 0");
        }
        // 拷贝一份：即使调用方之后修改了原始 list，也不会影响已经返回的分页结果。
        this.list = list == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(list));
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        // 向上取整：123 条、每页 20 条 -> 7 页（最后 1 页只有 3 条）。
        // 这里刻意用整数运算，避免 Math.ceil(total / (double) pageSize) 的浮点误差。
        this.pages = total == 0 ? 0 : total / pageSize + (total % pageSize == 0 ? 0 : 1);
    }

    /**
     * 构造一页数据。这是业务代码最常用的入口。
     *
     * <p>示例：{@code PageResult.of(rows, count, query.getPageNum(), query.getPageSize())}</p>
     *
     * @param list     当前页数据，允许为 null
     * @param total    满足条件的总记录数
     * @param pageNum  当前页码，从 1 开始
     * @param pageSize 每页条数
     * @param <T>      元素类型
     * @return 分页结果
     */
    public static <T> PageResult<T> of(List<T> list, long total, long pageNum, long pageSize) {
        return new PageResult<>(list, total, pageNum, pageSize);
    }

    /**
     * 构造一个“查询结果为空”的分页对象，total 固定为 0、pages 固定为 0。
     *
     * <p>适用场景：上游已经短路（例如权限校验后直接返回空），但仍要保持响应结构一致，
     * 前端不需要为“空结果”单独写分支。</p>
     *
     * @param pageNum  当前页码，从 1 开始
     * @param pageSize 每页条数
     * @param <T>      元素类型
     * @return 空的分页结果
     */
    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        return new PageResult<>(Collections.emptyList(), 0, pageNum, pageSize);
    }

    /**
     * 构造默认空页：第 1 页、每页 20 条。仅用于确实拿不到分页参数的兜底场景。
     *
     * @param <T> 元素类型
     * @return 空的分页结果
     */
    public static <T> PageResult<T> empty() {
        return empty(1, 20);
    }
}
