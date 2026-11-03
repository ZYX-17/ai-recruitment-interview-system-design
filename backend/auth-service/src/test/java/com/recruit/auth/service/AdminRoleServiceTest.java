package com.recruit.auth.service;

import com.recruit.auth.dto.AssignPermissionsForm;
import com.recruit.auth.dto.CreateRoleForm;
import com.recruit.auth.dto.RoleDetailView;
import com.recruit.auth.dto.RoleView;
import com.recruit.auth.dto.UpdateRoleForm;
import com.recruit.auth.entity.IamRole;
import com.recruit.auth.mapper.IamPermissionMapper;
import com.recruit.auth.mapper.IamRoleMapper;
import com.recruit.auth.mapper.IamRolePermissionMapper;
import com.recruit.auth.mapper.IamUserRoleMapper;
import com.recruit.auth.mapper.row.AdminRoleRow;
import com.recruit.auth.mapper.row.PermissionBriefRow;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdminRoleService} 的角色管理规则测试（不连库、不连 Redis）。
 */
@ExtendWith(MockitoExtension.class)
class AdminRoleServiceTest {

    @Mock
    private IamRoleMapper roleMapper;
    @Mock
    private IamRolePermissionMapper rolePermissionMapper;
    @Mock
    private IamPermissionMapper permissionMapper;
    @Mock
    private IamUserRoleMapper userRoleMapper;
    @Mock
    private PermissionService permissionService;
    @Mock
    private AuditService auditService;

    private AdminRoleService service;

    @BeforeEach
    void setUp() {
        service = new AdminRoleService(roleMapper, rolePermissionMapper, permissionMapper,
                userRoleMapper, permissionService, auditService);
    }

