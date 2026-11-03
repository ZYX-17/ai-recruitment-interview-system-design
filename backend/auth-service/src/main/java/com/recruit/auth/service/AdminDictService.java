package com.recruit.auth.service;

import com.recruit.auth.dto.DictPageQuery;
import com.recruit.auth.dto.DictView;
import com.recruit.auth.dto.UpdateDictForm;
import com.recruit.auth.entity.IamDict;
import com.recruit.auth.mapper.IamDictMapper;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 字典管理（P2-5 扩展 / X-11）：分页与白名单更新。
 *
 * <p>字典类型与编码是前端契约的一部分，创建后不可在线修改；管理端只能维护
 * 展示文案、排序、启停与备注。更新使用 {@code version} 乐观锁，冲突返回 {@code SYS412}。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminDictService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final Set<String> STATUSES = Set.of(STATUS_ACTIVE, STATUS_DISABLED);

    private static final String RESOURCE_DICT = "DICT";
    private static final String ACTION_UPDATE = "DICT_UPDATE";

    private final IamDictMapper dictMapper;
    private final AuditService auditService;

    /**
     * 字典分页。
     *
     * @param query 分页与过滤条件
     * @return 分页字典项
     */
    public PageResult<DictView> page(DictPageQuery query) {
        String dictType = trimToNull(query.getDictType());
        String keyword = trimToNull(query.getKeyword());
        String status = trimToNull(query.getStatus());

        long total = dictMapper.countDictPage(dictType, keyword, status);
        if (total == 0) {
            return PageResult.of(List.of(), 0, query.getPageNum(), query.getPageSize());
        }
        long offset = (query.getPageNum() - 1) * query.getPageSize();
        List<IamDict> rows = dictMapper.selectDictPage(dictType, keyword, status,
                query.getPageSize(), offset);

        List<DictView> views = new ArrayList<>(rows.size());
        for (IamDict row : rows) {
            views.add(toView(row));
        }
        return PageResult.of(views, total, query.getPageNum(), query.getPageSize());
    }

    /**
     * 修改字典项（类型/编码不可改）。
     *
     * @param id         字典项 ID
     * @param form       修改入参
     * @param operatorId 当前操作者 ID
     * @param ip         客户端 IP，写审计用
     * @return 更新后的字典项
     */
    @Transactional
    public DictView update(long id, UpdateDictForm form, long operatorId, String ip) {
        IamDict current = requireDict(id);
        String dictLabel = hasText(form.getDictLabel()) ? form.getDictLabel().trim() : current.getDictLabel();
        int sort = form.getSort() == null
                ? (current.getSort() == null ? 0 : current.getSort())
                : form.getSort();
        String status = form.getStatus() == null
                ? current.getStatus()
                : requireOneOf(form.getStatus(), STATUSES, "字典状态非法");
        String remark = form.getRemark() == null ? null : trimToNull(form.getRemark());

        int updated = dictMapper.updateDict(id, dictLabel, sort, status, remark, form.getVersion());
        if (updated == 0) {
            if (dictMapper.selectById(id) == null) {
                throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "字典项不存在");
            }
            throw new BizException(ErrorCode.VERSION_CONFLICT);
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("dictType", current.getDictType());
        detail.put("dictCode", current.getDictCode());
        detail.put("status", status);
        auditService.recordQuietly(operatorId, ACTION_UPDATE, RESOURCE_DICT,
                String.valueOf(id), true, ip, detail);

        return toView(requireDict(id));
    }

    private IamDict requireDict(long id) {
        IamDict dict = dictMapper.selectById(id);
        if (dict == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "字典项不存在");
        }
        return dict;
    }

    private static DictView toView(IamDict row) {
        return new DictView(
                String.valueOf(row.getId()),
                row.getDictType(),
                row.getDictCode(),
                row.getDictLabel(),
                row.getSort() == null ? 0 : row.getSort(),
                row.getStatus(),
                row.getRemark(),
                row.getCreatedAt(),
                row.getVersion() == null ? 0 : row.getVersion());
    }

    private static String requireOneOf(String value, Set<String> allowed, String message) {
        String text = value == null ? null : value.trim().toUpperCase(Locale.ROOT);
        if (text == null || !allowed.contains(text)) {
            throw new BizException(ErrorCode.PARAM_INVALID, message);
        }
        return text;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
