package com.recruit.auth.service;

import com.recruit.auth.dto.DictPageQuery;
import com.recruit.auth.dto.DictView;
import com.recruit.auth.dto.UpdateDictForm;
import com.recruit.auth.entity.IamDict;
import com.recruit.auth.mapper.IamDictMapper;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdminDictService} 的分页与白名单更新测试。
 */
@ExtendWith(MockitoExtension.class)
class AdminDictServiceTest {

    @Mock
    private IamDictMapper dictMapper;
    @Mock
    private AuditService auditService;

    private AdminDictService service;

    @BeforeEach
    void setUp() {
        service = new AdminDictService(dictMapper, auditService);
    }

    @Test
    void pageReturnsEmptyWithoutQueryingRows() {
        when(dictMapper.countDictPage("user_status", null, "ACTIVE")).thenReturn(0L);

        PageResult<DictView> result = service.page(query("user_status", "ACTIVE"));

        assertEquals(0, result.getTotal());
        assertEquals(1, result.getPageNum());
        assertEquals(20, result.getPageSize());
        verify(dictMapper, never()).selectDictPage(any(), any(), any(), anyLong(), anyLong());
    }

    @Test
    void pageMapsRows() {
        IamDict dict = dict(70003L, "user_status", "ACTIVE", "正常", 10, "ACTIVE", 2);
        when(dictMapper.countDictPage("user_status", "启用", null)).thenReturn(1L);
        when(dictMapper.selectDictPage("user_status", "启用", null, 20, 0))
                .thenReturn(List.of(dict));

        PageResult<DictView> result = service.page(queryWithKeyword("user_status", "启用"));

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getList().size());
        DictView view = result.getList().get(0);
        assertEquals("70003", view.id());
        assertEquals("user_status", view.dictType());
        assertEquals("ACTIVE", view.dictCode());
        assertEquals("正常", view.dictLabel());
        assertEquals(2, view.version());
    }

    @Test
    void updateKeepsUnspecifiedFieldsAndWritesAudit() {
        IamDict current = dict(70003L, "user_status", "ACTIVE", "正常", 10, "ACTIVE", 2);
        IamDict updated = dict(70003L, "user_status", "ACTIVE", "可用", 10, "DISABLED", 3);
        when(dictMapper.selectById(70003L)).thenReturn(current, updated);
        when(dictMapper.updateDict(70003L, "可用", 10, "DISABLED", null, 2)).thenReturn(1);

        UpdateDictForm form = new UpdateDictForm();
        form.setDictLabel("可用");
        form.setStatus("DISABLED");
        form.setVersion(2);

        DictView view = service.update(70003L, form, 1L, "127.0.0.1");

        assertEquals("可用", view.dictLabel());
        assertEquals("DISABLED", view.status());
        assertNull(view.remark());
        verify(auditService).recordQuietly(eq(1L), eq("DICT_UPDATE"), eq("DICT"),
                eq("70003"), eq(true), eq("127.0.0.1"), any());
    }

    @Test
    void updateReturnsVersionConflict() {
        IamDict current = dict(70003L, "user_status", "ACTIVE", "正常", 10, "ACTIVE", 2);
        when(dictMapper.selectById(70003L)).thenReturn(current, current);
        when(dictMapper.updateDict(70003L, "正常", 10, "ACTIVE", null, 1)).thenReturn(0);

        UpdateDictForm form = new UpdateDictForm();
        form.setVersion(1);

        BizException error = assertThrows(BizException.class,
                () -> service.update(70003L, form, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.VERSION_CONFLICT, error.getErrorCode());
        verify(auditService, never()).recordQuietly(any(), anyString(), anyString(),
                anyString(), eq(true), anyString(), any());
    }

    @Test
    void updateReturnsNotFoundWhenRowDisappears() {
        IamDict current = dict(70003L, "user_status", "ACTIVE", "正常", 10, "ACTIVE", 2);
        when(dictMapper.selectById(70003L)).thenReturn(current, (IamDict) null);
        when(dictMapper.updateDict(eq(70003L), anyString(), anyInt(), anyString(), any(), anyInt()))
                .thenReturn(0);

        UpdateDictForm form = new UpdateDictForm();
        form.setVersion(2);

        BizException error = assertThrows(BizException.class,
                () -> service.update(70003L, form, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, error.getErrorCode());
    }

    private static DictPageQuery query(String dictType, String status) {
        DictPageQuery query = new DictPageQuery();
        query.setDictType(dictType);
        query.setStatus(status);
        return query;
    }

    private static DictPageQuery queryWithKeyword(String dictType, String keyword) {
        DictPageQuery query = new DictPageQuery();
        query.setDictType(dictType);
        query.setKeyword(keyword);
        return query;
    }

    private static IamDict dict(long id, String type, String code, String label,
                                int sort, String status, int version) {
        IamDict dict = new IamDict();
        dict.setId(id);
        dict.setDictType(type);
        dict.setDictCode(code);
        dict.setDictLabel(label);
        dict.setSort(sort);
        dict.setStatus(status);
        dict.setVersion(version);
        return dict;
    }
}
