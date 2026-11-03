package com.recruit.auth.service;

import com.recruit.auth.dto.MenuNodeView;
import com.recruit.auth.entity.IamPermission;
import com.recruit.auth.mapper.IamPermissionMapper;
import com.recruit.auth.mapper.IamRoleMapper;
import com.recruit.common.constant.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 角色/权限查询、动态菜单与权限缓存维护。
 *
 * <p>权限码按用户 ID 现查，保证与库一致；P2-6 再叠加
 * {@code auth:perm:{userId}:{ver}} 缓存，角色/权限变更时递增 {@code auth:permver} 即可让旧缓存失效。</p>
 *
 * <p>{@code SUPER_ADMIN} 在这里显式判断：直接返回全部启用权限/菜单，
 * 不依赖“角色名散落各处”的重复判断（docs/4 §5.2.5）。</p>
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    /** 超级管理员角色码。 */
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    private final IamRoleMapper roleMapper;
    private final IamPermissionMapper permissionMapper;
    private final StringRedisTemplate redisTemplate;

    /**
     * 查询用户生效的角色码。
     *
     * @param userId 用户 ID
     * @return 角色码列表
     */
    public List<String> roleCodes(Long userId) {
        return roleMapper.selectActiveRoleCodesByUserId(userId);
    }

    /**
     * 查询用户生效的权限码。
     *
     * @param userId 用户 ID
     * @return 权限码列表；超级管理员返回全部启用权限
     */
    public List<String> permissionCodes(Long userId) {
        return isSuperAdmin(userId)
                ? permissionMapper.selectAllActivePermissionCodes()
                : permissionMapper.selectActivePermissionCodesByUserId(userId);
    }

    /**
     * 当前用户的动态菜单树（A-06）。
     *
     * <p>只取 ACTIVE 的 MENU 节点；父节点不可见时该节点提升为根节点，避免整棵子树消失。</p>
     *
     * @param userId 用户 ID
     * @return 菜单树
     */
    public List<MenuNodeView> menus(Long userId) {
        List<IamPermission> rows = isSuperAdmin(userId)
                ? permissionMapper.selectActiveMenus()
                : permissionMapper.selectActiveMenusByUserId(userId);
        return buildMenuTree(rows);
    }

    /**
     * 是否超级管理员。
     *
     * @param userId 用户 ID，可为 null
     * @return 命中 SUPER_ADMIN 角色返回 true
     */
    public boolean isSuperAdmin(Long userId) {
        return userId != null && roleCodes(userId).contains(ROLE_SUPER_ADMIN);
    }

    /**
     * 递增用户权限版本，并清理该用户的权限缓存。
     *
     * <p>两步都要做：版本号变了旧缓存自然不再命中；再删一次是为了防止 {@code permver}
     * 被淘汰回退到 0 时又读到旧版本缓存（docs/4 §7.2.2）。</p>
     *
     * <p>Redis 异常直接抛出，让角色变更事务回滚：收权不能因为缓存刷新失败而“看起来成功”。</p>
     *
     * @param userId 用户 ID
     * @return 递增后的版本号
     */
    public long bumpVersion(Long userId) {
        Long version = redisTemplate.opsForValue()
                .increment(RedisKeys.authPermVersion(Long.toString(userId)));
        Set<String> cacheKeys = redisTemplate.keys(RedisKeys.AUTH_PERM + userId + ":*");
        if (cacheKeys != null && !cacheKeys.isEmpty()) {
            redisTemplate.delete(cacheKeys);
        }
        return version == null ? 0L : version;
    }

    /**
     * 批量失效权限缓存（角色授权变更时按绑定用户逐个失效）。
     *
     * @param userIds 受影响的用户 ID，可为 null
     */
    public void bumpVersions(Collection<Long> userIds) {
        if (userIds == null) {
            return;
        }
        for (Long userId : new LinkedHashSet<>(userIds)) {
            if (userId != null) {
                bumpVersion(userId);
            }
        }
    }

    /**
     * 权限定义变化（新增/修改/删除）后清理全部权限缓存。
     *
     * <p>用 SCAN 而不是 KEYS，避免在 Redis 上阻塞其他命令；缓存清空后下一次请求回源 DB。</p>
     */
    public void evictAllPermissionCaches() {
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(RedisKeys.AUTH_PERM + "*").count(500).build())) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private List<MenuNodeView> buildMenuTree(List<IamPermission> rows) {
        Set<Long> ids = new LinkedHashSet<>();
        for (IamPermission row : rows) {
            ids.add(row.getId());
        }
        Map<Long, List<IamPermission>> byParent = new LinkedHashMap<>();
        List<IamPermission> roots = new ArrayList<>();
        for (IamPermission row : rows) {
            if (row.getParentId() == null || !ids.contains(row.getParentId())) {
                roots.add(row);
            } else {
                byParent.computeIfAbsent(row.getParentId(), key -> new ArrayList<>()).add(row);
            }
        }
        List<MenuNodeView> result = new ArrayList<>(roots.size());
        for (IamPermission root : roots) {
            result.add(toMenuNode(root, byParent));
        }
        return result;
    }

    private MenuNodeView toMenuNode(IamPermission row, Map<Long, List<IamPermission>> byParent) {
        List<MenuNodeView> children = new ArrayList<>();
        for (IamPermission child : byParent.getOrDefault(row.getId(), List.of())) {
            children.add(toMenuNode(child, byParent));
        }
        return new MenuNodeView(
                String.valueOf(row.getId()),
                row.getParentId() == null ? null : String.valueOf(row.getParentId()),
                row.getPermissionCode(),
                row.getName(),
                row.getType(),
                row.getRoutePath(),
                row.getComponent(),
                row.getIcon(),
                row.getSort() == null ? 0 : row.getSort(),
                children);
    }
}
