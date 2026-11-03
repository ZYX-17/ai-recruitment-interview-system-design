package com.recruit.auth.service;

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

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PermissionService} 的权限码、动态菜单与缓存失效测试。
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
        service = new PermissionService(roleMapper, permissionMapper, redisTemplate);
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
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("auth:permver:5")).thenReturn(3L);
        when(redisTemplate.keys("auth:perm:5:*")).thenReturn(Set.of("auth:perm:5:2"));

        assertEquals(3L, service.bumpVersion(5L));
        verify(redisTemplate).delete(Set.of("auth:perm:5:2"));
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
