package com.recruit.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.constant.RedisKeys;
import com.recruit.common.context.UserContext;
import com.recruit.common.exception.BizException;
import com.recruit.common.idempotency.IdempotencyService;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.PageResult;
import com.recruit.interview.dto.AsyncAiResultForm;
import com.recruit.interview.dto.AsyncAnswerView;
import com.recruit.interview.dto.AsyncInviteLinkView;
import com.recruit.interview.dto.AsyncPaperCreateForm;
import com.recruit.interview.dto.AsyncPaperPageQuery;
import com.recruit.interview.dto.AsyncPaperUpdateForm;
import com.recruit.interview.dto.AsyncPaperView;
import com.recruit.interview.dto.AsyncPublishView;
import com.recruit.interview.dto.AsyncQuestionCreateForm;
import com.recruit.interview.dto.AsyncQuestionView;
import com.recruit.interview.dto.AsyncReviewForm;
import com.recruit.interview.entity.AsyncAnswer;
import com.recruit.interview.entity.AsyncAnswerScore;
import com.recruit.interview.entity.AsyncPaper;
import com.recruit.interview.entity.AsyncQuestion;
import com.recruit.interview.entity.Application;
import com.recruit.interview.entity.Job;
import com.recruit.interview.mapper.ApplicationMapper;
import com.recruit.interview.mapper.AsyncAnswerMapper;
import com.recruit.interview.mapper.AsyncAnswerScoreMapper;
import com.recruit.interview.mapper.AsyncPaperMapper;
import com.recruit.interview.mapper.AsyncQuestionMapper;
import com.recruit.interview.mapper.JobMapper;
import com.recruit.interview.mapper.row.AsyncAnswerRow;
import com.recruit.interview.mapper.row.AsyncPaperRow;
import com.recruit.interview.support.IdSupport;
import com.recruit.interview.support.SecretToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 异步面试管理端（P9-1~P9-4、P9-7）。
 *
 * <p>关键口径（docs/4 §5.7、§7.4.4）：</p>
 * <ul>
 *   <li>一份投递一份试卷；{@code DRAFT → RELEASED → IN_PROGRESS → SUBMITTED → SCORED}，
 *       所有流转带 {@code version} CAS；</li>
 *   <li>发布后题目只读；加题只允许 DRAFT，{@code (paper_id, seq_no)} 唯一；</li>
 *   <li>邀请 token 只存 {@code sha256}，明文只出现在发布响应/重新邀请响应，Redis 缓存 10 分钟；</li>
 *   <li>人工复核分优先于 AI 分；全部题目有分后计算加权总分并置 {@code SCORED}；</li>
 *   <li>HR 重置清空作答并回到 RELEASED，写审计日志。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncInterviewService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_RELEASED = "RELEASED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_SCORED = "SCORED";

    public static final String ANSWER_SUBMITTED = "SUBMITTED";
    public static final String ANSWER_TRANSCRIBED = "TRANSCRIBED";
    public static final String ANSWER_AI_SCORED = "AI_SCORED";
    public static final String ANSWER_REVIEWED = "REVIEWED";

    private static final Set<String> STATUSES = Set.of(STATUS_DRAFT, STATUS_RELEASED,
            STATUS_IN_PROGRESS, STATUS_SUBMITTED, STATUS_SCORED);
    private static final Set<String> INVITABLE = Set.of(STATUS_RELEASED, STATUS_IN_PROGRESS);
    private static final Set<String> REVIEWABLE = Set.of(STATUS_SUBMITTED, STATUS_SCORED);
    private static final String SCOPE_CREATE = "interview.async.create";
    private static final String SCOPE_QUESTION = "interview.async.question";
    private static final int DEFAULT_DURATION_SEC = 3600;
    private static final int DEFAULT_PREPARE_SEC = 60;
    private static final int DEFAULT_ANSWER_SEC = 180;
    private static final BigDecimal DEFAULT_WEIGHT = BigDecimal.ONE;
    private static final Duration INVITE_CACHE_TTL = Duration.ofMinutes(10);
    private static final DateTimeFormatter PAPER_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AsyncPaperMapper paperMapper;
    private final AsyncQuestionMapper questionMapper;
    private final AsyncAnswerMapper answerMapper;
    private final AsyncAnswerScoreMapper scoreMapper;
    private final ApplicationMapper applicationMapper;
    private final JobMapper jobMapper;
    private final DataScopeService dataScopeService;
    private final IdempotencyService idempotencyService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${recruit.async.invite-base-url:http://localhost:5173/portal/async-interview}")
    private String inviteBaseUrl = "http://localhost:5173/portal/async-interview";

    @Value("${recruit.async.invite-ttl-hours:168}")
    private long inviteTtlHours = 168;

    /**
     * 试卷分页。
     *
     * @param query 分页与过滤条件
     * @return 分页试卷（列表不加载题目，避免 N+1）
     */
    public PageResult<AsyncPaperView> page(AsyncPaperPageQuery query) {
        DataScopeService.DataScope scope = dataScopeService.current();
        String status = trimToNull(query.getStatus());
        if (status != null) {
            status = status.toUpperCase(Locale.ROOT);
            if (!STATUSES.contains(status)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "试卷状态非法: " + query.getStatus());
            }
        }
        Long jobId = IdSupport.optionalLong(query.getJobId(), "jobId");
        Long applicationId = IdSupport.optionalLong(query.getApplicationId(), "applicationId");
        String keyword = trimToNull(query.getKeyword());
        String sqlScope = scope.sqlScope();

        long total = paperMapper.countPaperPage(keyword, status, jobId, applicationId, sqlScope,
                scope.deptIds(), scope.userId());
        if (total == 0) {
            return PageResult.of(List.of(), 0, query.getPageNum(), query.getPageSize());
        }
        long offset = (long) (query.getPageNum() - 1) * query.getPageSize();
        List<AsyncPaperRow> rows = paperMapper.selectPaperPage(keyword, status, jobId, applicationId,
                sqlScope, scope.deptIds(), scope.userId(), query.getPageSize(), offset);
        List<AsyncPaperView> views = new ArrayList<>(rows.size());
        for (AsyncPaperRow row : rows) {
            views.add(toView(row, false));
        }
        return PageResult.of(views, total, query.getPageNum(), query.getPageSize());
    }

    /**
     * 试卷详情（含题目）。
     *
     * @param id 试卷 ID
     * @return 详情
     */
    public AsyncPaperView detail(long id) {
        return toView(loadVisible(id), true);
    }

    /**
     * 创建试卷（DRAFT）。
     *
     * @param form 创建入参
     * @param key  Idempotency-Key，可为空
     * @return 新试卷
     */
    @Transactional
    public AsyncPaperView create(AsyncPaperCreateForm form, String key) {
        if (hasText(key)) {
            return idempotencyService.execute(SCOPE_CREATE, key, AsyncPaperView.class,
                    () -> doCreate(form));
        }
        return doCreate(form);
    }

    private AsyncPaperView doCreate(AsyncPaperCreateForm form) {
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
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "无权为该投递创建异步试卷");
        }
        if (paperMapper.selectRowByApplicationId(applicationId) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该投递已存在试卷");
        }

        AsyncPaper paper = new AsyncPaper();
        paper.setPaperNo(newPaperNo());
        paper.setApplicationId(applicationId);
        paper.setStatus(STATUS_DRAFT);
        paper.setQuestionCount(form.getQuestionCount() == null ? 0 : form.getQuestionCount());
        paper.setDurationLimitSec(form.getDurationLimitSec() == null
                ? DEFAULT_DURATION_SEC : form.getDurationLimitSec());
        paper.setSettingsJson(writeJson(form.getSettings()));
        paper.setCreatedBy(currentUserId());
        paper.setVersion(0);
        try {
            paperMapper.insert(paper);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该投递已存在试卷");
        }
        audit("create", paper.getId(), currentUserId(), "status=DRAFT");
        return toView(paperMapper.selectRowById(paper.getId()), true);
    }

    /**
     * 修改 DRAFT 试卷。
     *
     * @param id   试卷 ID
     * @param form 修改入参
     * @return 修改后的试卷
     */
    @Transactional
    public AsyncPaperView update(long id, AsyncPaperUpdateForm form) {
        AsyncPaperRow current = loadVisible(id);
        requireStatus(current, STATUS_DRAFT, "仅草稿试卷可修改");
        requireVersion(current.getVersion(), form.getVersion());

        int duration = form.getDurationLimitSec() == null
                ? current.getDurationLimitSec() : form.getDurationLimitSec();
        String settingsJson = form.getSettings() == null
                ? current.getSettingsJson() : writeJson(form.getSettings());
        if (paperMapper.updateDraft(id, duration, settingsJson, form.getQuestionCount(),
                form.getVersion()) == 0) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突，请刷新后重试");
        }
        audit("update", id, currentUserId(), "status=DRAFT");
        return detail(id);
    }

    /**
     * 新增题目（仅 DRAFT）。
     *
     * @param id   试卷 ID
     * @param form 题目入参
     * @param key  Idempotency-Key，可为空
     * @return 新题目
     */
    @Transactional
    public AsyncQuestionView addQuestion(long id, AsyncQuestionCreateForm form, String key) {
        if (hasText(key)) {
            return idempotencyService.execute(SCOPE_QUESTION, key, AsyncQuestionView.class,
                    () -> doAddQuestion(id, form));
        }
        return doAddQuestion(id, form);
    }

    private AsyncQuestionView doAddQuestion(long id, AsyncQuestionCreateForm form) {
        AsyncPaperRow paper = loadVisible(id);
        requireStatus(paper, STATUS_DRAFT, "发布后题目只读");
        AsyncQuestion question = new AsyncQuestion();
        question.setPaperId(id);
        question.setSeqNo(form.getSeqNo());
        question.setQuestionText(form.getQuestionText().trim());
        question.setPrepareSec(form.getPrepareSec() == null
                ? DEFAULT_PREPARE_SEC : form.getPrepareSec());
        question.setAnswerSec(form.getAnswerSec() == null
                ? DEFAULT_ANSWER_SEC : form.getAnswerSec());
        question.setWeight(form.getWeight() == null ? DEFAULT_WEIGHT : form.getWeight());
        try {
            questionMapper.insert(question);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "题号重复，请换一个序号");
        }
        paperMapper.refreshQuestionCount(id);
        audit("add_question", id, currentUserId(), "seqNo=" + form.getSeqNo());
        return toQuestionView(question);
    }

    /**
     * 发布试卷：DRAFT → RELEASED，生成邀请 token；已发布时幂等返回。
     *
     * @param id      试卷 ID
     * @param version 期望版本
     * @return 试卷 + 一次性邀请链接
     */
    @Transactional
    public AsyncPublishView publish(long id, int version) {
        AsyncPaperRow current = loadVisible(id);
        if (!STATUS_DRAFT.equals(current.getStatus())) {
            if (!INVITABLE.contains(current.getStatus())
                    && !STATUS_SUBMITTED.equals(current.getStatus())
                    && !STATUS_SCORED.equals(current.getStatus())) {
                throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "当前状态不可发布");
            }
            requireVersion(current.getVersion(), version);
            return new AsyncPublishView(toView(current, true), null,
                    toInstant(current.getInviteExpireAt()));
        }
        requireVersion(current.getVersion(), version);
        long count = questionMapper.countByPaperId(id);
        if (count == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请先添加题目再发布");
        }
        paperMapper.refreshQuestionCount(id);

        String token = SecretToken.newToken();
        LocalDateTime expireAt = nowUtc().plusHours(inviteTtlHours);
        if (paperMapper.publish(id, SecretToken.sha256Hex(token), expireAt, version) == 0) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突，请刷新后重试");
        }
        String url = inviteUrl(token);
        cacheInvite(id, url);
        audit("publish", id, currentUserId(), "status=RELEASED questionCount=" + count);
        return new AsyncPublishView(toView(paperMapper.selectRowById(id), true), url,
                toInstant(expireAt));
    }

    /**
     * 重新生成邀请链接（旧链接立即失效）。
     *
     * @param id      试卷 ID
     * @param version 期望版本
     * @return 新链接
     */
    @Transactional
    public AsyncInviteLinkView inviteLink(long id, int version) {
        AsyncPaperRow current = loadVisible(id);
        if (!INVITABLE.contains(current.getStatus())) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "当前状态不可生成邀请链接");
        }
        requireVersion(current.getVersion(), version);
        String token = SecretToken.newToken();
        LocalDateTime expireAt = nowUtc().plusHours(inviteTtlHours);
        if (paperMapper.rotateInvite(id, SecretToken.sha256Hex(token), expireAt, version) == 0) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突，请刷新后重试");
        }
        String url = inviteUrl(token);
        cacheInvite(id, url);
        audit("invite_link", id, currentUserId(), "expireAt=" + expireAt);
        return new AsyncInviteLinkView(url, toInstant(expireAt));
    }

    /**
     * 读取缓存中的邀请链接（notify 内部接口用，不轮换 token）。
     *
     * @param id 试卷 ID
     * @return 链接
     */
    public AsyncInviteLinkView cachedInviteLink(long id) {
        AsyncPaperRow paper = paperMapper.selectRowById(id);
        if (paper == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "试卷不存在");
        }
        String url;
        try {
            url = redisTemplate.opsForValue().get(RedisKeys.asyncInvite(String.valueOf(id)));
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.SYS_ERROR, "邀请链接缓存不可用", e);
        }
        if (!hasText(url)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "邀请链接已过期，请重新生成");
        }
        return new AsyncInviteLinkView(url, toInstant(paper.getInviteExpireAt()));
    }

    /**
     * 回看作答列表。
     *
     * @param id 试卷 ID
     * @return 作答列表
     */
    public List<AsyncAnswerView> answers(long id) {
        loadVisible(id);
        List<AsyncAnswerRow> rows = answerMapper.selectByPaperId(id);
        List<AsyncAnswerView> views = new ArrayList<>(rows.size());
        for (AsyncAnswerRow row : rows) {
            views.add(toAnswerView(row));
        }
        return views;
    }

    /**
     * 人工复核单题（人工分优先）。
     *
     * @param id       试卷 ID
     * @param answerId 作答 ID
     * @param form     复核入参
     * @return 复核后的作答
     */
    @Transactional
    public AsyncAnswerView review(long id, long answerId, AsyncReviewForm form) {
        AsyncPaperRow paper = loadVisible(id);
        if (!REVIEWABLE.contains(paper.getStatus())) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "候选人提交后才能复核");
        }
        AsyncAnswerRow answer = answerMapper.selectRowById(answerId);
        if (answer == null || !Long.valueOf(id).equals(answer.getPaperId())) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "作答不存在");
        }
        requireVersion(answer.getVersion(), form.getVersion());
        if (answerMapper.updateManual(answerId, form.getManualScore(), trimToNull(form.getComment()),
                currentUserId(), nowUtc(), form.getVersion()) == 0) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突，请刷新后重试");
        }
        recomputePaperScore(paper, currentUserId());
        audit("review", id, currentUserId(), "answerId=" + answerId + " score=" + form.getManualScore());
        return toAnswerView(answerMapper.selectRowById(answerId));
    }

    /**
     * AI 回写逐题转写/评分（ai-service 内部接口）。
     *
     * @param answerId 作答 ID
     * @param form     回写入参
     * @return 回写后的作答
     */
    @Transactional
    public AsyncAnswerView applyAiResult(long answerId, AsyncAiResultForm form) {
        AsyncAnswerRow answer = answerMapper.selectRowById(answerId);
        if (answer == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "作答不存在");
        }
        AsyncPaperRow paper = paperMapper.selectRowById(answer.getPaperId());
        if (paper == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "试卷不存在");
        }
        upsertAiScore(answerId, form);
        if (!ANSWER_REVIEWED.equals(answer.getStatus())) {
            String status = form.getAiScore() == null ? ANSWER_TRANSCRIBED : ANSWER_AI_SCORED;
            if (answerMapper.updateAiResult(answerId, trimToNull(form.getTranscriptText()),
                    form.getAiScore(), status, answer.getVersion()) == 0) {
                throw new BizException(ErrorCode.VERSION_CONFLICT);
            }
        }
        if (REVIEWABLE.contains(paper.getStatus())) {
            recomputePaperScore(paper, null);
        }
        audit("ai_result", paper.getId(), currentUserIdOrNull(), "answerId=" + answerId);
        return toAnswerView(answerMapper.selectRowById(answerId));
    }

    /**
     * HR 重置作答：清空作答并回到 RELEASED，写审计。
     *
     * @param id      试卷 ID
     * @param version 期望版本
     * @return 重置后的试卷
     */
    @Transactional
    public AsyncPaperView reset(long id, int version) {
        AsyncPaperRow current = loadVisible(id);
        if (!REVIEWABLE.contains(current.getStatus())) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "仅已提交/已评分试卷可重置");
        }
        requireVersion(current.getVersion(), version);
        answerMapper.deleteByPaperId(id);
        if (paperMapper.reset(id, version) == 0) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突，请刷新后重试");
        }
        audit("reset", id, currentUserId(), "status=RELEASED");
        return detail(id);
    }

    private AsyncPaperRow loadVisible(long id) {
        AsyncPaperRow row = paperMapper.selectRowById(id);
        if (row == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "异步试卷不存在");
        }
        DataScopeService.DataScope scope = dataScopeService.current();
        if (!scope.canSee(row.getDeptId(), row.getOwnerHrId())) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "无权访问该异步试卷");
        }
        return row;
    }

    private void recomputePaperScore(AsyncPaperRow paper, Long reviewerId) {
        List<AsyncQuestion> questions = questionMapper.selectByPaperId(paper.getId());
        List<AsyncAnswerRow> answers = answerMapper.selectByPaperId(paper.getId());
        BigDecimal total = weightedTotal(questions, answers);
        if (total == null || answers.size() != questions.size() || questions.isEmpty()) {
            return;
        }
        if (paperMapper.markScored(paper.getId(), total, reviewerId, nowUtc(),
                paper.getVersion()) == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT);
        }
    }

    private static BigDecimal weightedTotal(List<AsyncQuestion> questions, List<AsyncAnswerRow> answers) {
        Map<Long, BigDecimal> scoreByQuestion = new HashMap<>();
        for (AsyncAnswerRow answer : answers) {
            BigDecimal score = answer.getManualScore() != null
                    ? answer.getManualScore() : answer.getAiScore();
            if (score != null) {
                scoreByQuestion.put(answer.getQuestionId(), score);
            }
        }
        BigDecimal weighted = BigDecimal.ZERO;
        BigDecimal weights = BigDecimal.ZERO;
        for (AsyncQuestion question : questions) {
            BigDecimal score = scoreByQuestion.get(question.getId());
            if (score == null) {
                return null;
            }
            BigDecimal weight = question.getWeight() == null ? DEFAULT_WEIGHT : question.getWeight();
            weighted = weighted.add(score.multiply(weight));
            weights = weights.add(weight);
        }
        if (weights.signum() == 0) {
            return null;
        }
        return weighted.divide(weights, 2, RoundingMode.HALF_UP);
    }

    private void upsertAiScore(long answerId, AsyncAiResultForm form) {
        String dimensionJson = form.getDimensionScores() == null
                ? "{}" : writeJson(form.getDimensionScores());
        int promptVersion = form.getPromptVersion() == null ? 1 : form.getPromptVersion();
        AsyncAnswerScore existing = scoreMapper.selectByAnswerId(answerId);
        if (existing == null) {
            AsyncAnswerScore score = new AsyncAnswerScore();
            score.setAnswerId(answerId);
            score.setDimensionScores(dimensionJson);
            score.setComment(trimToNull(form.getComment()));
            score.setModel(form.getModel());
            score.setPromptVersion(promptVersion);
            try {
                scoreMapper.insert(score);
            } catch (DuplicateKeyException e) {
                scoreMapper.updateByAnswerId(answerId, dimensionJson,
                        trimToNull(form.getComment()), form.getModel(), promptVersion);
            }
            return;
        }
        scoreMapper.updateByAnswerId(answerId, dimensionJson,
                trimToNull(form.getComment()), form.getModel(), promptVersion);
    }

    private AsyncPaperView toView(AsyncPaperRow row, boolean withQuestions) {
        List<AsyncQuestionView> questions = withQuestions ? questionViews(row.getId()) : List.of();
        return new AsyncPaperView(
                IdSupport.asString(row.getId()),
                row.getPaperNo(),
                IdSupport.asString(row.getApplicationId()),
                IdSupport.asString(row.getJobId()),
                hasText(row.getJobTitle()) ? row.getJobTitle() : null,
                IdSupport.asString(row.getPersonId()),
                row.getPersonName(),
                row.getStatus(),
                row.getQuestionCount(),
                row.getDurationLimitSec(),
                readJson(row.getSettingsJson()),
                row.getTotalScore(),
                toInstant(row.getInviteExpireAt()),
                toInstant(row.getStartedAt()),
                toInstant(row.getSubmittedAt()),
                toInstant(row.getReviewedAt()),
                questions,
                toInstant(row.getCreatedAt()),
                row.getVersion());
    }

    private List<AsyncQuestionView> questionViews(long paperId) {
        List<AsyncQuestion> rows = questionMapper.selectByPaperId(paperId);
        List<AsyncQuestionView> views = new ArrayList<>(rows.size());
        for (AsyncQuestion row : rows) {
            views.add(toQuestionView(row));
        }
        return views;
    }

    private static AsyncQuestionView toQuestionView(AsyncQuestion row) {
        return new AsyncQuestionView(IdSupport.asString(row.getId()), row.getSeqNo(),
                row.getQuestionText(), row.getPrepareSec(), row.getAnswerSec(), row.getWeight());
    }

    private static AsyncAnswerView toAnswerView(AsyncAnswerRow row) {
        return new AsyncAnswerView(
                IdSupport.asString(row.getId()),
                IdSupport.asString(row.getQuestionId()),
                row.getSeqNo(),
                row.getQuestionText(),
                row.getWeight(),
                IdSupport.asString(row.getVideoFileId()),
                IdSupport.asString(row.getAudioFileId()),
                row.getAnswerSec(),
                row.getTranscriptText(),
                row.getAiScore(),
                row.getManualScore(),
                row.getManualComment(),
                row.getStatus(),
                toInstant(row.getSubmittedAt()),
                row.getVersion());
    }

    private static void requireStatus(AsyncPaperRow row, String expected, String message) {
        if (!expected.equals(row.getStatus())) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, message);
        }
    }

    private static void requireVersion(Integer current, int expected) {
        int value = current == null ? 0 : current;
        if (value != expected) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突，请刷新后重试");
        }
    }

    private void cacheInvite(long paperId, String url) {
        try {
            redisTemplate.opsForValue().set(RedisKeys.asyncInvite(String.valueOf(paperId)),
                    url, INVITE_CACHE_TTL);
        } catch (RuntimeException e) {
            log.warn("缓存异步面试邀请链接失败（不影响本次链接生成）: paperId={}", paperId, e);
        }
    }

    private String inviteUrl(String token) {
        String base = hasText(inviteBaseUrl) ? inviteBaseUrl.trim()
                : "http://localhost:5173/portal/async-interview";
        return base + (base.contains("?") ? "&" : "?") + "token=" + token;
    }

    private Map<String, Object> readJson(String json) {
        if (!hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYS_ERROR, "异步面试配置解析失败", e);
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYS_ERROR, "异步面试配置序列化失败", e);
        }
    }

    private static String newPaperNo() {
        String suffix = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 6).toUpperCase(Locale.ROOT);
        return "ASYNC-" + LocalDate.now(ZoneOffset.UTC).format(PAPER_NO_DATE) + "-" + suffix;
    }

    private static long currentUserId() {
        return Long.parseLong(UserContext.requireUserId());
    }

    private static Long currentUserIdOrNull() {
        try {
            return currentUserId();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void audit(String action, Long paperId, Long operatorId, String detail) {
        log.info("async interview audit action={} paperId={} operatorId={} detail={}",
                action, paperId, operatorId, detail);
    }

    private static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
