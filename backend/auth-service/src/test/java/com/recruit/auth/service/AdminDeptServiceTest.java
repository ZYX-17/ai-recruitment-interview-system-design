package com.recruit.auth.service;

import com.recruit.auth.dto.CreateDeptForm;
import com.recruit.auth.dto.DeptNodeView;
import com.recruit.auth.dto.UpdateDeptForm;
import com.recruit.auth.entity.IamDepartment;
import com.recruit.auth.mapper.IamDepartmentMapper;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * {@link AdminDeptService} 的部门树与祖级路径维护测试（不连库、不连 Redis）。
 */
@ExtendWith(MockitoExtension.class)
class AdminDeptServiceTest {

    @Mock
    private IamDepartmentMapper departmentMapper;
    @Mock
    private AuditService auditService;

    private AdminDeptService service;

    @BeforeEach
    void setUp() {
        service = new AdminDeptService(departmentMapper, auditService);
    }

    @Test
    void treeBuildsHierarchyAndPromotesOrphans() {
        when(departmentMapper.selectAll()).thenReturn(List.of(
                dept(20000L, null, "0", "总公司", "ACTIVE", 10, 0),
                dept(20001L, 20000L, "0,20000", "招聘部", "ACTIVE", 10, 0),
                dept(20009L, 99999L, "0,99999", "孤儿部门", "ACTIVE", 20, 0)));

        List<DeptNodeView> tree = service.tree();

        assertEquals(2, tree.size());
        assertEquals("总公司", tree.get(0).deptName());
        assertEquals(1, tree.get(0).children().size());
        assertEquals("招聘部", tree.get(0).children().get(0).deptName());
        assertEquals("孤儿部门", tree.get(1).deptName());
    }

    @Test
    void createRootUsesZeroAncestors() {
        CreateDeptForm form = createForm("新部门", null);
        when(departmentMapper.countByDeptNameAndParentId("新部门", null)).thenReturn(0L);
        when(departmentMapper.insert(any(IamDepartment.class))).thenAnswer(invocation -> {
            IamDepartment saved = invocation.getArgument(0);
            saved.setId(20010L);
            return 1;
        });

        DeptNodeView node = service.create(form, 1L, "127.0.0.1");

        assertEquals("20010", node.id());
        ArgumentCaptor<IamDepartment> captor = ArgumentCaptor.forClass(IamDepartment.class);
        verify(departmentMapper).insert(captor.capture());
        assertEquals("0", captor.getValue().getAncestors());
        verify(auditService).recordQuietly(eq(1L), eq("DEPT_CREATE"), eq("DEPT"),
                eq("20010"), eq(true), eq("127.0.0.1"), any());
    }

    @Test
    void createChildAppendsParentAncestors() {
        CreateDeptForm form = createForm("招聘二部", 20001L);
        when(departmentMapper.selectById(20001L))
                .thenReturn(dept(20001L, 20000L, "0,20000", "招聘部", "ACTIVE", 10, 0));
        when(departmentMapper.countByDeptNameAndParentId("招聘二部", 20001L)).thenReturn(0L);
        when(departmentMapper.insert(any(IamDepartment.class))).thenAnswer(invocation -> {
            IamDepartment saved = invocation.getArgument(0);
            saved.setId(20011L);
            return 1;
        });

        DeptNodeView node = service.create(form, 1L, "127.0.0.1");

        assertEquals("20001", node.parentId());
        ArgumentCaptor<IamDepartment> captor = ArgumentCaptor.forClass(IamDepartment.class);
        verify(departmentMapper).insert(captor.capture());
        assertEquals("0,20000,20001", captor.getValue().getAncestors());
    }

