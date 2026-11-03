package com.recruit.auth.service;

import com.recruit.auth.dto.CreatePermissionForm;
import com.recruit.auth.dto.PermissionNodeView;
import com.recruit.auth.dto.UpdatePermissionForm;
import com.recruit.auth.entity.IamPermission;
import com.recruit.auth.mapper.IamPermissionMapper;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdminPermissionService} 的权限树与 CRUD 规则测试（不连库、不连 Redis）。
 */
@ExtendWith(MockitoExtension.class)
class AdminPermissionServiceTest {

    @Mock
    private IamPermissionMapper permissionMapper;
    @Mock
    private PermissionService permissionService;
    @Mock
    private AuditService auditService;

    private AdminPermissionService service;

    @BeforeEach
    void setUp() {
        service = new AdminPermissionService(permissionMapper, permissionService, auditService);
    }

    @Test
    void treeBuildsHierarchyAndPromotesOrphans() {
        when(permissionMapper.selectAll()).thenReturn(List.of(
                permission(1L, "interview", "MENU", null, "ACTIVE", 10, 0),
                permission(2L, "interview:job:view", "MENU", 1L, "ACTIVE", 1, 0),
                permission(3L, "orphan:view", "MENU", 99L, "ACTIVE", 2, 0)));

        List<PermissionNodeView> tree = service.tree();

        assertEquals(2, tree.size());
        assertEquals("interview", tree.get(0).code());
        assertEquals(1, tree.get(0).children().size());
        assertEquals("interview:job:view", tree.get(0).children().get(0).code());
        assertEquals("orphan:view", tree.get(1).code());
    }

