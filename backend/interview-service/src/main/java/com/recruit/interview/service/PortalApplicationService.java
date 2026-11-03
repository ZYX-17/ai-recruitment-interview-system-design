package com.recruit.interview.service;

import com.recruit.common.context.UserContext;
import com.recruit.common.idempotency.IdempotencyService;
import com.recruit.common.result.PageResult;
import com.recruit.interview.dto.ApplicationCreateForm;
import com.recruit.interview.dto.ApplicationView;
import com.recruit.interview.dto.PortalApplicationCreateForm;
import com.recruit.interview.dto.PortalApplicationPageQuery;
import com.recruit.interview.dto.PortalApplicationView;
import com.recruit.interview.entity.Application;
import com.recruit.interview.mapper.ApplicationMapper;
import com.recruit.interview.support.PortalGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 门户“我的投递”（I-09）。
 *
 * <p>候选人身份只取网关注入的当前用户；列表/详情/撤回都按 {@code person_id} 归属校验，
 * 请求体不接受 {@code personId}，避免冒充他人。</p>
 */
@Service
@RequiredArgsConstructor
public class PortalApplicationService {

    private static final String SCOPE_CREATE = "interview.portal.application.create";
    private static final String SOURCE_PORTAL = "PORTAL";

    private final ApplicationMapper applicationMapper;
    private final ApplicationService applicationService;
    private final IdempotencyService idempotencyService;

    /**
     * 我的投递分页。
     *
     * @param query 分页与状态
     * @return 分页投递
     */
    public PageResult<PortalApplicationView> page(PortalApplicationPageQuery query) {
        long personId = PortalGuard.requireCandidateId();
        String status = normalizeStatus(query.getStatus());
        long total = applicationMapper.countPortalPage(personId, status);
        if (total == 0) {
            return PageResult.of(List.of(), 0, query.getPageNum(), query.getPageSize());
        }
        long offset = (long) (query.getPageNum() - 1) * query.getPageSize();
        List<Application> rows = applicationMapper.selectPortalPage(personId, status,
                query.getPageSize(), offset);
        List<PortalApplicationView> views = new ArrayList<>(rows.size());
        for (Application row : rows) {
            views.add(toView(row));
        }
        return PageResult.of(views, total, query.getPageNum(), query.getPageSize());
    }

    /**
     * 投递岗位。
     *
     * @param form 投递入参
     * @param key  Idempotency-Key，可选
     * @return 新投递
     */
    public PortalApplicationView create(PortalApplicationCreateForm form, String key) {
        if (key != null && !key.isBlank()) {
            return idempotencyService.execute(SCOPE_CREATE, key, PortalApplicationView.class,
                    () -> doCreate(form));
        }
        return doCreate(form);
    }

    private PortalApplicationView doCreate(PortalApplicationCreateForm form) {
        long personId = PortalGuard.requireCandidateId();
        ApplicationCreateForm inner = new ApplicationCreateForm();
        inner.setJobId(form.getJobId());
        inner.setPersonId(String.valueOf(personId));
        inner.setPersonName(UserContext.getDisplayName());
        inner.setSource(SOURCE_PORTAL);
        ApplicationView created = applicationService.create(inner, null, form.getRemark());
        return toView(created);
    }

    /**
     * 撤回投递。
     *
     * @param id 投递 ID
     * @return 撤回后的投递
     */
    public PortalApplicationView withdraw(long id) {
        long personId = PortalGuard.requireCandidateId();
        return toView(applicationService.withdrawForCandidate(id, personId));
    }

    private static PortalApplicationView toView(Application application) {
        return new PortalApplicationView(
                String.valueOf(application.getId()),
                String.valueOf(application.getJobId()),
                application.getJobTitle(),
                application.getStatus(),
                application.getCurrentStage(),
                application.getRoundNo(),
                application.getSource(),
                application.getRejectReason(),
                toInstant(application.getCreatedAt()),
                toInstant(application.getUpdatedAt()),
                application.getVersion());
    }

    private static PortalApplicationView toView(ApplicationView view) {
        return new PortalApplicationView(
                view.id(),
                view.jobId(),
                view.jobTitle(),
                view.status(),
                view.currentStage(),
                view.roundNo(),
                view.source(),
                view.rejectReason(),
                view.createdAt(),
                view.updatedAt(),
                view.version());
    }

    private static String normalizeStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
