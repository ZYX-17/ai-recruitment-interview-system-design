package com.recruit.auth.service;

import com.recruit.auth.dto.AuditLogPageQuery;
import com.recruit.auth.dto.AuditLogView;
import com.recruit.auth.dto.LoginLogPageQuery;
import com.recruit.auth.dto.LoginLogView;
import com.recruit.auth.mapper.IamAuditLogMapper;
import com.recruit.auth.mapper.row.AuditLogRow;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdminAuditService} 的操作日志/登录日志分页测试。
 */
@ExtendWith(MockitoExtension.class)
class AdminAuditServiceTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 3, 0, 0);

    @Mock
    private IamAuditLogMapper auditLogMapper;

    private AdminAuditService service;

    @BeforeEach
    void setUp() {
        service = new AdminAuditService(auditLogMapper);
    }

    @Test
    void auditPageMapsFiltersAndRows() {
        AuditLogRow row = row(1L, 40001L, "admin", "管理员", "USER_UPDATE", "USER",
                "40002", "OK", "127.0.0.1", "trace-1", "{\"status\":\"ACTIVE\"}");
        when(auditLogMapper.countAuditPage(40001L, "USER_UPDATE", "USER", "OK",
                "127.0.0.1", "trace-1", START, END)).thenReturn(1L);
        when(auditLogMapper.selectAuditPage(40001L, "USER_UPDATE", "USER", "OK",
                "127.0.0.1", "trace-1", START, END, 20, 0)).thenReturn(List.of(row));

        AuditLogPageQuery query = new AuditLogPageQuery();
        query.setOperatorId(40001L);
        query.setAction(" USER_UPDATE ");
        query.setResourceType("USER");
        query.setResult("OK");
        query.setIp("127.0.0.1");
        query.setTraceId("trace-1");
        query.setStartTime(START);
        query.setEndTime(END);

        PageResult<AuditLogView> result = service.auditPage(query);

        assertEquals(1, result.getTotal());
        AuditLogView view = result.getList().get(0);
        assertEquals("1", view.id());
        assertEquals("40001", view.operatorId());
        assertEquals("admin", view.operatorUsername());
        assertEquals("USER_UPDATE", view.action());
        assertEquals("{\"status\":\"ACTIVE\"}", view.detail());
    }

    @Test
    void auditPageReturnsEmptyWithoutQueryingRows() {
        when(auditLogMapper.countAuditPage(null, null, null, null, null, null, null, null))
                .thenReturn(0L);

        PageResult<AuditLogView> result = service.auditPage(new AuditLogPageQuery());

        assertEquals(0, result.getTotal());
        assertEquals(0, result.getPages());
        verify(auditLogMapper, never()).selectAuditPage(any(), any(), any(), any(), any(),
                any(), any(), any(), anyLong(), anyLong());
    }

    @Test
    void auditPageRejectsInvertedRange() {
        AuditLogPageQuery query = new AuditLogPageQuery();
        query.setStartTime(END);
        query.setEndTime(START);

        BizException error = assertThrows(BizException.class, () -> service.auditPage(query));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
        verify(auditLogMapper, never()).countAuditPage(any(), any(), any(), any(), any(),
                any(), any(), any());
    }

    @Test
    void loginPageMapsRows() {
        AuditLogRow row = row(2L, 40001L, "admin", "管理员", "LOGIN", "USER",
                "40001", "FAIL", "10.0.0.1", "trace-2", "{\"reason\":\"PASSWORD_MISMATCH\"}");
        when(auditLogMapper.countLoginPage("admin", "FAIL", null, null, null, null)).thenReturn(1L);
        when(auditLogMapper.selectLoginPage("admin", "FAIL", null, null, null, null, 20, 0))
                .thenReturn(List.of(row));

        LoginLogPageQuery query = new LoginLogPageQuery();
        query.setUsername(" admin ");
        query.setResult("FAIL");

        PageResult<LoginLogView> result = service.loginPage(query);

        assertEquals(1, result.getTotal());
        LoginLogView view = result.getList().get(0);
        assertEquals("admin", view.username());
        assertEquals("FAIL", view.result());
        assertEquals("trace-2", view.traceId());
    }

    @Test
    void loginPageSupportsAnonymousFailure() {
        AuditLogRow row = row(3L, null, null, null, "LOGIN", "USER",
                null, "FAIL", "10.0.0.2", "trace-3", "{\"reason\":\"USER_NOT_FOUND\"}");
        when(auditLogMapper.countLoginPage(null, null, "10.0.0.2", null, START, END)).thenReturn(1L);
        when(auditLogMapper.selectLoginPage(null, null, "10.0.0.2", null, START, END, 20, 0))
                .thenReturn(List.of(row));

        LoginLogPageQuery query = new LoginLogPageQuery();
        query.setIp("10.0.0.2");
        query.setStartTime(START);
        query.setEndTime(END);

        LoginLogView view = service.loginPage(query).getList().get(0);

        assertNull(view.operatorId());
        assertNull(view.username());
        assertEquals("FAIL", view.result());
    }

    private static AuditLogRow row(long id, Long operatorId, String username, String name,
                                   String action, String resourceType, String resourceId,
                                   String result, String ip, String traceId, String detail) {
        AuditLogRow row = new AuditLogRow();
        row.setId(id);
        row.setOperatorId(operatorId);
        row.setOperatorUsername(username);
        row.setOperatorName(name);
        row.setAction(action);
        row.setResourceType(resourceType);
        row.setResourceId(resourceId);
        row.setResult(result);
        row.setIp(ip);
        row.setTraceId(traceId);
        row.setDetail(detail);
        row.setCreatedAt(START);
        return row;
    }
}
