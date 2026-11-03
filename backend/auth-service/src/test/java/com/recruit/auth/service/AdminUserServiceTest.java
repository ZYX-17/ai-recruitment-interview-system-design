package com.recruit.auth.service;

import com.recruit.auth.dto.AdminUserView;
import com.recruit.auth.dto.AssignRolesForm;
import com.recruit.auth.dto.CreateUserForm;
import com.recruit.auth.dto.CreatedUserResult;
import com.recruit.auth.dto.ResetPasswordResult;
import com.recruit.auth.dto.UpdateUserForm;
import com.recruit.auth.dto.UserPageQuery;
import com.recruit.auth.entity.IamUser;
import com.recruit.auth.mapper.IamDepartmentMapper;
import com.recruit.auth.mapper.IamRoleMapper;
import com.recruit.auth.mapper.IamUserMapper;
import com.recruit.auth.mapper.IamUserRoleMapper;
import com.recruit.auth.mapper.row.AdminUserRow;
import com.recruit.auth.mapper.row.UserRoleRow;
import com.recruit.auth.support.PhoneCipher;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdminUserService} 的用户管理规则测试（不连库、不连 Redis）。
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    private static final String PHONE_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Mock
    private IamUserMapper userMapper;
    @Mock
    private IamUserRoleMapper userRoleMapper;
    @Mock
    private IamRoleMapper roleMapper;
    @Mock
    private IamDepartmentMapper departmentMapper;
    @Mock
    private TokenRevocationService tokenRevocationService;
    @Mock
    private MustChangePasswordService mustChangePasswordService;
    @Mock
    private PermissionService permissionService;
    @Mock
    private AuditService auditService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(userMapper, userRoleMapper, roleMapper, departmentMapper,
                passwordEncoder, new PhoneCipher(PHONE_KEY), tokenRevocationService,
                mustChangePasswordService, permissionService, auditService);
    }

    @Test
    void pageAppliesDepartmentScopeAndGroupsRoles() {
        when(roleMapper.selectActiveDataScopesByUserId(1L)).thenReturn(List.of("DEPARTMENT"));
        when(userMapper.selectById(1L)).thenReturn(user(1L, 20001L, "ACTIVE", 0));
        when(userMapper.countAdminPage(isNull(), isNull(), isNull(), eq(20001L), isNull())).thenReturn(1L);

        AdminUserRow row = new AdminUserRow();
        row.setId(5L);
        row.setUsername("hr02");
        row.setDisplayName("招聘专员2");
        row.setUserType("EMPLOYEE");
        row.setStatus("ACTIVE");
        row.setDeptId(20001L);
        row.setDeptName("招聘部");
        row.setVersion(0);
        when(userMapper.selectAdminPage(isNull(), isNull(), isNull(), eq(20001L), isNull(), eq(20L), eq(0L)))
                .thenReturn(List.of(row));

        UserRoleRow roleRow = new UserRoleRow();
        roleRow.setUserId(5L);
        roleRow.setRoleId(40002L);
        roleRow.setRoleCode("RECRUITER");
        roleRow.setRoleName("招聘专员");
        when(userMapper.selectRolesByUserIds(List.of(5L))).thenReturn(List.of(roleRow));

        PageResult<com.recruit.auth.dto.AdminUserView> result = service.page(new UserPageQuery(), 1L);

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getList().size());
        assertEquals("40002", result.getList().get(0).roles().get(0).id());
        assertEquals("招聘部", result.getList().get(0).deptName());
        assertNull(result.getList().get(0).phone());
    }

    @Test
    void pageScopesSelfForNonDepartmentRoles() {
        when(roleMapper.selectActiveDataScopesByUserId(7L)).thenReturn(List.of("SELF"));
        when(userMapper.countAdminPage(isNull(), isNull(), isNull(), isNull(), eq(7L))).thenReturn(0L);

        PageResult<com.recruit.auth.dto.AdminUserView> result = service.page(new UserPageQuery(), 7L);

        assertEquals(0, result.getTotal());
        assertTrue(result.getList().isEmpty());
        verify(userMapper, never()).selectAdminPage(any(), any(), any(), any(), any(), anyLong(), anyLong());
    }

    @Test
    void createRejectsDuplicateUsername() {
        CreateUserForm form = createForm("hr02");
        when(userMapper.countByUsername("hr02")).thenReturn(1L);

        BizException error = assertThrows(BizException.class, () -> service.create(form, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
        verify(userMapper, never()).insert(any(IamUser.class));
    }

    @Test
    void createRejectsUnknownRoles() {
        CreateUserForm form = createForm("hr02");
        form.setRoleIds(List.of(40002L));
        when(userMapper.countByUsername("hr02")).thenReturn(0L);
        when(roleMapper.countActiveByIds(List.of(40002L))).thenReturn(0L);

        BizException error = assertThrows(BizException.class, () -> service.create(form, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
        verify(userMapper, never()).insert(any(IamUser.class));
    }

    @Test
    void createGeneratesTemporaryPasswordAndMarksMustChange() {
        CreateUserForm form = createForm("hr02");
        form.setPhone("13800000000");
        when(userMapper.countByUsername("hr02")).thenReturn(0L);
        when(userMapper.countByPhoneHash(anyString())).thenReturn(0L);
        when(userMapper.insert(any(IamUser.class))).thenAnswer(invocation -> {
            IamUser saved = invocation.getArgument(0);
            saved.setId(10002L);
            return 1;
        });

        CreatedUserResult result = service.create(form, 1L, "127.0.0.1");

        assertEquals("10002", result.id());
        assertTrue(result.mustChangePassword());
        assertNotNull(result.initialPassword());
        assertEquals(16, result.initialPassword().length());

        ArgumentCaptor<IamUser> userCaptor = ArgumentCaptor.forClass(IamUser.class);
        verify(userMapper).insert(userCaptor.capture());
        IamUser saved = userCaptor.getValue();
        assertTrue(passwordEncoder.matches(result.initialPassword(), saved.getPasswordHash()));
        assertNotNull(saved.getPhoneEnc());
        assertNotNull(saved.getPhoneHash());
        assertEquals(64, saved.getPhoneHash().length());
        verify(mustChangePasswordService).mark(10002L);
    }

    @Test
    void createRejectsPhoneWhenCipherNotConfigured() {
        AdminUserService unconfigured = new AdminUserService(userMapper, userRoleMapper, roleMapper,
                departmentMapper, passwordEncoder, new PhoneCipher(""), tokenRevocationService,
                mustChangePasswordService, permissionService, auditService);
        CreateUserForm form = createForm("hr02");
        form.setPhone("13800000000");
        when(userMapper.countByUsername("hr02")).thenReturn(0L);
        when(userMapper.countByPhoneHash(anyString())).thenReturn(0L);

        BizException error = assertThrows(BizException.class, () -> unconfigured.create(form, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
        assertTrue(error.getMessage().contains("PHONE_ENC_KEY"));
    }

    @Test
    void updateReturnsVersionConflictWhenOptimisticLockMisses() {
        when(userMapper.selectById(5L)).thenReturn(user(5L, null, "ACTIVE", 3));
        when(userMapper.updateProfile(eq(5L), anyString(), isNull(), eq("ACTIVE"), eq(3))).thenReturn(0);

        UpdateUserForm form = new UpdateUserForm();
        form.setDisplayName("招聘专员3");
        form.setVersion(3);

        BizException error = assertThrows(BizException.class, () -> service.update(5L, form, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.VERSION_CONFLICT, error.getErrorCode());
    }

    @Test
    void deleteRejectsSelf() {
        BizException error = assertThrows(BizException.class, () -> service.delete(1L, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
        verify(userMapper, never()).softDelete(anyLong());
    }

    @Test
    void deleteRejectsLastSuperAdmin() {
        when(userMapper.selectById(5L)).thenReturn(user(5L, 20001L, "ACTIVE", 0));
        when(userMapper.countSuperAdminRoles(5L)).thenReturn(1L);
        when(userMapper.countOtherActiveSuperAdmins(5L)).thenReturn(0L);

        BizException error = assertThrows(BizException.class, () -> service.delete(5L, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
        verify(tokenRevocationService, never()).revokeAllSessions(anyLong());
    }

    @Test
    void deleteSoftDeletesAndRevokesSessions() {
        when(userMapper.selectById(5L)).thenReturn(user(5L, 20001L, "ACTIVE", 0));
        when(userMapper.countSuperAdminRoles(5L)).thenReturn(0L);
        when(userMapper.softDelete(5L)).thenReturn(1);

        service.delete(5L, 1L, "127.0.0.1");

        verify(userMapper).softDelete(5L);
        verify(tokenRevocationService).revokeAllSessions(5L);
    }

    @Test
    void resetPasswordReturnsTemporaryPasswordAndRevokesSessions() {
        when(userMapper.selectById(5L)).thenReturn(user(5L, 20001L, "ACTIVE", 0));
        when(userMapper.updatePasswordHash(eq(5L), anyString())).thenReturn(1);

        ResetPasswordResult result = service.resetPassword(5L, 1L, "127.0.0.1");

        assertTrue(result.mustChangePassword());
        assertEquals(16, result.temporaryPassword().length());
        verify(tokenRevocationService).revokeAllSessions(5L);
        verify(mustChangePasswordService).mark(5L);

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(userMapper).updatePasswordHash(eq(5L), hashCaptor.capture());
        assertTrue(passwordEncoder.matches(result.temporaryPassword(), hashCaptor.getValue()));
    }

    @Test
    void assignRolesReplacesRelationsAndRevokesSessions() {
        when(userMapper.selectById(5L)).thenReturn(user(5L, 20001L, "ACTIVE", 2));
        when(roleMapper.countActiveByIds(List.of(40002L, 40004L))).thenReturn(2L);
        when(roleMapper.selectActiveRoleCodesByUserId(5L)).thenReturn(List.of("RECRUITER"));
        when(userMapper.incrementVersion(5L, 2)).thenReturn(1);
        when(userMapper.selectAdminRowById(5L)).thenReturn(adminRow(5L, 3));
        when(userMapper.selectRolesByUserIds(List.of(5L))).thenReturn(List.of(
                roleRow(5L, 40002L, "RECRUITER", "招聘专员"),
                roleRow(5L, 40004L, "INTERVIEWER", "面试官")));

        AssignRolesForm form = new AssignRolesForm();
        form.setRoleIds(List.of(40002L, 40004L, 40002L));
        form.setVersion(2);

        AdminUserView view = service.assignRoles(5L, form, 1L, "127.0.0.1");

        assertEquals(2, view.roles().size());
        assertEquals(3, view.version());
        verify(userRoleMapper).deleteByUserId(5L);
        verify(userRoleMapper, times(2)).insert(any(com.recruit.auth.entity.IamUserRole.class));
        verify(tokenRevocationService).revokeAllSessions(5L);
        verify(permissionService).bumpVersion(5L);
        verify(auditService).recordQuietly(eq(1L), eq("USER_ASSIGN_ROLE"), eq("USER"),
                eq("5"), eq(true), eq("127.0.0.1"), any());
    }

    @Test
    void assignRolesRejectsUnknownRoles() {
        when(userMapper.selectById(5L)).thenReturn(user(5L, 20001L, "ACTIVE", 2));
        when(roleMapper.countActiveByIds(List.of(40002L, 49999L))).thenReturn(1L);

        AssignRolesForm form = new AssignRolesForm();
        form.setRoleIds(List.of(40002L, 49999L));

        BizException error = assertThrows(BizException.class,
                () -> service.assignRoles(5L, form, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
        verify(userRoleMapper, never()).deleteByUserId(5L);
    }

    @Test
    void assignRolesRejectsRemovingLastSuperAdmin() {
        when(userMapper.selectById(5L)).thenReturn(user(5L, 20001L, "ACTIVE", 2));
        when(roleMapper.countActiveByIds(List.of(40002L))).thenReturn(1L);
        when(roleMapper.selectActiveRoleCodesByUserId(5L)).thenReturn(List.of("SUPER_ADMIN"));
        when(roleMapper.countSuperAdminInIds(List.of(40002L))).thenReturn(0L);
        when(userMapper.countOtherActiveSuperAdmins(5L)).thenReturn(0L);

        AssignRolesForm form = new AssignRolesForm();
        form.setRoleIds(List.of(40002L));

        BizException error = assertThrows(BizException.class,
                () -> service.assignRoles(5L, form, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
        verify(userRoleMapper, never()).deleteByUserId(5L);
    }

    @Test
    void assignRolesReturnsVersionConflictWhenOptimisticLockMisses() {
        when(userMapper.selectById(5L)).thenReturn(user(5L, 20001L, "ACTIVE", 2));
        when(roleMapper.countActiveByIds(List.of(40002L))).thenReturn(1L);
        when(roleMapper.selectActiveRoleCodesByUserId(5L)).thenReturn(List.of("RECRUITER"));
        when(userMapper.incrementVersion(5L, 2)).thenReturn(0);

        AssignRolesForm form = new AssignRolesForm();
        form.setRoleIds(List.of(40002L));
        form.setVersion(2);

        BizException error = assertThrows(BizException.class,
                () -> service.assignRoles(5L, form, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.VERSION_CONFLICT, error.getErrorCode());
        verify(userRoleMapper, never()).deleteByUserId(5L);
    }

    @Test
    void updateRejectsDisablingLastSuperAdmin() {
        when(userMapper.selectById(5L)).thenReturn(user(5L, 20001L, "ACTIVE", 3));
        when(userMapper.countSuperAdminRoles(5L)).thenReturn(1L);
        when(userMapper.countOtherActiveSuperAdmins(5L)).thenReturn(0L);

        UpdateUserForm form = new UpdateUserForm();
        form.setStatus("DISABLED");
        form.setVersion(3);

        BizException error = assertThrows(BizException.class,
                () -> service.update(5L, form, 1L, "127.0.0.1"));

        assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
        verify(userMapper, never()).updateProfile(anyLong(), anyString(), any(), anyString(), any());
    }

    @Test
    void kickRevokesAllSessionsAndWritesAudit() {
        when(userMapper.selectById(5L)).thenReturn(user(5L, 20001L, "ACTIVE", 0));
        when(tokenRevocationService.revokeAllSessions(5L)).thenReturn(4L);

        service.kick(5L, 1L, "127.0.0.1");

        verify(tokenRevocationService).revokeAllSessions(5L);
        verify(auditService).recordQuietly(eq(1L), eq("USER_KICK"), eq("USER"),
                eq("5"), eq(true), eq("127.0.0.1"), any());
    }

    private static AdminUserRow adminRow(long id, int version) {
        AdminUserRow row = new AdminUserRow();
        row.setId(id);
        row.setUsername("user" + id);
        row.setDisplayName("用户" + id);
        row.setUserType("EMPLOYEE");
        row.setStatus("ACTIVE");
        row.setDeptId(20001L);
        row.setDeptName("招聘部");
        row.setVersion(version);
        return row;
    }

    private static UserRoleRow roleRow(long userId, long roleId, String code, String name) {
        UserRoleRow row = new UserRoleRow();
        row.setUserId(userId);
        row.setRoleId(roleId);
        row.setRoleCode(code);
        row.setRoleName(name);
        return row;
    }

    private static IamUser user(long id, Long deptId, String status, int version) {
        IamUser user = new IamUser();
        user.setId(id);
        user.setUsername("user" + id);
        user.setDisplayName("用户" + id);
        user.setUserType("EMPLOYEE");
        user.setStatus(status);
        user.setDeptId(deptId);
        user.setVersion(version);
        return user;
    }

    private static CreateUserForm createForm(String username) {
        CreateUserForm form = new CreateUserForm();
        form.setUsername(username);
        form.setDisplayName("招聘专员2");
        form.setUserType("EMPLOYEE");
        form.setStatus("ACTIVE");
        return form;
    }
}
