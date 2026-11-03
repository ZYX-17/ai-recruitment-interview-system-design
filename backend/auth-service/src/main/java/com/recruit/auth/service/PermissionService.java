package com.recruit.auth.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.auth.dto.MenuNodeView;
import com.recruit.auth.entity.IamPermission;
import com.recruit.auth.mapper.IamPermissionMapper;
import com.recruit.auth.mapper.IamRoleMapper;
import com.recruit.common.constant.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
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
 * <p>权限码按 {@code auth:perm:{userId}:{ver}} 读缓存（TTL 30 分钟），版本号取自
 * {@code auth:permver:{userId}}；角色/用户角色变更时递增版本并删除旧缓存，
 * 权限定义变更时清空全部 {@code auth:perm:*}，保证下一次请求即时生效。</p>
 *
 * <p>缓存故障不放大权限：读路径降级回源数据库，写路径（{@link #bumpVersion}）直接抛出，
 * 让角色变更事务回滚，避免“收权看起来成功、实际仍可访问”。</p>
 *
 * <p>{@code SUPER_ADMIN} 在这里显式判断：直接返回全部启用权限/菜单，
 * 不依赖“角色名散落各处”的重复判断（docs/4 §5.2.5）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    /** 超级管理员角色码。 */
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    /** 权限码缓存有效期（docs/4 §4.3）。 */
    private static final Duration PERMISSION_CACHE_TTL = Duration.ofMinutes(30);

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final IamRoleMapper roleMapper;
    private final IamPermissionMapper permissionMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

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
     * 查询用户生效的权限码（带版本化缓存）。
     *
     * @param userId 用户 ID
     * @return 权限码列表；超级管理员返回全部启用权限
     */
    public List<String> permissionCodes(Long userId) {
        if (userId == null) {
            return List.of();
        }
        String userIdText = String.valueOf(userId);
        long versionAtRead = currentVersion(userIdText);
        String cacheKey = RedisKeys.authPerm(userIdText, versionAtRead);
        List<String> cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }
        List<String> codes = loadPermissionCodes(userId);
        // 回源期间可能有人改了角色：版本已变则丢弃旧结果，避免把过期权限写进旧版本 key
        if (currentVersion(userIdText) == versionAtRead) {
            writeCache(cacheKey, codes);
        }
        return codes;
    }

    /**
     * 当前用户的动态菜单树（A-06）。
     *
     * <p>只取 ACTIVE 的 MENU 节点；父节点不可见时该节点提升为根节点，避免整棵子树消失。
     * 菜单不缓存，权限/菜单定义变更后下一次请求即拿到新结构。</p>
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
        String userIdText = String.valueOf(userId);
        Long version = redisTemplate.opsForValue()
                .increment(RedisKeys.authPermVersion(userIdText));
        Set<String> cacheKeys = redisTemplate.keys(RedisKeys.AUTH_PERM + userIdText + ":*");
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

    private List<String> loadPermissionCodes(Long userId) {
        return isSuperAdmin(userId)
                ? permissionMapper.selectAllActivePermissionCodes()
                : permissionMapper.selectActivePermissionCodesByUserId(userId);
    }

    /**
     * 读取权限版本；Redis 不可用或值非法时按 0 处理，后续缓存读写会继续降级。
     */
    private long currentVersion(String userIdText) {
        try {
            String value = redisTemplate.opsForValue().get(RedisKeys.authPermVersion(userIdText));
            if (value == null || value.isBlank()) {
                return 0L;
            }
            return Long.parseLong(value);
        } catch (RuntimeException e) {
            log.warn("读取权限版本失败，按 0 处理并回源数据库: userId={}", userIdText, e);
            return 0L;
        }
    }

    /**
     * 读缓存；未命中、内容损坏或 Redis 不可用时返回 null，由调用方回源 DB。
     */
    private List<String> readCache(String cacheKey) {
        try {
            String value = redisTemplate.opsForValue().get(cacheKey);
            if (value == null || value.isBlank()) {
                return null;
            }
            return objectMapper.readValue(value, STRING_LIST);
        } catch (Exception e) {
            log.warn("读取权限缓存失败，回源数据库: key={}", cacheKey, e);
            return null;
        }
    }

    /**
     * 写缓存；失败只记日志，不影响本次授权结果（本次仍使用 DB 查询结果）。
     */
    private void writeCache(String cacheKey, List<String> codes) {
        try {
            redisTemplate.opsForValue().set(cacheKey,
                    objectMapper.writeValueAsString(codes), PERMISSION_CACHE_TTL);
        } catch (Exception e) {
            log.warn("写入权限缓存失败，本次直接使用数据库结果: key={}", cacheKey, e);
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
