package com.recruit.auth.service;

import com.recruit.auth.mapper.IamPermissionMapper;
import com.recruit.auth.mapper.IamRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 角色/权限查询。
 *
 * <p>登录时按用户 ID 现查，保证返回的权限码与库一致；P2-6 再叠加
 * {@code auth:perm:{userId}:{ver}} 缓存与 {@code auth:permver} 失效。</p>
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final IamRoleMapper roleMapper;
    private final IamPermissionMapper permissionMapper;

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
}
