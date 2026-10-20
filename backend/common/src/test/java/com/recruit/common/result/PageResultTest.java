package com.recruit.common.result;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageResultTest {

    @Test
    void computesPagesFromTotalAndPageSize() {
        PageResult<String> result = PageResult.of(List.of("a", "b"), 21, 2, 10);

        assertEquals(2, result.getList().size());
        assertEquals(21, result.getTotal());
        assertEquals(2, result.getPageNum());
        assertEquals(10, result.getPageSize());
        assertEquals(3, result.getPages());
    }

    @Test
    void emptyResultHasZeroPages() {
        PageResult<String> result = PageResult.empty();

        assertTrue(result.getList().isEmpty());
        assertEquals(0, result.getTotal());
        assertEquals(1, result.getPageNum());
        assertEquals(20, result.getPageSize());
        assertEquals(0, result.getPages());
    }

    @Test
    void rejectsInvalidPagingArguments() {
        assertThrows(IllegalArgumentException.class, () -> PageResult.of(List.of(), 0, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> PageResult.of(List.of(), 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> PageResult.of(List.of(), -1, 1, 10));
    }
}
