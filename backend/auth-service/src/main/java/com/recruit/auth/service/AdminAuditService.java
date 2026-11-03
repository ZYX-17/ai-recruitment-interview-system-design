package com.recruit.auth.service;

import com.recruit.auth.dto.AuditLogPageQuery;
import com.recruit.auth.dto.AuditLogView;
import com.recruit.auth.dto.LoginLogPageQuery;
import com.recruit.auth.dto.LoginLogView;
import com.recruit.auth.mapper.IamAuditLogMapper;
import com.recruit.auth.mapper.row.AuditLogRow;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 审计日志查询（P2-5 / A-15）：操作日志与登录日志分页。
 *
 * <p>日志只读，不做更新/删除；登录日志固定取 {@code action = 'LOGIN'}，
 * 失败原因等摘要已由写入方脱敏，这里原样返回 JSON 字符串，避免二次解析丢字段。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private final IamAuditLogMapper auditLogMapper;

    /**
     * 操作日志分页。
     *
     * @param query 分页与过滤条件
     * @return 分页操作日志
     */
    public PageResult<AuditLogView> auditPage(AuditLogPageQuery query) {
        validateRange(query.getStartTime(), query.getEndTime());

        long total = auditLogMapper.countAuditPage(query.getOperatorId(), trimToNull(query.getAction()),
                trimToNull(query.getResourceType()), trimToNull(query.getResult()),
                trimToNull(query.getIp()), trimToNull(query.getTraceId()),
                query.getStartTime(), query.getEndTime());
        if (total == 0) {
            return PageResult.of(List.of(), 0, query.getPageNum(), query.getPageSize());
        }
        long offset = (query.getPageNum() - 1) * query.getPageSize();
        List<AuditLogRow> rows = auditLogMapper.selectAuditPage(query.getOperatorId(),
                trimToNull(query.getAction()), trimToNull(query.getResourceType()),
                trimToNull(query.getResult()), trimToNull(query.getIp()),
                trimToNull(query.getTraceId()), query.getStartTime(), query.getEndTime(),
                query.getPageSize(), offset);

        List<AuditLogView> views = new ArrayList<>(rows.size());
        for (AuditLogRow row : rows) {
            views.add(new AuditLogView(
                    String.valueOf(row.getId()),
                    row.getOperatorId() == null ? null : String.valueOf(row.getOperatorId()),
                    row.getOperatorUsername(),
                    row.getOperatorName(),
                    row.getAction(),
                    row.getResourceType(),
                    row.getResourceId(),
                    row.getResult(),
                    row.getIp(),
                    row.getTraceId(),
                    row.getDetail(),
                    row.getCreatedAt()));
        }
        return PageResult.of(views, total, query.getPageNum(), query.getPageSize());
    }

    /**
     * 登录日志分页。
     *
     * @param query 分页与过滤条件
     * @return 分页登录日志
     */
    public PageResult<LoginLogView> loginPage(LoginLogPageQuery query) {
        validateRange(query.getStartTime(), query.getEndTime());

        long total = auditLogMapper.countLoginPage(trimToNull(query.getUsername()),
                trimToNull(query.getResult()), trimToNull(query.getIp()),
                trimToNull(query.getTraceId()), query.getStartTime(), query.getEndTime());
        if (total == 0) {
            return PageResult.of(List.of(), 0, query.getPageNum(), query.getPageSize());
        }
        long offset = (query.getPageNum() - 1) * query.getPageSize();
        List<AuditLogRow> rows = auditLogMapper.selectLoginPage(trimToNull(query.getUsername()),
                trimToNull(query.getResult()), trimToNull(query.getIp()),
                trimToNull(query.getTraceId()), query.getStartTime(), query.getEndTime(),
                query.getPageSize(), offset);

        List<LoginLogView> views = new ArrayList<>(rows.size());
        for (AuditLogRow row : rows) {
            views.add(new LoginLogView(
                    String.valueOf(row.getId()),
                    row.getOperatorId() == null ? null : String.valueOf(row.getOperatorId()),
                    row.getOperatorUsername(),
                    row.getOperatorName(),
                    row.getResult(),
                    row.getIp(),
                    row.getTraceId(),
                    row.getDetail(),
                    row.getCreatedAt()));
        }
        return PageResult.of(views, total, query.getPageNum(), query.getPageSize());
    }

    private static void validateRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "startTime 不能晚于 endTime");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}
