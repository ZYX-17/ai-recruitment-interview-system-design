package com.recruit.auth.service;

import com.recruit.auth.mapper.IamPermissionMapper;
import com.recruit.auth.mapper.IamRoleMapper;
import com.recruit.common.constant.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 角色/权限查询与权限版本维护。
 *
 * <p>权限码按用户 ID 现查，保证与库一致；P2-6 再叠加
 * {@code auth:perm:{userId}:{ver}} 缓存，角色/权限变更时递增 {@code auth:permver} 即可让旧缓存失效。</p>
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

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
     * @return 权限码列表
     */
    public List<String> permissionCodes(Long userId) {
        return permissionMapper.selectActivePermissionCodesByUserId(userId);
    }

    /**
     * 递增用户权限版本，使带旧版本号的权限缓存即时失效。
     *
     * <p>Redis 异常直接抛出，让角色变更事务回滚：收权不能因为缓存刷新失败而“看起来成功”。</p>
     *
     * @param userId 用户 ID
     * @return 递增后的版本号
     */
    public long bumpVersion(Long userId) {
        Long version = redisTemplate.opsForValue()
                .increment(RedisKeys.authPermVersion(Long.toString(userId)));
        return version == null ? 0L : version;
    }
}
