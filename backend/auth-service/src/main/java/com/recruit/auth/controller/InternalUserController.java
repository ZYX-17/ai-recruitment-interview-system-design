package com.recruit.auth.controller;

import com.recruit.auth.dto.UserAuthzView;
import com.recruit.auth.entity.IamUser;
import com.recruit.auth.mapper.IamDepartmentMapper;
import com.recruit.auth.mapper.IamUserMapper;
import com.recruit.auth.service.PermissionService;
import com.recruit.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务间用户鉴权查询（X-13）。
 *
 * <p>业务服务不能直连 {@code recruit_auth} 库，也不该信任客户端自带的权限码；
 * 需要鉴权时通过本接口拿当前用户快照。入口由 {@code InternalTokenFilter} 校验
 * {@code X-Internal-Token}，网关侧对 {@code /internal/**} 一律 403。</p>
 */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final PermissionService permissionService;
    private final IamUserMapper userMapper;
    private final IamDepartmentMapper departmentMapper;

    /**
     * 查询用户鉴权快照。
     *
     * @param userId 用户 ID
     * @return 权限码/角色/数据范围/部门
     */
    @GetMapping("/{userId}/authz")
    public Result<UserAuthzView> authz(@PathVariable long userId) {
        IamUser user = userMapper.selectById(userId);
        Long deptId = user == null ? null : user.getDeptId();
        List<Long> deptIds = deptId == null ? List.of() : departmentMapper.selectSubtreeIds(deptId);
        return Result.ok(new UserAuthzView(
                permissionService.permissionCodes(userId),
                permissionService.roleCodes(userId),
                permissionService.dataScope(userId),
                deptId == null ? null : String.valueOf(deptId),
                deptIds.stream().map(String::valueOf).toList()));
    }
}
