package com.recruit.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.auth.dto.MenuNodeView;
import com.recruit.auth.entity.IamPermission;
import com.recruit.auth.mapper.IamPermissionMapper;
import com.recruit.auth.mapper.IamRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PermissionService} 的权限码缓存、动态菜单与缓存失效测试（不连库、不连 Redis）。
 */
@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private IamRoleMapper roleMapper;
    @Mock
    private IamPermissionMapper permissionMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private PermissionService service;

    @BeforeEach
    void setUp() {
        service = new PermissionService(roleMapper, permissionMapper, redisTemplate, new ObjectMapper());
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void dataScopePicksWidestRoleScope() {
        when(roleMapper.selectActiveDataScopesByUserId(3L))
                .thenReturn(List.of("SELF", "DEPARTMENT", "ASSIGNED"));

        assertEquals("DEPARTMENT", service.dataScope(3L));
    }

    @Test
    void dataScopeFallsBackToSelfWithoutRoles() {
        when(roleMapper.selectActiveDataScopesByUserId(3L)).thenReturn(List.of());
        assertEquals("SELF", service.dataScope(3L));
        assertEquals("SELF", service.dataScope(null));
    }

    @Test
    void permissionCodesReturnsAllForSuperAdmin() {
        when(roleMapper.selectActiveRoleCodesByUserId(1L)).thenReturn(List.of("SUPER_ADMIN"));
        when(permissionMapper.selectAllActivePermissionCodes()).thenReturn(List.of("a", "b"));

        assertEquals(List.of("a", "b"), service.permissionCodes(1L));
        verify(permissionMapper, never()).selectActivePermissionCodesByUserId(1L);
    }

    @Test
    void permissionCodesUsesRoleGrantsForNormalUser() {
        when(roleMapper.selectActiveRoleCodesByUserId(2L)).thenReturn(List.of("RECRUITER"));
        when(permissionMapper.selectActivePermissionCodesByUserId(2L)).thenReturn(List.of("interview:job:view"));

        assertEquals(List.of("interview:job:view"), service.permissionCodes(2L));
    }

    @Test
    void permissionCodesUsesCachedValueWhenVersionMatches() {
        when(valueOperations.get("auth:permver:2")).thenReturn("4");
        when(valueOperations.get("auth:perm:2:4")).thenReturn("[\"cached:a\"]");

        assertEquals(List.of("cached:a"), service.permissionCodes(2L));
        verify(roleMapper, never()).selectActiveRoleCodesByUserId(2L);
        verify(permissionMapper, never()).selectActivePermissionCodesByUserId(2L);
    }

    @Test
    void permissionCodesTreatsEmptyCachedListAsHit() {
        when(valueOperations.get("auth:permver:2")).thenReturn("4");
        when(valueOperations.get("auth:perm:2:4")).thenReturn("[]");

        assertTrue(service.permissionCodes(2L).isEmpty());
        verify(permissionMapper, never()).selectActivePermissionCodesByUserId(2L);
    }

    @Test
    void permissionCodesCachesDatabaseResultWithVersionAndTtl() {
        when(valueOperations.get("auth:permver:2")).thenReturn("4");
        when(valueOperations.get("auth:perm:2:4")).thenReturn(null);
        when(roleMapper.selectActiveRoleCodesByUserId(2L)).thenReturn(List.of("RECRUITER"));
        when(permissionMapper.selectActivePermissionCodesByUserId(2L)).thenReturn(List.of("a", "b"));

        assertEquals(List.of("a", "b"), service.permissionCodes(2L));
        verify(valueOperations).set("auth:perm:2:4", "[\"a\",\"b\"]", Duration.ofMinutes(30));
    }

    @Test
    void permissionCodesFallsBackToDatabaseWhenRedisIsDown() {
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("redis down"));
        when(roleMapper.selectActiveRoleCodesByUserId(2L)).thenReturn(List.of("RECRUITER"));
        when(permissionMapper.selectActivePermissionCodesByUserId(2L)).thenReturn(List.of("a"));

        assertEquals(List.of("a"), service.permissionCodes(2L));
    }

    @Test
    void permissionCodesRepairsMalformedCacheFromDatabase() {
        when(valueOperations.get("auth:permver:2")).thenReturn("4");
        when(valueOperations.get("auth:perm:2:4")).thenReturn("not-json");
        when(roleMapper.selectActiveRoleCodesByUserId(2L)).thenReturn(List.of("RECRUITER"));
        when(permissionMapper.selectActivePermissionCodesByUserId(2L)).thenReturn(List.of("a"));

        assertEquals(List.of("a"), service.permissionCodes(2L));
        verify(valueOperations).set("auth:perm:2:4", "[\"a\"]", Duration.ofMinutes(30));
    }

    @Test
    void skipsCacheWriteWhenVersionChangesDuringDatabaseLoad() {
        when(valueOperations.get("auth:permver:2")).thenReturn("0", "1");
        when(valueOperations.get("auth:perm:2:0")).thenReturn(null);
        when(roleMapper.selectActiveRoleCodesByUserId(2L)).thenReturn(List.of("RECRUITER"));
        when(permissionMapper.selectActivePermissionCodesByUserId(2L)).thenReturn(List.of("old"));

        assertEquals(List.of("old"), service.permissionCodes(2L));
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void versionBumpMakesOldCacheUnreachable() {
        when(valueOperations.get("auth:permver:2")).thenReturn("0");
        when(valueOperations.get("auth:perm:2:0")).thenReturn("[\"old\"]");
        assertEquals(List.of("old"), service.permissionCodes(2L));

        // 并发管理员改了角色：版本递增 + 旧 key 删除，旧缓存自此不可达
        when(valueOperations.increment("auth:permver:2")).thenReturn(1L);
        when(redisTemplate.keys("auth:perm:2:*")).thenReturn(Set.of("auth:perm:2:0"));
        service.bumpVersion(2L);

        when(valueOperations.get("auth:permver:2")).thenReturn("1");
        when(valueOperations.get("auth:perm:2:1")).thenReturn(null);
        when(roleMapper.selectActiveRoleCodesByUserId(2L)).thenReturn(List.of("RECRUITER"));
        when(permissionMapper.selectActivePermissionCodesByUserId(2L)).thenReturn(List.of("new"));

        assertEquals(List.of("new"), service.permissionCodes(2L));
        verify(redisTemplate).delete(Set.of("auth:perm:2:0"));
        verify(valueOperations).set("auth:perm:2:1", "[\"new\"]", Duration.ofMinutes(30));
    }

    @Test
    void menusBuildsTreeForNormalUser() {
        when(roleMapper.selectActiveRoleCodesByUserId(2L)).thenReturn(List.of("RECRUITER"));
        when(permissionMapper.selectActiveMenusByUserId(2L)).thenReturn(List.of(
                menu(1L, "interview", null, 10),
                menu(2L, "interview:job:view", 1L, 1),
                menu(3L, "orphan", 99L, 2)));

        List<MenuNodeView> menus = service.menus(2L);

        assertEquals(2, menus.size());
        assertEquals("interview", menus.get(0).code());
        assertEquals(1, menus.get(0).children().size());
        assertEquals("interview:job:view", menus.get(0).children().get(0).code());
        assertEquals("orphan", menus.get(1).code());
    }

    @Test
    void menusReturnsAllActiveMenusForSuperAdmin() {
        when(roleMapper.selectActiveRoleCodesByUserId(1L)).thenReturn(List.of("SUPER_ADMIN"));
        when(permissionMapper.selectActiveMenus()).thenReturn(List.of(menu(1L, "interview", null, 10)));

        List<MenuNodeView> menus = service.menus(1L);

        assertEquals(1, menus.size());
        verify(permissionMapper, never()).selectActiveMenusByUserId(1L);
    }

    @Test
    void bumpVersionIncrementsAndDeletesUserCache() {
        when(valueOperations.increment("auth:permver:5")).thenReturn(3L);
        when(redisTemplate.keys("auth:perm:5:*")).thenReturn(Set.of("auth:perm:5:2"));

        assertEquals(3L, service.bumpVersion(5L));
        verify(redisTemplate).delete(Set.of("auth:perm:5:2"));
    }

    @Test
    void bumpVersionsSkipsNullAndDeduplicatesUsers() {
        when(valueOperations.increment("auth:permver:5")).thenReturn(1L);
        when(valueOperations.increment("auth:permver:6")).thenReturn(1L);
        when(redisTemplate.keys("auth:perm:5:*")).thenReturn(Set.of());
        when(redisTemplate.keys("auth:perm:6:*")).thenReturn(null);

        service.bumpVersions(java.util.Arrays.asList(5L, null, 5L, 6L));

        verify(valueOperations).increment("auth:permver:5");
        verify(valueOperations).increment("auth:permver:6");
        verify(redisTemplate, never()).delete(anyCollection());
    }

    @Test
    void evictAllPermissionCachesDeletesScannedKeys() {
        @SuppressWarnings("unchecked")
        Cursor<String> cursor = org.mockito.Mockito.mock(Cursor.class);
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        doAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(0);
            consumer.accept("auth:perm:1:0");
            consumer.accept("auth:perm:2:0");
            return null;
        }).when(cursor).forEachRemaining(any());

        service.evictAllPermissionCaches();

        verify(redisTemplate).delete(List.of("auth:perm:1:0", "auth:perm:2:0"));
    }

    private static IamPermission menu(long id, String code, Long parentId, int sort) {
        IamPermission permission = new IamPermission();
        permission.setId(id);
        permission.setPermissionCode(code);
        permission.setName(code);
        permission.setType("MENU");
        permission.setParentId(parentId);
        permission.setSort(sort);
        permission.setStatus("ACTIVE");
        return permission;
    }
}
