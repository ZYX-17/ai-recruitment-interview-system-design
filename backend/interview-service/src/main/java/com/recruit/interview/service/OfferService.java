package com.recruit.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.context.UserContext;
import com.recruit.common.exception.BizException;
import com.recruit.common.idempotency.IdempotencyService;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.PageResult;
import com.recruit.contracts.event.EventTypes;
import com.recruit.contracts.event.payload.NotificationRequestedPayload;
import com.recruit.contracts.event.payload.OfferConfirmedPayload;
import com.recruit.contracts.event.payload.OfferDeclinedPayload;
import com.recruit.contracts.event.payload.OfferExpiredPayload;
import com.recruit.contracts.event.payload.OfferSentPayload;
import com.recruit.interview.dto.ConfirmLinkView;
import com.recruit.interview.dto.OfferApproveForm;
import com.recruit.interview.dto.OfferCreateForm;
import com.recruit.interview.dto.OfferPageQuery;
import com.recruit.interview.dto.OfferRespondForm;
import com.recruit.interview.dto.OfferSendForm;
import com.recruit.interview.dto.OfferView;
import com.recruit.interview.dto.PortalOfferView;
import com.recruit.interview.entity.Application;
import com.recruit.interview.entity.ApplicationStageHistory;
import com.recruit.interview.entity.Job;
import com.recruit.interview.entity.Offer;
import com.recruit.interview.event.OutboxPublisher;
import com.recruit.interview.mapper.ApplicationMapper;
import com.recruit.interview.mapper.ApplicationStageHistoryMapper;
import com.recruit.interview.mapper.JobMapper;
import com.recruit.interview.mapper.OfferMapper;
import com.recruit.interview.mapper.row.OfferRow;
import com.recruit.interview.support.IdSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Offer 闭环（P7-1~P7-7）。
 *
 * <p>关键口径（docs/4 §5.6、§7.7）：</p>
 * <ul>
 *   <li>{@code application_id} 唯一，一个投递一份 Offer；</li>
 *   <li>状态机 {@code DRAFT → PENDING_APPROVAL → SENT → CONFIRMED/DECLINED/EXPIRED/WITHDRAWN}，
 *       所有流转带 {@code version} CAS；</li>
 *   <li>确认 token 32 bytes，只存 {@code sha256}；明文只在邮件链接，MQ/Outbox 不出现；</li>
 *   <li>门户确认/拒绝幂等：重复确认返回已确认结果，不重复发事件；</li>
 *   <li>过期由定时扫描 + CAS 兜底，多实例重复触发无害；</li>
 *   <li>发送 → 投递 {@code OFFERED}；确认 → {@code HIRED}；拒绝 → 回 {@code IN_PROGRESS}。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfferService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_DECLINED = "DECLINED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";

    private static final Set<String> STATUSES = Set.of(STATUS_DRAFT, STATUS_PENDING_APPROVAL,
            STATUS_SENT, STATUS_CONFIRMED, STATUS_DECLINED, STATUS_EXPIRED, STATUS_WITHDRAWN);
    private static final Set<String> SENDABLE = Set.of(STATUS_DRAFT, STATUS_PENDING_APPROVAL);
    private static final Set<String> WITHDRAWABLE = Set.of(
            STATUS_DRAFT, STATUS_PENDING_APPROVAL, STATUS_SENT);
    private static final Set<String> CHANNELS = Set.of("EMAIL", "INAPP", "SMS_MOCK");
    private static final List<String> DEFAULT_CHANNELS = List.of("EMAIL", "INAPP");
    private static final Set<String> OFFERABLE_APPLICATION_STATUSES = Set.of(
            "IN_PROGRESS", "PASSED", "OFFERED");

    private static final String SCOPE_CREATE = "interview.offer.create";
    private static final String SCOPE_SEND = "interview.offer.send";
    private static final String OFFER_STAGE = "OFFER";
    private static final int EXPIRE_BATCH = 100;
    private static final Duration CONFIRM_CACHE_TTL = Duration.ofMinutes(10);
    private static final DateTimeFormatter OFFER_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OfferMapper offerMapper;
    private final ApplicationMapper applicationMapper;
    private final ApplicationStageHistoryMapper historyMapper;
    private final JobMapper jobMapper;
    private final OutboxPublisher outboxPublisher;
    private final ObjectMapper objectMapper;
    private final DataScopeService dataScopeService;
    private final IdempotencyService idempotencyService;
    private final StringRedisTemplate redisTemplate;

    @Value("${recruit.offer.confirm-base-url:http://localhost:5173/portal/offer/confirm}")
    private String confirmBaseUrl = "http://localhost:5173/portal/offer/confirm";

    @Value("${recruit.offer.company-name:示例公司}")
    private String companyName = "示例公司";

    /**
     * Offer 分页。
     *
     * @param query 分页与过滤条件
     * @return 分页 Offer
     */
    public PageResult<OfferView> page(OfferPageQuery query) {
        DataScopeService.DataScope scope = dataScopeService.current();
        String status = trimToNull(query.getStatus());
        if (status != null) {
            status = status.toUpperCase(Locale.ROOT);
            if (!STATUSES.contains(status)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "Offer 状态非法: " + query.getStatus());
            }
        }
        Long jobId = IdSupport.optionalLong(query.getJobId(), "jobId");
        String keyword = trimToNull(query.getKeyword());
        String sqlScope = scope.sqlScope();

        long total = offerMapper.countOfferPage(keyword, status, jobId, sqlScope,
                scope.deptIds(), scope.userId());
        if (total == 0) {
            return PageResult.of(List.of(), 0, query.getPageNum(), query.getPageSize());
        }
        long offset = (long) (query.getPageNum() - 1) * query.getPageSize();
        List<OfferRow> rows = offerMapper.selectOfferPage(keyword, status, jobId, sqlScope,
                scope.deptIds(), scope.userId(), query.getPageSize(), offset);
        List<OfferView> views = new ArrayList<>(rows.size());
        for (OfferRow row : rows) {
            views.add(toView(row));
        }
        return PageResult.of(views, total, query.getPageNum(), query.getPageSize());
    }

    /**
     * Offer 详情。
     *
     * @param id Offer ID
     * @return 详情
     */
    public OfferView detail(long id) {
        return toView(loadVisible(id));
    }

    /**
     * 创建 Offer（DRAFT）。
     *
     * @param form 创建入参
     * @param key  Idempotency-Key，可为空
     * @return 新 Offer
     */
    @Transactional
    public OfferView create(OfferCreateForm form, String key) {
        if (hasText(key)) {
            return idempotencyService.execute(SCOPE_CREATE, key, OfferView.class,
                    () -> doCreate(form));
        }
        return doCreate(form);
    }

    private OfferView doCreate(OfferCreateForm form) {
        long applicationId = IdSupport.requireLong(form.getApplicationId(), "applicationId");
        Application application = applicationMapper.selectById(applicationId);
        if (application == null) {
            throw new BizException(ErrorCode.APP_NOT_FOUND, "投递不存在");
        }
        Job job = jobMapper.selectById(application.getJobId());
        if (job == null) {
            throw new BizException(ErrorCode.APP_NOT_FOUND, "投递关联岗位不存在");
        }
        DataScopeService.DataScope scope = dataScopeService.current();
        if (!scope.canSee(job.getDeptId(), job.getOwnerHrId())) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "无权为该投递创建 Offer");
        }
        String applicationStatus = application.getStatus() == null
                ? "" : application.getStatus().toUpperCase(Locale.ROOT);
        if (!OFFERABLE_APPLICATION_STATUSES.contains(applicationStatus)) {
            throw new BizException(ErrorCode.OFFER_STATE_NOT_ALLOWED, "当前投递状态不能创建 Offer");
        }
        if (offerMapper.selectRowByApplicationId(applicationId) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该投递已存在 Offer");
        }
        Instant expireAt = form.getExpireAt();
        if (expireAt == null || !expireAt.isAfter(Instant.now())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "Offer 过期时间必须晚于当前时间");
        }

        Offer offer = new Offer();
        offer.setOfferNo(newOfferNo());
        offer.setApplicationId(applicationId);
        offer.setPositionTitle(hasText(application.getJobTitle())
                ? application.getJobTitle() : job.getTitle());
        offer.setSalaryJson(writeJson(form.getSalary()));
        offer.setEntryDate(form.getEntryDate());
        offer.setExpireAt(toUtc(expireAt));
        offer.setConfirmTokenHash(sha256Hex(newToken()));
        offer.setStatus(STATUS_DRAFT);
        offer.setOnboardingJson(writeJson(form.getOnboarding()));
        offer.setOperatorId(currentUserId());
        offer.setVersion(0);
        try {
            offerMapper.insert(offer);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该投递已存在 Offer");
        }
        audit("create", offer.getId(), currentUserId(), "status=DRAFT");
        return toView(offerMapper.selectRowById(offer.getId()));
    }

    /**
     * 发送 Offer：生成 token（只存哈希）、状态置 SENT、发事件并联动投递 OFFERED。
     *
     * @param id   Offer ID
     * @param form 发送入参
     * @param key  Idempotency-Key，可为空
     * @return 发送后的 Offer
     */
    @Transactional
    public OfferView send(long id, OfferSendForm form, String key) {
        if (hasText(key)) {
            return idempotencyService.execute(SCOPE_SEND, key, OfferView.class,
                    () -> doSend(id, form));
        }
        return doSend(id, form);
    }

    private OfferView doSend(long id, OfferSendForm form) {
        OfferRow row = loadVisible(id);
        if (STATUS_SENT.equals(row.getStatus())) {
            return toView(row);
        }
        List<String> channels = normalizeChannels(form == null ? null : form.getChannel());
        String templateCode = form == null || !hasText(form.getTemplateCode())
                ? "offer_send" : form.getTemplateCode().trim();
        return doSend(row, channels, templateCode, form == null ? null : form.getVersion());
    }

    private OfferView doSend(OfferRow row, List<String> channels, String templateCode,
                             Integer expectedVersion) {
        if (!SENDABLE.contains(row.getStatus())) {
            throw new BizException(ErrorCode.OFFER_STATE_NOT_ALLOWED,
                    "仅草稿/待审批的 Offer 可以发送");
        }
        if (expectedVersion != null && !Objects.equals(row.getVersion(), expectedVersion)) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突，请刷新后重试");
        }
        if (row.getExpireAt() != null && row.getExpireAt().isBefore(nowUtc())) {
            throw new BizException(ErrorCode.OFFER_STATE_NOT_ALLOWED, "Offer 已过期，不能发送");
        }
        String token = newToken();
        String tokenHash = sha256Hex(token);
        if (offerMapper.markSent(row.getId(), tokenHash, row.getVersion()) == 0) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突或状态不允许发送");
        }
        long operatorId = currentUserId();
        markApplicationOffered(row, operatorId);
        cacheConfirmLink(row.getId(), token);
        publishSent(row, channels, templateCode);
        audit("send", row.getId(), operatorId, "status=SENT");
        return toView(offerMapper.selectRowById(row.getId()));
    }

    /**
     * 简化审批：通过即发送；驳回回 DRAFT。
     *
     * @param id   Offer ID
     * @param form 审批入参
     * @return 审批后的 Offer
     */
    @Transactional
    public OfferView approve(long id, OfferApproveForm form) {
        OfferRow row = loadVisible(id);
        if (Boolean.TRUE.equals(form.getApproved())) {
            if (STATUS_SENT.equals(row.getStatus())) {
                return toView(row);
            }
            return doSend(row, DEFAULT_CHANNELS, "offer_send", form.getVersion());
        }
        if (STATUS_DRAFT.equals(row.getStatus())) {
            return toView(row);
        }
        if (!SENDABLE.contains(row.getStatus())) {
            throw new BizException(ErrorCode.OFFER_STATE_NOT_ALLOWED, "仅草稿/待审批的 Offer 可以驳回");
        }
        if (form.getVersion() != null && !Objects.equals(row.getVersion(), form.getVersion())) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突，请刷新后重试");
        }
        if (offerMapper.markDraft(id, row.getVersion()) == 0) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突或状态不允许驳回");
        }
        audit("approve-reject", id, currentUserId(), trimToNull(form.getComment()));
        return detail(id);
    }

    /**
     * 撤回未确认 Offer；已确认/已拒绝/已过期不可撤回。
     *
     * @param id     Offer ID
     * @param reason 撤回原因（仅日志/通知）
     * @return 撤回后的 Offer
     */
    @Transactional
    public OfferView withdraw(long id, String reason) {
        OfferRow row = loadVisible(id);
        if (STATUS_WITHDRAWN.equals(row.getStatus())) {
            return toView(row);
        }
        if (!WITHDRAWABLE.contains(row.getStatus())) {
            throw new BizException(ErrorCode.OFFER_STATE_NOT_ALLOWED,
                    "已确认/已拒绝/已过期的 Offer 不能撤回");
        }
        if (offerMapper.markWithdrawn(id, row.getVersion()) == 0) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突或状态不允许撤回");
        }
        long operatorId = currentUserId();
        publishNotification(String.valueOf(row.getOwnerHrId()), List.of("INAPP"),
                "offer_withdrawn", String.valueOf(id),
                variables(row, Map.of("reason",
                        trimToNull(reason) == null ? "" : trimToNull(reason))),
                "offer:" + id + ":withdrawn");
        audit("withdraw", id, operatorId, trimToNull(reason));
        return detail(id);
    }

    /**
     * 门户查看 Offer（免登录）。
     *
     * @param token 明文 token
     * @return 候选人可见字段
     */
    public PortalOfferView portalDetail(String token) {
        OfferRow row = loadByToken(token);
        ensurePortalAvailable(row);
        return toPortalView(row);
    }

    /**
     * 门户确认/拒绝 Offer（免登录，CAS + 幂等）。
     *
     * @param token 明文 token
     * @param form  决策入参
     * @return 处理后的门户视图
     */
    @Transactional
    public PortalOfferView respond(String token, OfferRespondForm form) {
        OfferRow row = loadByToken(token);
        String decision = form.getDecision() == null
                ? "" : form.getDecision().trim().toUpperCase(Locale.ROOT);
        if (!"ACCEPT".equals(decision) && !"DECLINE".equals(decision)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "decision 只能为 ACCEPT 或 DECLINE");
        }
        if (hasText(form.getName())
                && !form.getName().trim().equals(row.getPersonName())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "姓名与 Offer 候选人不一致");
        }
        if ("ACCEPT".equals(decision)) {
            return accept(row);
        }
        return decline(row, form.getComment());
    }

    private PortalOfferView accept(OfferRow row) {
        if (STATUS_CONFIRMED.equals(row.getStatus())) {
            return toPortalView(row);
        }
        ensurePortalAvailable(row);
        if (offerMapper.markConfirmed(row.getId(), row.getVersion()) == 0) {
            OfferRow latest = offerMapper.selectRowById(row.getId());
            if (latest != null && STATUS_CONFIRMED.equals(latest.getStatus())) {
                return toPortalView(latest);
            }
            throw new BizException(ErrorCode.OFFER_STATE_NOT_ALLOWED, "Offer 已处理或版本冲突");
        }
        markApplicationHired(row);
        OfferRow latest = offerMapper.selectRowById(row.getId());
        outboxPublisher.publish(EventTypes.OFFER_CONFIRMED, String.valueOf(row.getId()), "offer",
                new OfferConfirmedPayload(String.valueOf(row.getId()),
                        String.valueOf(row.getApplicationId()),
                        String.valueOf(row.getPersonId()),
                        row.getEntryDate(),
                        toInstant(latest.getConfirmedAt())));
        publishNotification(String.valueOf(row.getOwnerHrId()), List.of("INAPP"),
                "offer_confirmed", String.valueOf(row.getId()),
                variables(row, Map.of()), "offer:" + row.getId() + ":confirmed");
        audit("confirm", row.getId(), row.getPersonId(), "status=CONFIRMED");
        return toPortalView(latest);
    }

    private PortalOfferView decline(OfferRow row, String comment) {
        if (STATUS_DECLINED.equals(row.getStatus())) {
            return toPortalView(row);
        }
        ensurePortalAvailable(row);
        if (offerMapper.markDeclined(row.getId(), row.getVersion()) == 0) {
            OfferRow latest = offerMapper.selectRowById(row.getId());
            if (latest != null && STATUS_DECLINED.equals(latest.getStatus())) {
                return toPortalView(latest);
            }
            throw new BizException(ErrorCode.OFFER_STATE_NOT_ALLOWED, "Offer 已处理或版本冲突");
        }
        markApplicationDeclined(row, comment);
        String reason = trimToNull(comment);
        outboxPublisher.publish(EventTypes.OFFER_DECLINED, String.valueOf(row.getId()), "offer",
                new OfferDeclinedPayload(String.valueOf(row.getId()),
                        String.valueOf(row.getApplicationId()),
                        String.valueOf(row.getPersonId()),
                        reason, Instant.now()));
        publishNotification(String.valueOf(row.getOwnerHrId()), List.of("INAPP"),
                "offer_declined", String.valueOf(row.getId()),
                variables(row, Map.of("reason", reason == null ? "" : reason)),
                "offer:" + row.getId() + ":declined");
        audit("decline", row.getId(), row.getPersonId(), reason);
        return toPortalView(offerMapper.selectRowById(row.getId()));
    }

    /**
     * 内部接口：给 notify 现取确认链接（不落日志、不进 MQ）。
     *
     * @param id Offer ID
     * @return 链接与缓存过期时间
     */
    @Transactional
    public ConfirmLinkView confirmLink(long id) {
        OfferRow row = offerMapper.selectRowById(id);
        if (row == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Offer 不存在");
        }
        if (!STATUS_SENT.equals(row.getStatus())) {
            throw new BizException(ErrorCode.OFFER_STATE_NOT_ALLOWED, "Offer 未发送或已处理");
        }
        if (row.getExpireAt() != null && row.getExpireAt().isBefore(nowUtc())) {
            expireOne(row);
            throw new BizException(ErrorCode.OFFER_STATE_NOT_ALLOWED, "Offer 已过期");
        }
        String key = com.recruit.common.constant.RedisKeys.offerConfirm(String.valueOf(id));
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (hasText(cached)) {
                return new ConfirmLinkView(cached, Instant.now().plus(CONFIRM_CACHE_TTL));
            }
        } catch (RuntimeException e) {
            log.warn("读取 Offer 确认链接缓存失败，将重新生成: offerId={}", id, e);
        }
        String token = newToken();
        if (offerMapper.rotateToken(id, sha256Hex(token), row.getVersion()) == 0) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "Offer 状态已变化，请重试");
        }
        String url = confirmUrl(token);
        cacheConfirmLink(id, token);
        return new ConfirmLinkView(url, Instant.now().plus(CONFIRM_CACHE_TTL));
    }

    /**
     * 定时扫描过期 Offer（每分钟，CAS 兜底；多实例重复触发无害）。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @Transactional
    public void expireOverdue() {
        List<Offer> rows = offerMapper.selectExpired(EXPIRE_BATCH);
        for (Offer row : rows) {
            if (offerMapper.markExpired(row.getId(), row.getVersion()) == 1) {
                OfferRow detailed = offerMapper.selectRowById(row.getId());
                if (detailed != null) {
                    publishExpired(detailed);
                }
                audit("expire", row.getId(), 0L, "status=EXPIRED");
            }
        }
    }

    private void expireOne(OfferRow row) {
        if (offerMapper.markExpired(row.getId(), row.getVersion()) == 1) {
            publishExpired(row);
            audit("expire", row.getId(), 0L, "status=EXPIRED");
        }
    }

    private void publishExpired(OfferRow row) {
        outboxPublisher.publish(EventTypes.OFFER_EXPIRED, String.valueOf(row.getId()), "offer",
                new OfferExpiredPayload(String.valueOf(row.getId()),
                        String.valueOf(row.getApplicationId()),
                        String.valueOf(row.getPersonId()), Instant.now()));
        publishNotification(String.valueOf(row.getOwnerHrId()), List.of("INAPP"),
                "offer_expired", String.valueOf(row.getId()),
                variables(row, Map.of()), "offer:" + row.getId() + ":expired");
    }

    private void markApplicationOffered(OfferRow row, long operatorId) {
        Application application = applicationMapper.selectById(row.getApplicationId());
        if (application == null) {
            throw new BizException(ErrorCode.APP_NOT_FOUND, "投递不存在");
        }
        String status = application.getStatus() == null ? "" : application.getStatus();
        if ("OFFERED".equals(status)) {
            return;
        }
        if (!Set.of("IN_PROGRESS", "PASSED").contains(status)) {
            throw new BizException(ErrorCode.OFFER_STATE_NOT_ALLOWED, "当前投递状态不能发放 Offer");
        }
        application.setStatus("OFFERED");
        application.setCurrentStage(OFFER_STAGE);
        if (applicationMapper.updateStateWithVersion(application) == 0) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "投递状态已被修改，请刷新后重试");
        }
        recordHistory(application, "OFFERED", "PENDING", "发送 Offer", operatorId);
    }

    private void markApplicationHired(OfferRow row) {
        Application application = applicationMapper.selectById(row.getApplicationId());
        if (application == null) {
            throw new BizException(ErrorCode.APP_NOT_FOUND, "投递不存在");
        }
        application.setStatus("HIRED");
        if (applicationMapper.updateStateWithVersion(application) == 0) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "投递状态已被修改，请刷新后重试");
        }
        recordHistory(application, "HIRED", "PASS", "候选人确认 Offer", row.getOperatorId());
    }

    private void markApplicationDeclined(OfferRow row, String comment) {
        Application application = applicationMapper.selectById(row.getApplicationId());
        if (application == null) {
            throw new BizException(ErrorCode.APP_NOT_FOUND, "投递不存在");
        }
        application.setStatus("IN_PROGRESS");
        application.setCurrentStage(OFFER_STAGE);
        if (applicationMapper.updateStateWithVersion(application) == 0) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "投递状态已被修改，请刷新后重试");
        }
        recordHistory(application, "IN_PROGRESS", "FAIL",
                trimToNull(comment) == null ? "候选人拒绝 Offer" : trimToNull(comment),
                row.getOperatorId());
    }

    private void recordHistory(Application application, String status, String result,
                               String comment, long operatorId) {
        LocalDateTime now = nowUtc();
        ApplicationStageHistory history = new ApplicationStageHistory();
        history.setApplicationId(application.getId());
        history.setStageNo(application.getCurrentStage());
        history.setRoundNo(application.getRoundNo() == null ? 0 : application.getRoundNo());
        history.setStatus(status);
        history.setResult(result);
        history.setOperatorId(operatorId);
        history.setComment(comment);
        history.setOperatedAt(now);
        history.setCreatedAt(now);
        historyMapper.insert(history);
    }

    private void publishSent(OfferRow row, List<String> channels, String templateCode) {
        outboxPublisher.publish(EventTypes.OFFER_SENT, String.valueOf(row.getId()), "offer",
                new OfferSentPayload(String.valueOf(row.getId()),
                        String.valueOf(row.getApplicationId()),
                        String.valueOf(row.getPersonId()),
                        row.getOfferNo(),
                        toInstant(row.getExpireAt())));
        publishNotification(String.valueOf(row.getPersonId()), channels, templateCode,
                String.valueOf(row.getId()), variables(row, Map.of()),
                "offer:" + row.getId() + ":sent");
    }

    private void publishNotification(String receiverId, List<String> channels,
                                     String templateCode, String bizId,
                                     Map<String, Object> variables, String dedupKey) {
        outboxPublisher.publish(EventTypes.NOTIFICATION_REQUESTED, bizId, "offer",
                new NotificationRequestedPayload(templateCode, receiverId, null, channels,
                        "OFFER", bizId, variables, dedupKey));
    }

    private Map<String, Object> variables(OfferRow row, Map<String, Object> extra) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("offerNo", row.getOfferNo());
        variables.put("positionTitle", row.getPositionTitle());
        variables.put("candidateName", row.getPersonName());
        if (row.getEntryDate() != null) {
            variables.put("entryDate", row.getEntryDate().toString());
        }
        if (row.getExpireAt() != null) {
            variables.put("expireAt", row.getExpireAt().toInstant(ZoneOffset.UTC).toString());
        }
        variables.putAll(extra);
        return variables;
    }

    private void cacheConfirmLink(long offerId, String token) {
        try {
            redisTemplate.opsForValue().set(
                    com.recruit.common.constant.RedisKeys.offerConfirm(String.valueOf(offerId)),
                    confirmUrl(token), CONFIRM_CACHE_TTL);
        } catch (RuntimeException e) {
            log.warn("缓存 Offer 确认链接失败（不影响本次链接生成）: offerId={}", offerId, e);
        }
    }

    private String confirmUrl(String token) {
        String base = hasText(confirmBaseUrl) ? confirmBaseUrl.trim()
                : "http://localhost:5173/portal/offer/confirm";
        return base + (base.contains("?") ? "&" : "?") + "token=" + token;
    }

    private OfferRow loadVisible(long id) {
        OfferRow row = offerMapper.selectRowById(id);
        if (row == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Offer 不存在");
        }
        DataScopeService.DataScope scope = dataScopeService.current();
        if (!scope.canSee(row.getDeptId(), row.getOwnerHrId())) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "无权访问该 Offer");
        }
        return row;
    }

    private OfferRow loadByToken(String token) {
        if (!hasText(token)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "token 不能为空");
        }
        OfferRow row = offerMapper.selectRowByTokenHash(sha256Hex(token.trim()));
        if (row == null) {
            throw new BizException(ErrorCode.OFFER_STATE_NOT_ALLOWED, "链接无效或已过期");
        }
        return row;
    }

    private void ensurePortalAvailable(OfferRow row) {
        if (STATUS_SENT.equals(row.getStatus())
                && row.getExpireAt() != null && row.getExpireAt().isBefore(nowUtc())) {
            expireOne(row);
            throw new BizException(ErrorCode.OFFER_STATE_NOT_ALLOWED, "Offer 已过期");
        }
        if (!STATUS_SENT.equals(row.getStatus())) {
            throw new BizException(ErrorCode.OFFER_STATE_NOT_ALLOWED, "Offer 已处理");
        }
    }

    private OfferView toView(OfferRow row) {
        return new OfferView(
                IdSupport.asString(row.getId()),
                row.getOfferNo(),
                IdSupport.asString(row.getApplicationId()),
                IdSupport.asString(row.getJobId()),
                row.getPositionTitle(),
                IdSupport.asString(row.getPersonId()),
                row.getPersonName(),
                readJson(row.getSalaryJson()),
                row.getEntryDate(),
                toInstant(row.getExpireAt()),
                row.getStatus(),
                readJson(row.getOnboardingJson()),
                IdSupport.asString(row.getOperatorId()),
                toInstant(row.getConfirmedAt()),
                toInstant(row.getCreatedAt()),
                toInstant(row.getUpdatedAt()),
                row.getVersion());
    }

    private PortalOfferView toPortalView(OfferRow row) {
        if (row == null) {
            throw new BizException(ErrorCode.OFFER_STATE_NOT_ALLOWED, "Offer 已处理");
        }
        return new PortalOfferView(row.getOfferNo(), row.getPositionTitle(),
                readJson(row.getSalaryJson()), row.getEntryDate(), toInstant(row.getExpireAt()),
                row.getStatus(), row.getPersonName(), companyName);
    }

    private Map<String, Object> readJson(String json) {
        if (!hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYS_ERROR, "Offer 配置解析失败", e);
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYS_ERROR, "Offer 配置序列化失败", e);
        }
    }

    private static List<String> normalizeChannels(List<String> values) {
        if (values == null || values.isEmpty()) {
            return DEFAULT_CHANNELS;
        }
        List<String> channels = new ArrayList<>(values.size());
        for (String value : values) {
            String channel = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
            if (!CHANNELS.contains(channel)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "通知渠道非法: " + value);
            }
            if (!channels.contains(channel)) {
                channels.add(channel);
            }
        }
        return channels;
    }

    private static String newOfferNo() {
        String suffix = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 6).toUpperCase(Locale.ROOT);
        return "OFFER-" + LocalDate.now(ZoneOffset.UTC).format(OFFER_NO_DATE) + "-" + suffix;
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BizException(ErrorCode.SYS_ERROR, "token 哈希失败", e);
        }
    }

    private static void audit(String action, Long offerId, long operatorId, String detail) {
        log.info("offer audit action={} offerId={} operatorId={} detail={}",
                action, offerId, operatorId, detail);
    }

    private static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private static LocalDateTime toUtc(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static long currentUserId() {
        return Long.parseLong(UserContext.requireUserId());
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