    @Test
    void createRejectsDuplicateNameUnderSameParent() {
        CreateDeptForm form = createForm("招聘部", 20000L);
        when(departmentMapper.selectById(20000L))
                .thenReturn(dept(20000L, null, "0", "总公司", "ACTIVE", 10, 0));
        when(departmentMapper.countByDeptNameAndParentId("招聘部", 20000L)).thenReturn(1L);

        BizException error = assertThrows(BizException.class,
                () -> service.create(form, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
        verify(departmentMapper, never()).insert(any(IamDepartment.class));
    }

    @Test
    void createRejectsInactiveParent() {
        CreateDeptForm form = createForm("新部门", 20001L);
        when(departmentMapper.selectById(20001L))
                .thenReturn(dept(20001L, 20000L, "0,20000", "招聘部", "DISABLED", 10, 0));

        BizException error = assertThrows(BizException.class,
                () -> service.create(form, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
    }

    @Test
    void updateRejectsSelfAsParent() {
        when(departmentMapper.selectById(20001L))
                .thenReturn(dept(20001L, 20000L, "0,20000", "招聘部", "ACTIVE", 10, 1));

        UpdateDeptForm form = updateForm(1);
        form.setParentId(20001L);

        BizException error = assertThrows(BizException.class,
                () -> service.update(20001L, form, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
    }

    @Test
    void updateRejectsCycle() {
        when(departmentMapper.selectById(20001L))
                .thenReturn(dept(20001L, 20000L, "0,20000", "招聘部", "ACTIVE", 10, 1));
        when(departmentMapper.selectById(20002L))
                .thenReturn(dept(20002L, 20001L, "0,20000,20001", "招聘二部", "ACTIVE", 10, 0));

        UpdateDeptForm form = updateForm(1);
        form.setParentId(20002L);

        BizException error = assertThrows(BizException.class,
                () -> service.update(20001L, form, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
        verify(departmentMapper, never()).updateDept(anyLong(), anyString(), any(), any(),
                anyInt(), anyString(), anyInt());
    }

    @Test
    void updateMovesSubtreeAndRewritesDescendants() {
        IamDepartment current = dept(20001L, 20000L, "0,20000", "招聘部", "ACTIVE", 10, 2);
        IamDepartment moved = dept(20001L, 20003L, "0,20000,20003", "招聘部", "ACTIVE", 10, 3);
        when(departmentMapper.selectById(20001L)).thenReturn(current, moved);
        when(departmentMapper.selectById(20003L))
                .thenReturn(dept(20003L, 20000L, "0,20000", "产品部", "ACTIVE", 30, 0));
        when(departmentMapper.selectById(20000L))
                .thenReturn(dept(20000L, null, "0", "总公司", "ACTIVE", 10, 0));
        when(departmentMapper.countByDeptNameAndParentIdExcludingId("招聘部", 20003L, 20001L))
                .thenReturn(0L);
        when(departmentMapper.selectMaxAncestorsLength(20001L)).thenReturn(18);
        when(departmentMapper.updateDept(eq(20001L), eq("招聘部"), eq(20003L),
                eq("0,20000,20003"), eq(10), eq("ACTIVE"), eq(2))).thenReturn(1);

        UpdateDeptForm form = updateForm(2);
        form.setParentId(20003L);

        DeptNodeView node = service.update(20001L, form, 1L, "127.0.0.1");

        assertEquals("20003", node.parentId());
        verify(departmentMapper).updateDescendantAncestors(20001L, "0,20000", "0,20000,20003");
        verify(auditService).recordQuietly(eq(1L), eq("DEPT_UPDATE"), eq("DEPT"),
                eq("20001"), eq(true), eq("127.0.0.1"), any());
    }

    @Test
    void updateReturnsVersionConflict() {
        IamDepartment current = dept(20001L, 20000L, "0,20000", "招聘部", "ACTIVE", 10, 1);
        when(departmentMapper.selectById(20001L)).thenReturn(current, current);
        when(departmentMapper.selectById(20000L))
                .thenReturn(dept(20000L, null, "0", "总公司", "ACTIVE", 10, 0));
        when(departmentMapper.countByDeptNameAndParentIdExcludingId("招聘部", 20000L, 20001L))
                .thenReturn(0L);
        when(departmentMapper.updateDept(eq(20001L), eq("招聘部"), eq(20000L),
                eq("0,20000"), eq(10), eq("ACTIVE"), eq(1))).thenReturn(0);

        UpdateDeptForm form = updateForm(1);
        form.setParentId(20000L);

        BizException error = assertThrows(BizException.class,
                () -> service.update(20001L, form, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.VERSION_CONFLICT, error.getErrorCode());
    }

    @Test
    void updateRejectsDisablingParentWithChildren() {
        when(departmentMapper.selectById(20000L))
                .thenReturn(dept(20000L, null, "0", "总公司", "ACTIVE", 10, 1));
        when(departmentMapper.countByDeptNameAndParentIdExcludingId("总公司", null, 20000L))
                .thenReturn(0L);
        when(departmentMapper.countByParentId(20000L)).thenReturn(2L);

        UpdateDeptForm form = updateForm(1);
        form.setStatus("DISABLED");

        BizException error = assertThrows(BizException.class,
                () -> service.update(20000L, form, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
        verify(departmentMapper, never()).updateDept(anyLong(), anyString(), isNull(), any(),
                anyInt(), anyString(), anyInt());
    }

    @Test
    void deleteRejectsChildren() {
        when(departmentMapper.selectById(20000L))
                .thenReturn(dept(20000L, null, "0", "总公司", "ACTIVE", 10, 0));
        when(departmentMapper.countByParentId(20000L)).thenReturn(1L);

        BizException error = assertThrows(BizException.class,
                () -> service.delete(20000L, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
        verify(departmentMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteRejectsUsers() {
        when(departmentMapper.selectById(20001L))
                .thenReturn(dept(20001L, 20000L, "0,20000", "招聘部", "ACTIVE", 10, 0));
        when(departmentMapper.countByParentId(20001L)).thenReturn(0L);
        when(departmentMapper.countUsersByDeptId(20001L)).thenReturn(3L);

        BizException error = assertThrows(BizException.class,
                () -> service.delete(20001L, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
        verify(departmentMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteRemovesEmptyLeaf() {
        when(departmentMapper.selectById(20001L))
                .thenReturn(dept(20001L, 20000L, "0,20000", "招聘部", "ACTIVE", 10, 0));
        when(departmentMapper.countByParentId(20001L)).thenReturn(0L);
        when(departmentMapper.countUsersByDeptId(20001L)).thenReturn(0L);

        service.delete(20001L, 1L, "127.0.0.1");

        verify(departmentMapper).deleteById(20001L);
        verify(auditService).recordQuietly(eq(1L), eq("DEPT_DELETE"), eq("DEPT"),
                eq("20001"), eq(true), eq("127.0.0.1"), any());
    }

    private static CreateDeptForm createForm(String name, Long parentId) {
        CreateDeptForm form = new CreateDeptForm();
        form.setDeptName(name);
        form.setParentId(parentId);
        return form;
    }

    private static UpdateDeptForm updateForm(int version) {
        UpdateDeptForm form = new UpdateDeptForm();
        form.setVersion(version);
        return form;
    }

    private static IamDepartment dept(long id, Long parentId, String ancestors, String name,
                                      String status, int sort, int version) {
        IamDepartment dept = new IamDepartment();
        dept.setId(id);
        dept.setParentId(parentId);
        dept.setAncestors(ancestors);
        dept.setDeptName(name);
        dept.setStatus(status);
        dept.setSort(sort);
        dept.setVersion(version);
        return dept;
    }
}
