package com.recruit.interview.service;

import com.recruit.common.context.UserContext;
import com.recruit.interview.dto.AuthzSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 数据范围解析（P4-7）。
 */
@ExtendWith(MockitoExtension.class)
class DataScopeServiceTest {

    @Mock
    private PermissionCacheService permissionCacheService;

    @InjectMocks
    private DataScopeService service;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void departmentScopeIncludesDescendantDepartments() {
        UserContext.set("10001", List.of("RECRUITER"));
        when(permissionCacheService.authz(10001L)).thenReturn(new AuthzSnapshot(
                List.of("interview:job:view"), List.of("RECRUITER"), "DEPARTMENT", "20001",
                List.of("20001", "20002")));

        DataScopeService.DataScope scope = service.current();

        assertThat(scope.scope()).isEqualTo("DEPARTMENT");
        assertThat(scope.canSee(20002L, 99999L)).isTrue();
        assertThat(scope.canSee(20003L, 99999L)).isFalse();
    }

    @Test
    void selfScopeOnlyAllowsOwnedResources() {
        UserContext.set("10001", List.of("INTERVIEWER"));
        when(permissionCacheService.authz(10001L)).thenReturn(new AuthzSnapshot(
                List.of(), List.of("INTERVIEWER"), "SELF", null, List.of()));

        DataScopeService.DataScope scope = service.current();

        assertThat(scope.canSee(20001L, 10001L)).isTrue();
        assertThat(scope.canSee(20001L, 10002L)).isFalse();
    }
}
