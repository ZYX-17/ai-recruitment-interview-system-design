package com.recruit.common.result;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PageResult} 的单元测试。
 *
 * <p>覆盖三件事：总页数计算、空结果语义、非法分页参数的兜底校验。</p>
 */
class PageResultTest {

    /**
     * 21 条数据、每页 10 条，应该得到 3 页（最后一页只有 1 条），验证向上取整逻辑。
     */
    @Test
    void computesPagesFromTotalAndPageSize() {
        PageResult<String> result = PageResult.of(List.of("a", "b"), 21, 2, 10);

        assertEquals(2, result.getList().size());
        assertEquals(21, result.getTotal());
        assertEquals(2, result.getPageNum());
        assertEquals(10, result.getPageSize());
        assertEquals(3, result.getPages());
    }

    /**
     * 空结果：total 和 pages 都必须是 0，不能出现“0 条数据但 1 页”的矛盾状态。
     */
    @Test
    void emptyResultHasZeroPages() {
        PageResult<String> result = PageResult.empty();

        assertTrue(result.getList().isEmpty());
        assertEquals(0, result.getTotal());
        assertEquals(1, result.getPageNum());
        assertEquals(20, result.getPageSize());
        assertEquals(0, result.getPages());
    }

    /**
     * 页码从 0 开始、页大小为 0、total 为负数都属于编程错误，必须快速失败。
     */
    @Test
    void rejectsInvalidPagingArguments() {
        assertThrows(IllegalArgumentException.class, () -> PageResult.of(List.of(), 0, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> PageResult.of(List.of(), 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> PageResult.of(List.of(), -1, 1, 10));
    }
}