    @Test
    void createRejectsDuplicateRoleCode() {
        when(roleMapper.selectByRoleCode("AUDITOR")).thenReturn(new IamRole());

        BizException error = assertThrows(BizException.class,
                () -> service.create(createForm("auditor"), 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
        verify(roleMapper, never()).insert(any(IamRole.class));
    }

    @Test
    void createRejectsSystemRoleForNonSuperAdmin() {
        when(roleMapper.selectByRoleCode("AUDITOR")).thenReturn(null);
        when(roleMapper.selectActiveRoleCodesByUserId(1L)).thenReturn(List.of("RECRUITER"));

        CreateRoleForm form = createForm("AUDITOR");
        form.setIsSystemRole(true);

        BizException error = assertThrows(BizException.class,
                () -> service.create(form, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.AUTH_FORBIDDEN, error.getErrorCode());
        verify(roleMapper, never()).insert(any(IamRole.class));
    }

    @Test
    void createNormalizesRoleCodeAndWritesAudit() {
        when(roleMapper.selectByRoleCode("AUDITOR")).thenReturn(null);
        when(roleMapper.insert(any(IamRole.class))).thenAnswer(invocation -> {
            IamRole saved = invocation.getArgument(0);
            saved.setId(40010L);
            return 1;
        });
        when(roleMapper.selectAdminRoleById(40010L)).thenReturn(roleRow(40010L, "AUDITOR", false, 0));

        RoleView view = service.create(createForm("auditor"), 1L, "127.0.0.1");

        assertEquals("40010", view.id());
        assertEquals("AUDITOR", view.roleCode());
        ArgumentCaptor<IamRole> captor = ArgumentCaptor.forClass(IamRole.class);
        verify(roleMapper).insert(captor.capture());
        assertEquals("AUDITOR", captor.getValue().getRoleCode());
        assertEquals("ACTIVE", captor.getValue().getStatus());
        verify(auditService).recordQuietly(eq(1L), eq("ROLE_CREATE"), eq("ROLE"),
                eq("40010"), eq(true), eq("127.0.0.1"), any());
    }

    @Test
    void updateRejectsSystemRoleDataScopeChange() {
        when(roleMapper.selectAdminRoleById(40001L)).thenReturn(roleRow(40001L, "SUPER_ADMIN", true, 2));

        UpdateRoleForm form = new UpdateRoleForm();
        form.setDataScope("DEPARTMENT");
        form.setVersion(2);

        BizException error = assertThrows(BizException.class,
                () -> service.update(40001L, form, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
        verify(roleMapper, never()).updateRole(anyLong(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void updateReturnsVersionConflict() {
        when(roleMapper.selectAdminRoleById(40010L)).thenReturn(roleRow(40010L, "AUDITOR", false, 2));
        when(roleMapper.updateRole(eq(40010L), anyString(), anyString(), anyString(), any(), eq(2))).thenReturn(0);

        UpdateRoleForm form = new UpdateRoleForm();
        form.setRoleName("审计员");
        form.setVersion(2);

        BizException error = assertThrows(BizException.class,
                () -> service.update(40010L, form, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.VERSION_CONFLICT, error.getErrorCode());
    }

    @Test
    void deleteRejectsSystemRole() {
        when(roleMapper.selectAdminRoleById(40004L)).thenReturn(roleRow(40004L, "INTERVIEWER", true, 0));

        BizException error = assertThrows(BizException.class,
                () -> service.delete(40004L, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
        verify(roleMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteRejectsRoleWithBoundUsers() {
        when(roleMapper.selectAdminRoleById(40010L)).thenReturn(roleRow(40010L, "AUDITOR", false, 0));
        when(userRoleMapper.countByRoleId(40010L)).thenReturn(3L);

        BizException error = assertThrows(BizException.class,
                () -> service.delete(40010L, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
        verify(rolePermissionMapper, never()).deleteByRoleId(anyLong());
    }

    @Test
    void deleteRemovesRoleAndPermissions() {
        when(roleMapper.selectAdminRoleById(40010L)).thenReturn(roleRow(40010L, "AUDITOR", false, 0));
        when(userRoleMapper.countByRoleId(40010L)).thenReturn(0L);

        service.delete(40010L, 1L, "127.0.0.1");

        verify(rolePermissionMapper).deleteByRoleId(40010L);
        verify(roleMapper).deleteById(40010L);
        verify(auditService).recordQuietly(eq(1L), eq("ROLE_DELETE"), eq("ROLE"),
                eq("40010"), eq(true), eq("127.0.0.1"), any());
    }

    @Test
    void assignPermissionsRejectsSuperAdminRole() {
        when(roleMapper.selectAdminRoleById(40001L)).thenReturn(roleRow(40001L, "SUPER_ADMIN", true, 0));

        AssignPermissionsForm form = new AssignPermissionsForm();
        form.setPermissionIds(List.of(30001L));

        BizException error = assertThrows(BizException.class,
                () -> service.assignPermissions(40001L, form, false, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
        verify(rolePermissionMapper, never()).deleteByRoleId(anyLong());
    }

    @Test
    void assignPermissionsRejectsUnknownPermission() {
        when(roleMapper.selectAdminRoleById(40010L)).thenReturn(roleRow(40010L, "AUDITOR", false, 0));
        when(permissionMapper.selectActiveByIds(List.of(30001L, 39999L)))
                .thenReturn(List.of(brief(30001L, "MENU", null)));

        AssignPermissionsForm form = new AssignPermissionsForm();
        form.setPermissionIds(List.of(30001L, 39999L));

        BizException error = assertThrows(BizException.class,
                () -> service.assignPermissions(40010L, form, false, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
        verify(rolePermissionMapper, never()).deleteByRoleId(anyLong());
    }

    @Test
    void assignPermissionsRejectsNonMenuForMenusEndpoint() {
        when(roleMapper.selectAdminRoleById(40010L)).thenReturn(roleRow(40010L, "AUDITOR", false, 0));
        when(permissionMapper.selectActiveByIds(List.of(30004L)))
                .thenReturn(List.of(brief(30004L, "BUTTON", null)));

        AssignPermissionsForm form = new AssignPermissionsForm();
        form.setPermissionIds(List.of(30004L));

        BizException error = assertThrows(BizException.class,
                () -> service.assignPermissions(40010L, form, true, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
    }

    @Test
    void assignPermissionsRejectsMissingParent() {
        when(roleMapper.selectAdminRoleById(40010L)).thenReturn(roleRow(40010L, "AUDITOR", false, 0));
        when(permissionMapper.selectActiveByIds(List.of(30002L)))
                .thenReturn(List.of(brief(30002L, "BUTTON", 30001L)));

        AssignPermissionsForm form = new AssignPermissionsForm();
        form.setPermissionIds(List.of(30002L));

        BizException error = assertThrows(BizException.class,
                () -> service.assignPermissions(40010L, form, false, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
    }

    @Test
    void assignPermissionsReplacesAndEvictsBoundUsers() {
        when(roleMapper.selectAdminRoleById(40010L)).thenReturn(roleRow(40010L, "AUDITOR", false, 3));
        when(permissionMapper.selectActiveByIds(List.of(30001L, 30002L))).thenReturn(List.of(
                brief(30001L, "MENU", null),
                brief(30002L, "BUTTON", 30001L)));
        when(roleMapper.incrementVersion(40010L, 3)).thenReturn(1);
        when(userRoleMapper.selectUserIdsByRoleId(40010L)).thenReturn(List.of(5L, 6L));
        when(rolePermissionMapper.selectPermissionIdsByRoleId(40010L)).thenReturn(List.of(30001L, 30002L));

        AssignPermissionsForm form = new AssignPermissionsForm();
        form.setPermissionIds(List.of(30001L, 30002L));
        form.setVersion(3);

        RoleDetailView view = service.assignPermissions(40010L, form, false, 1L, "127.0.0.1");

        assertEquals(List.of("30001", "30002"), view.permissionIds());
        verify(rolePermissionMapper).deleteByRoleId(40010L);
        verify(rolePermissionMapper, times(2)).insert(any(com.recruit.auth.entity.IamRolePermission.class));
        verify(permissionService).bumpVersions(List.of(5L, 6L));
        verify(auditService).recordQuietly(eq(1L), eq("ROLE_ASSIGN_PERMISSION"), eq("ROLE"),
                eq("40010"), eq(true), eq("127.0.0.1"), any());
    }

    private static CreateRoleForm createForm(String roleCode) {
        CreateRoleForm form = new CreateRoleForm();
        form.setRoleCode(roleCode);
        form.setRoleName("审计员");
        form.setDataScope("SELF");
        return form;
    }

    private static AdminRoleRow roleRow(long id, String roleCode, boolean systemRole, int version) {
        AdminRoleRow row = new AdminRoleRow();
        row.setId(id);
        row.setRoleCode(roleCode);
        row.setRoleName("角色" + id);
        row.setDataScope("ALL");
        row.setIsSystemRole(systemRole);
        row.setStatus("ACTIVE");
        row.setUserCount(0L);
        row.setVersion(version);
        return row;
    }

    private static PermissionBriefRow brief(long id, String type, Long parentId) {
        PermissionBriefRow row = new PermissionBriefRow();
        row.setId(id);
        row.setType(type);
        row.setParentId(parentId);
        return row;
    }
}
