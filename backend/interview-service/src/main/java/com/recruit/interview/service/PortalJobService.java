package com.recruit.interview.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.PageResult;
import com.recruit.interview.dto.PortalJobPageQuery;
import com.recruit.interview.dto.PortalJobView;
import com.recruit.interview.entity.Job;
import com.recruit.interview.mapper.JobMapper;
import com.recruit.interview.mapper.row.JobRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * 门户岗位浏览（I-10，公开只读）。
 *
 * <p>只返回 PUBLISHED 岗位，且不暴露负责人、部门 ID、版本号等内部字段。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortalJobService {

    private final JobMapper jobMapper;
    private final ObjectMapper objectMapper;

    /**
     * 已发布岗位分页。
     *
     * @param query 分页与关键词
     * @return 分页岗位
     */
    public PageResult<PortalJobView> page(PortalJobPageQuery query) {
        String keyword = trimToNull(query.getKeyword());
        long total = jobMapper.countPublishedPage(keyword);
        if (total == 0) {
            return PageResult.of(List.of(), 0, query.getPageNum(), query.getPageSize());
        }
        long offset = (long) (query.getPageNum() - 1) * query.getPageSize();
        List<JobRow> rows = jobMapper.selectPublishedPage(keyword, query.getPageSize(), offset);
        List<PortalJobView> views = new ArrayList<>(rows.size());
        for (JobRow row : rows) {
            views.add(toView(row));
        }
        return PageResult.of(views, total, query.getPageNum(), query.getPageSize());
    }

    /**
     * 已发布岗位详情。
     *
     * @param id 岗位 ID
     * @return 岗位详情
     */
    public PortalJobView detail(long id) {
        Job job = jobMapper.selectById(id);
        if (job == null || !JobService.STATUS_PUBLISHED.equals(job.getStatus())) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "岗位不存在或未发布");
        }
        return new PortalJobView(
                String.valueOf(job.getId()),
                job.getJobCode(),
                job.getTitle(),
                job.getDeptName(),
                job.getJdText(),
                readJson(job.getRequirementsJson()),
                job.getHeadcount(),
                job.getSalaryRange(),
                toInstant(job.getPublishedAt()));
    }

    private PortalJobView toView(JobRow row) {
        return new PortalJobView(
                String.valueOf(row.getId()),
                row.getJobCode(),
                row.getTitle(),
                row.getDeptName(),
                row.getJdText(),
                readJson(row.getRequirementsJson()),
                row.getHeadcount(),
                row.getSalaryRange(),
                toInstant(row.getPublishedAt()));
    }

    private JsonNode readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("岗位要求 JSON 解析失败，门户按空处理: {}", e.getMessage());
            return null;
        }
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}