    @Test
    void createRejectsDuplicateCode() {
        when(permissionMapper.selectByCode("interview:job:view")).thenReturn(new IamPermission());

        BizException error = assertThrows(BizException.class,
                () -> service.create(createForm("interview:job:view", "MENU", null), 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
        verify(permissionMapper, never()).insert(any(IamPermission.class));
    }

    @Test
    void createRejectsNonMenuParent() {
        when(permissionMapper.selectByCode("interview:job:create")).thenReturn(null);
        when(permissionMapper.selectById(30002L))
                .thenReturn(permission(30002L, "interview:job:view", "BUTTON", null, "ACTIVE", 1, 0));

        BizException error = assertThrows(BizException.class,
                () -> service.create(createForm("interview:job:create", "BUTTON", 30002L), 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
        verify(permissionMapper, never()).insert(any(IamPermission.class));
    }

    @Test
    void createInsertsAndEvictsCaches() {
        when(permissionMapper.selectByCode("interview:async:view")).thenReturn(null);
        when(permissionMapper.insert(any(IamPermission.class))).thenAnswer(invocation -> {
            IamPermission saved = invocation.getArgument(0);
            saved.setId(30050L);
            return 1;
        });

        PermissionNodeView node = service.create(createForm("interview:async:view", "MENU", null), 1L, "127.0.0.1");

        assertEquals("30050", node.id());
        assertEquals("ACTIVE", node.status());
        verify(permissionService).evictAllPermissionCaches();
        verify(auditService).recordQuietly(eq(1L), eq("PERMISSION_CREATE"), eq("PERMISSION"),
                eq("30050"), eq(true), eq("127.0.0.1"), any());
    }

    @Test
    void updateRejectsSelfAsParent() {
        when(permissionMapper.selectById(30001L))
                .thenReturn(permission(30001L, "interview", "MENU", null, "ACTIVE", 10, 1));

        UpdatePermissionForm form = updateForm(1);
        form.setParentId(30001L);

        BizException error = assertThrows(BizException.class,
                () -> service.update(30001L, form, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
    }

    @Test
    void updateRejectsCycle() {
        when(permissionMapper.selectById(30002L))
                .thenReturn(permission(30002L, "interview:job:view", "MENU", 30001L, "ACTIVE", 1, 1));
        when(permissionMapper.selectById(30001L))
                .thenReturn(permission(30001L, "interview", "MENU", 30002L, "ACTIVE", 10, 0));

        UpdatePermissionForm form = updateForm(1);
        form.setParentId(30001L);

        BizException error = assertThrows(BizException.class,
                () -> service.update(30002L, form, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
    }

    @Test
    void updateRejectsDisablingMenuWithChildren() {
        when(permissionMapper.selectById(30001L))
                .thenReturn(permission(30001L, "interview", "MENU", null, "ACTIVE", 10, 1));
        when(permissionMapper.countByParentId(30001L)).thenReturn(2L);

        UpdatePermissionForm form = updateForm(1);
        form.setStatus("DISABLED");

        BizException error = assertThrows(BizException.class,
                () -> service.update(30001L, form, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
        verify(permissionMapper, never()).updatePermission(anyLong(), anyString(), any(), any(), any(),
                any(), any(), anyInt(), anyString(), anyInt());
    }

    @Test
    void updateReturnsVersionConflict() {
        when(permissionMapper.selectById(30001L))
                .thenReturn(permission(30001L, "interview", "MENU", null, "ACTIVE", 10, 1));
        when(permissionMapper.updatePermission(eq(30001L), anyString(), isNull(), any(), any(), any(),
                any(), anyInt(), eq("ACTIVE"), eq(1))).thenReturn(0);

        BizException error = assertThrows(BizException.class,
                () -> service.update(30001L, updateForm(1), 1L, "127.0.0.1"));

        assertEquals(ErrorCode.VERSION_CONFLICT, error.getErrorCode());
    }

    @Test
    void deleteRejectsChildren() {
        when(permissionMapper.selectById(30001L))
                .thenReturn(permission(30001L, "interview", "MENU", null, "ACTIVE", 10, 0));
        when(permissionMapper.countByParentId(30001L)).thenReturn(1L);

        BizException error = assertThrows(BizException.class,
                () -> service.delete(30001L, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
        verify(permissionMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteRejectsRoleReference() {
        when(permissionMapper.selectById(30050L))
                .thenReturn(permission(30050L, "interview:async:view", "MENU", null, "ACTIVE", 1, 0));
        when(permissionMapper.countByParentId(30050L)).thenReturn(0L);
        when(permissionMapper.countRoleReferences(30050L)).thenReturn(3L);

        BizException error = assertThrows(BizException.class,
                () -> service.delete(30050L, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
        verify(permissionMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteRemovesAndEvictsCaches() {
        when(permissionMapper.selectById(30050L))
                .thenReturn(permission(30050L, "interview:async:view", "MENU", null, "ACTIVE", 1, 0));
        when(permissionMapper.countByParentId(30050L)).thenReturn(0L);
        when(permissionMapper.countRoleReferences(30050L)).thenReturn(0L);

        service.delete(30050L, 1L, "127.0.0.1");

        verify(permissionMapper).deleteById(30050L);
        verify(permissionService).evictAllPermissionCaches();
    }

    private static CreatePermissionForm createForm(String code, String type, Long parentId) {
        CreatePermissionForm form = new CreatePermissionForm();
        form.setPermissionCode(code);
        form.setType(type);
        form.setParentId(parentId);
        form.setName("测试权限");
        return form;
    }

    private static UpdatePermissionForm updateForm(int version) {
        UpdatePermissionForm form = new UpdatePermissionForm();
        form.setVersion(version);
        return form;
    }

    private static IamPermission permission(long id, String code, String type, Long parentId,
                                            String status, int sort, int version) {
        IamPermission permission = new IamPermission();
        permission.setId(id);
        permission.setPermissionCode(code);
        permission.setType(type);
        permission.setParentId(parentId);
        permission.setName(code);
        permission.setStatus(status);
        permission.setSort(sort);
        permission.setVersion(version);
        return permission;
    }
}
