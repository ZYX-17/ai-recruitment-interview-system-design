package com.recruit.interview.client;

import com.recruit.common.result.Result;
import com.recruit.interview.dto.AuthzSnapshot;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * auth-service 内部鉴权接口（X-13）。
 *
 * <p>本地联调可用 {@code AUTH_SERVICE_URL} 直连；生产走 Nacos 服务发现。</p>
 */
@FeignClient(name = "auth-service", url = "${AUTH_SERVICE_URL:}")
public interface AuthPermissionClient {

    /**
     * 查询用户鉴权快照。
     *
     * @param userId 用户 ID
     * @return 权限码/角色/数据范围/部门
     */
    @GetMapping("/internal/users/{userId}/authz")
    Result<AuthzSnapshot> authz(@PathVariable("userId") long userId);
}
