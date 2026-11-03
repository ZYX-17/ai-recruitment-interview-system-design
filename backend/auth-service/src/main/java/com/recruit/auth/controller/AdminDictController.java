package com.recruit.auth.controller;

import com.recruit.auth.dto.DictPageQuery;
import com.recruit.auth.dto.DictView;
import com.recruit.auth.dto.UpdateDictForm;
import com.recruit.auth.security.UserPrincipal;
import com.recruit.auth.service.AdminDictService;
import com.recruit.auth.support.IpUtil;
import com.recruit.auth.support.PrincipalHelper;
import com.recruit.common.result.PageResult;
import com.recruit.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字典管理端点（P2-5 扩展 / X-11）：前端下拉与枚举展示。
 *
 * <p>只提供分页与维护；字典类型/编码由 Flyway 或后续建设端固化，管理端不可改。</p>
 */
@RestController
@RequestMapping("/api/admin/dicts")
@RequiredArgsConstructor
public class AdminDictController {

    private final AdminDictService adminDictService;

    /**
     * 字典分页。
     *
     * @param query 分页与过滤条件
     * @return 分页字典项
     */
    @GetMapping
    @PreAuthorize("@permissionChecker.has('system:dict:view')")
    public Result<PageResult<DictView>> page(@Valid DictPageQuery query) {
        return Result.ok(adminDictService.page(query));
    }

    /**
     * 修改字典项。
     *
     * @param id        字典项 ID
     * @param form      修改入参
     * @param principal 当前登录主体
     * @param request   HTTP 请求（取 IP 写审计）
     * @return 更新后的字典项
     */
    @PutMapping("/{id}")
    @PreAuthorize("@permissionChecker.has('system:dict:update')")
    public Result<DictView> update(@PathVariable long id,
                                   @Valid @RequestBody UpdateDictForm form,
                                   @AuthenticationPrincipal UserPrincipal principal,
                                   HttpServletRequest request) {
        return Result.ok(adminDictService.update(id, form,
                PrincipalHelper.operatorId(principal), IpUtil.clientIp(request)));
    }
}
