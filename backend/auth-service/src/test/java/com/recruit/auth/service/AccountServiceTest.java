package com.recruit.auth.service;

import com.recruit.auth.dto.MeView;
import com.recruit.auth.dto.RegisterForm;
import com.recruit.auth.dto.RegisterResult;
import com.recruit.auth.entity.IamRole;
import com.recruit.auth.entity.IamUser;
import com.recruit.auth.entity.IamUserRole;
import com.recruit.auth.mapper.IamDepartmentMapper;
import com.recruit.auth.mapper.IamRoleMapper;
import com.recruit.auth.mapper.IamUserMapper;
import com.recruit.auth.mapper.IamUserRoleMapper;
import com.recruit.auth.support.PhoneCipher;
import com.recruit.common.exception.BizException;
import com.recruit.common.idempotency.IdempotencyService;
import com.recruit.common.result.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 候选人注册（A-02）与当前用户视图（A-05）。
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private IamUserMapper userMapper;
    @Mock
    private IamRoleMapper roleMapper;
    @Mock
    private IamUserRoleMapper userRoleMapper;
    @Mock
    private IamDepartmentMapper departmentMapper;
    @Mock
    private PermissionService permissionService;
    @Mock
    private MustChangePasswordService mustChangePasswordService;
    @Mock
    private AuditService auditService;
    @Mock
    private IdempotencyService idempotencyService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final PhoneCipher phoneCipher =
            new PhoneCipher(Base64.getEncoder().encodeToString(new byte[32]));
    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(userMapper, roleMapper, userRoleMapper, departmentMapper,
                permissionService, mustChangePasswordService, auditService, phoneCipher,
                passwordEncoder, idempotencyService);
    }

    @Test
    void registerCreatesCandidateAndBindsRole() {
        when(userMapper.countByUsername("cand01")).thenReturn(0L);
        when(roleMapper.selectByRoleCode("CANDIDATE")).thenReturn(role(40005L));
        when(userMapper.insert(any(IamUser.class))).thenAnswer(invocation -> {
            IamUser user = invocation.getArgument(0);
            user.setId(10006L);
            return 1;
        });

        RegisterResult result = service.register(form("cand01", null), null, request());

        assertThat(result.userId()).isEqualTo("10006");
        assertThat(result.userType()).isEqualTo("CANDIDATE");
        ArgumentCaptor<IamUser> inserted = ArgumentCaptor.forClass(IamUser.class);
        verify(userMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getUserType()).isEqualTo("CANDIDATE");
        assertThat(inserted.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(passwordEncoder.matches("secret-123", inserted.getValue().getPasswordHash())).isTrue();
        ArgumentCaptor<IamUserRole> relation = ArgumentCaptor.forClass(IamUserRole.class);
        verify(userRoleMapper).insert(relation.capture());
        assertThat(relation.getValue().getUserId()).isEqualTo(10006L);
        assertThat(relation.getValue().getRoleId()).isEqualTo(40005L);
        verify(auditService).recordQuietly(eq(10006L), eq("REGISTER"), eq("USER"), eq("10006"),
                eq(true), anyString(), any());
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userMapper.countByUsername("cand01")).thenReturn(1L);

        assertThatThrownBy(() -> service.register(form("cand01", null), null, request()))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
        verify(userMapper, never()).insert(any(IamUser.class));
    }

    @Test
    void registerRejectsMissingCandidateRole() {
        when(userMapper.countByUsername("cand01")).thenReturn(0L);
        when(roleMapper.selectByRoleCode("CANDIDATE")).thenReturn(null);

        assertThatThrownBy(() -> service.register(form("cand01", null), null, request()))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
        verify(userMapper, never()).insert(any(IamUser.class));
    }

    @Test
    void registerRejectsDuplicatePhone() {
        RegisterForm form = form("cand01", "13800138000");
        when(userMapper.countByUsername("cand01")).thenReturn(0L);
        when(roleMapper.selectByRoleCode("CANDIDATE")).thenReturn(role(40005L));
        when(userMapper.countByPhoneHash(anyString())).thenReturn(1L);

        assertThatThrownBy(() -> service.register(form, null, request()))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
        verify(userMapper, never()).insert(any(IamUser.class));
    }

    @Test
    void registerWithKeyUsesIdempotencyScope() {
        RegisterResult expected = new RegisterResult("10006", "cand01", "候选人-孙", "CANDIDATE");
        when(idempotencyService.execute(eq("auth.register"), eq("key-1"), eq(RegisterResult.class), any()))
                .thenReturn(expected);

        RegisterResult result = service.register(form("cand01", null), "key-1", request());

        assertThat(result).isSameAs(expected);
        verify(userMapper, never()).countByUsername(anyString());
    }

    @Test
    void meReturnsMaskedPhoneAndAuthorization() {
        IamUser user = new IamUser();
        user.setId(10006L);
        user.setUsername("cand01");
        user.setDisplayName("候选人-孙");
        user.setUserType("CANDIDATE");
        user.setPhoneEnc(phoneCipher.encrypt("13800138000"));
        user.setDeptId(20001L);
        when(userMapper.selectById(10006L)).thenReturn(user);
        when(departmentMapper.selectSubtreeIds(20001L)).thenReturn(List.of(20001L, 20002L));
        when(permissionService.roleCodes(10006L)).thenReturn(List.of("CANDIDATE"));
        when(permissionService.permissionCodes(10006L)).thenReturn(List.of("portal:job:view"));
        when(permissionService.dataScope(10006L)).thenReturn("SELF");
        when(mustChangePasswordService.isMarked(10006L)).thenReturn(false);

        MeView view = service.me(10006L);

        assertThat(view.userId()).isEqualTo("10006");
        assertThat(view.phoneMasked()).isEqualTo("138****8000");
        assertThat(view.deptId()).isEqualTo("20001");
        assertThat(view.deptIds()).containsExactly("20001", "20002");
        assertThat(view.roles()).containsExactly("CANDIDATE");
        assertThat(view.permissions()).containsExactly("portal:job:view");
        assertThat(view.dataScope()).isEqualTo("SELF");
    }

    @Test
    void meRejectsUnknownUser() {
        when(userMapper.selectById(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> service.me(99999L))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static RegisterForm form(String username, String phone) {
        RegisterForm form = new RegisterForm();
        form.setUsername(username);
        form.setPassword("secret-123");
        form.setDisplayName("候选人-孙");
        form.setPhone(phone);
        return form;
    }

    private static IamRole role(long id) {
        IamRole role = new IamRole();
        role.setId(id);
        role.setRoleCode("CANDIDATE");
        role.setStatus("ACTIVE");
        return role;
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
        request.setRemoteAddr("10.0.0.1");
        return request;
    }
}
