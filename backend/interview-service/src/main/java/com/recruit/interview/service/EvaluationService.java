package com.recruit.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.context.UserContext;
import com.recruit.common.exception.BizException;
import com.recruit.common.idempotency.IdempotencyService;
import com.recruit.common.result.ErrorCode;
import com.recruit.contracts.event.EventTypes;
import com.recruit.contracts.event.payload.InterviewResultSubmittedPayload;
import com.recruit.interview.dto.AiDimensionSuggestionView;
import com.recruit.interview.dto.AiDraftView;
import com.recruit.interview.dto.DimensionComparisonView;
import com.recruit.interview.dto.EvaluationComparisonView;
import com.recruit.interview.dto.EvaluationReopenForm;
import com.recruit.interview.dto.EvaluationSubmitForm;
import com.recruit.interview.dto.EvaluationSummaryView;
import com.recruit.interview.dto.EvaluationView;
import com.recruit.interview.dto.MyEvaluationTaskView;
import com.recruit.interview.entity.Evaluation;
import com.recruit.interview.entity.EvaluationDimension;
import com.recruit.interview.entity.InterviewMinutes;
import com.recruit.interview.entity.JobStageTemplate;
import com.recruit.interview.event.OutboxPublisher;
import com.recruit.interview.mapper.EvaluationDimensionMapper;
import com.recruit.interview.mapper.EvaluationMapper;
import com.recruit.interview.mapper.InterviewMapper;
import com.recruit.interview.mapper.InterviewMinutesMapper;
import com.recruit.interview.mapper.JobStageTemplateMapper;
import com.recruit.interview.mapper.row.InterviewRow;
import com.recruit.interview.mapper.row.MyEvaluationRow;
import com.recruit.interview.support.IdSupport;
import com.recruit.interview.support.ScheduleSlotWindow;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 评价（P6-1~P6-7）。
 *
 * <p>关键口径（docs/4 §5.5、§7.4）：</p>
 * <ul>
 *   <li>{@code (interview_id, round_no, interviewer_id)} 唯一；重复提交返回 409 {@code ITV3102}；</li>
 *   <li>面试官只能提交本人评价，HR_ADMIN 也不能代录；更正走重开；</li>
 *   <li>维度必须与提交的 {@code template_version} 一致，分数落在 {@code [0, full_score]}；</li>
 *   <li>总分 = Σ(score × weight)，保留 1 位小数；任一分项低于否决阈值直接 FAIL；</li>
 *   <li>单份提交不发事件；全部必填面试官提交后才发 {@code interview.result.submitted}；</li>
 *   <li>AI 草稿只读；无转写/草稿缺失返回 422 {@code AIS5104}，不写正式评价。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class EvaluationService {

    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_REOPENED = "REOPENED";
    public static final String TASK_PENDING = "PENDING";
    public static final String RESULT_PASS = "PASS";
    public static final String RESULT_FAIL = "FAIL";

    private static final Set<String> RESULTS = Set.of(RESULT_PASS, RESULT_FAIL);
    private static final String REVIEW_INSUFFICIENT = "INSUFFICIENT_TRANSCRIPT";
    private static final String SCOPE_SUBMIT = "interview.evaluation.submit";
    private static final int MAX_QUERY_DAYS = 366;
    private static final int DEFAULT_PAST_DAYS = 30;
    private static final int DEFAULT_FUTURE_DAYS = 60;

    private static final TypeReference<Map<String, BigDecimal>> SCORE_MAP = new TypeReference<>() {
    };

    private final EvaluationMapper evaluationMapper;
    private final InterviewMapper interviewMapper;
    private final InterviewMinutesMapper minutesMapper;
    private final JobStageTemplateMapper templateMapper;
    private final EvaluationDimensionMapper dimensionMapper;
    private final OutboxPublisher outboxPublisher;
    private final ObjectMapper objectMapper;
    private final DataScopeService dataScopeService;
    private final IdempotencyService idempotencyService;

    /**
     * 某场次全部评价（含重开未重提的）。
     *
     * @param interviewId 面试 ID
     * @return 评价列表
     */
    public List<EvaluationView> list(long interviewId) {
        InterviewRow interview = loadInterview(interviewId);
        List<Evaluation> rows = evaluationMapper.selectByInterviewId(interviewId);
        List<EvaluationView> views = new ArrayList<>(rows.size());
        Map<String, TemplateDims> cache = new LinkedHashMap<>();
        for (Evaluation row : rows) {
            views.add(toView(row, dims(interview.getJobId(), row.getTemplateVersion(), cache)));
        }
        return views;
    }

    /**
     * 提交评价：幂等键可选；同一面试官同一轮仅一条。
     *
     * @param interviewId 面试 ID
     * @param form        提交入参
     * @param key         Idempotency-Key，可为空
     * @return 评价
     */
    @Transactional
    public EvaluationView submit(long interviewId, EvaluationSubmitForm form, String key) {
        if (hasText(key)) {
            return idempotencyService.execute(SCOPE_SUBMIT, key, EvaluationView.class,
                    () -> doSubmit(interviewId, form));
        }
        return doSubmit(interviewId, form);
    }

    private EvaluationView doSubmit(long interviewId, EvaluationSubmitForm form) {
        InterviewRow interview = loadInterview(interviewId);
        long userId = currentUserId();
        List<Long> interviewerIds = parseIds(interview.getInterviewerIds());
        if (!interviewerIds.contains(userId)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "只能提交本人的面试评价");
        }
        if (ScheduleService.STATUS_CANCELLED.equals(interview.getStatus())) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "面试已取消，不能提交评价");
        }

        TemplateDims dims = loadDims(interview.getJobId(), form.getTemplateVersion());
        Map<String, BigDecimal> scores = normalizeScores(form.getDimensionScores(), dims);
        String result = normalizeResult(form.getResult());
        BigDecimal totalScore = totalScore(scores, dims);
        if (isVetoed(scores, dims)) {
            result = RESULT_FAIL;
        }

        LocalDateTime submittedAt = LocalDateTime.now(ZoneOffset.UTC);
        Evaluation existing = evaluationMapper.selectByInterviewAndInterviewer(
                interviewId, interview.getRoundNo(), userId);
        Evaluation saved;
        if (existing == null) {
            if (form.getVersion() != null && form.getVersion() != 0) {
                throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突，请刷新后重试");
            }
            Evaluation evaluation = new Evaluation();
            evaluation.setInterviewId(interviewId);
            evaluation.setRoundNo(interview.getRoundNo());
            evaluation.setInterviewerId(userId);
            evaluation.setDimensionScores(writeJson(scores));
            evaluation.setComment(trimToNull(form.getComment()));
            evaluation.setResult(result);
            evaluation.setStatus(STATUS_SUBMITTED);
            evaluation.setTemplateVersion(dims.template().getTemplateVer());
            evaluation.setSubmittedAt(submittedAt);
            evaluation.setVersion(0);
            try {
                evaluationMapper.insert(evaluation);
            } catch (DuplicateKeyException e) {
                throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "评价已提交，请勿重复提交");
            }
            saved = evaluation;
        } else {
            if (STATUS_SUBMITTED.equals(existing.getStatus())) {
                throw new BizException(ErrorCode.ILLEGAL_TRANSITION,
                        "评价已提交，如需更正请联系 HR 重开");
            }
            if (!Objects.equals(existing.getVersion(), form.getVersion())) {
                throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突，请刷新后重试");
            }
            Evaluation update = new Evaluation();
            update.setId(existing.getId());
            update.setVersion(existing.getVersion());
            update.setDimensionScores(writeJson(scores));
            update.setComment(trimToNull(form.getComment()));
            update.setResult(result);
            update.setTemplateVersion(dims.template().getTemplateVer());
            update.setSubmittedAt(submittedAt);
            if (evaluationMapper.resubmit(update) == 0) {
                throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突或评价状态不允许重提");
            }
            saved = evaluationMapper.selectById(existing.getId());
        }

        publishIfCompleted(interview);
        return toView(saved, dims);
    }

    /**
     * 我的评价任务：当前用户被安排为面试官的场次，默认前后各一段窗口。
     *
     * @param dateFrom 开始日期（业务时区），可为空
     * @param dateTo   结束日期（业务时区），可为空
     * @return 任务列表
     */
    public List<MyEvaluationTaskView> myTasks(LocalDate dateFrom, LocalDate dateTo) {
        long userId = currentUserId();
        LocalDate today = LocalDate.now(ScheduleSlotWindow.BUSINESS_ZONE);
        LocalDate from = dateFrom == null ? today.minusDays(DEFAULT_PAST_DAYS) : dateFrom;
        LocalDate to = dateTo == null ? today.plusDays(DEFAULT_FUTURE_DAYS) : dateTo;
        if (to.isBefore(from)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "结束日期不能早于开始日期");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_QUERY_DAYS) {
            throw new BizException(ErrorCode.PARAM_INVALID, "查询范围不能超过 366 天");
        }
        List<MyEvaluationRow> rows = evaluationMapper.selectMyTasks(userId, String.valueOf(userId),
                utcStartOfDay(from), utcStartOfDay(to.plusDays(1)));
        List<MyEvaluationTaskView> views = new ArrayList<>(rows.size());
        Map<String, TemplateDims> cache = new LinkedHashMap<>();
        for (MyEvaluationRow row : rows) {
            String status = hasText(row.getEvaluationStatus())
                    ? row.getEvaluationStatus() : TASK_PENDING;
            BigDecimal totalScore = null;
            if (hasText(row.getDimensionScores()) && row.getTemplateVersion() != null) {
                TemplateDims dims = dims(row.getJobId(), row.getTemplateVersion(), cache);
                totalScore = totalScore(readScores(row.getDimensionScores()), dims);
            }
            views.add(new MyEvaluationTaskView(
                    IdSupport.asString(row.getInterviewId()),
                    IdSupport.asString(row.getApplicationId()),
                    row.getJobTitle(),
                    row.getPersonName(),
                    row.getRoundNo(),
                    toInstant(row.getStartTime()),
                    toInstant(row.getEndTime()),
                    status,
                    IdSupport.asString(row.getEvaluationId()),
                    row.getResult(),
                    totalScore,
                    toInstant(row.getSubmittedAt())));
        }
        return views;
    }

    /**
     * 多面试官对比：维度均分、各评价摘要、分歧度与建议。
     *
     * @param interviewId 面试 ID
     * @return 对比结果
     */
    public EvaluationComparisonView comparison(long interviewId) {
        InterviewRow interview = loadInterview(interviewId);
        List<Evaluation> all = evaluationMapper.selectByInterviewId(interviewId);
        List<Evaluation> submitted = new ArrayList<>(all.size());
        Integer templateVersion = null;
        for (Evaluation row : all) {
            if (!STATUS_SUBMITTED.equals(row.getStatus())) {
                continue;
            }
            if (templateVersion == null) {
                templateVersion = row.getTemplateVersion();
            }
            if (Objects.equals(templateVersion, row.getTemplateVersion())) {
                submitted.add(row);
            }
        }
        if (submitted.isEmpty()) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "该场次暂无已提交评价");
        }
        TemplateDims dims = loadDims(interview.getJobId(), templateVersion);

        List<DimensionComparisonView> dimensions = new ArrayList<>(dims.dimensions().size());
        for (EvaluationDimension dim : dims.dimensions()) {
            List<BigDecimal> scores = new ArrayList<>(submitted.size());
            BigDecimal sum = BigDecimal.ZERO;
            for (Evaluation row : submitted) {
                BigDecimal score = readScores(row.getDimensionScores()).get(dim.getDimCode());
                BigDecimal value = score == null ? BigDecimal.ZERO : score;
                scores.add(value);
                sum = sum.add(value);
            }
            BigDecimal average = sum.divide(BigDecimal.valueOf(submitted.size()), 1, RoundingMode.HALF_UP);
            dimensions.add(new DimensionComparisonView(dim.getDimCode(), dim.getDimName(),
                    dim.getWeight(), average, scores));
        }

        List<EvaluationSummaryView> summaries = new ArrayList<>(submitted.size());
        BigDecimal min = null;
        BigDecimal max = null;
        int passCount = 0;
        for (Evaluation row : submitted) {
            BigDecimal total = totalScore(readScores(row.getDimensionScores()), dims);
            if (RESULT_PASS.equals(row.getResult())) {
                passCount++;
            }
            min = min == null || total.compareTo(min) < 0 ? total : min;
            max = max == null || total.compareTo(max) > 0 ? total : max;
            summaries.add(new EvaluationSummaryView(
                    IdSupport.asString(row.getId()),
                    IdSupport.asString(row.getInterviewerId()),
                    interviewerName(row.getInterviewerId()),
                    total,
                    row.getResult(),
                    row.getComment(),
                    toInstant(row.getSubmittedAt())));
        }
        BigDecimal divergence = max.subtract(min).setScale(1, RoundingMode.HALF_UP);
        String suggestion = passCount * 3 >= submitted.size() * 2 ? RESULT_PASS : RESULT_FAIL;
        return new EvaluationComparisonView(
                IdSupport.asString(interviewId),
                interview.getRoundNo(),
                templateVersion,
                dimensions,
                summaries,
                divergence,
                suggestion);
    }

    /**
     * AI 评价草稿（只读）。
     *
     * @param interviewId 面试 ID
     * @return 草稿；无转写返回 422 {@code AIS5104}
     */
    public AiDraftView aiDraft(long interviewId) {
        loadInterview(interviewId);
        InterviewMinutes minutes = minutesMapper.selectByInterviewId(interviewId);
        if (minutes == null || minutes.getTranscriptSegmentCount() == null
                || minutes.getTranscriptSegmentCount() <= 0
                || REVIEW_INSUFFICIENT.equals(minutes.getReviewStatus())) {
            throw new BizException(ErrorCode.INSUFFICIENT_TRANSCRIPT, "转写内容不足，无法生成 AI 草稿");
        }
        JsonNode root = readJsonNode(minutes.getAiEvaluation());
        if (root == null || root.isNull() || root.isMissingNode()) {
            throw new BizException(ErrorCode.INSUFFICIENT_TRANSCRIPT, "AI 草稿尚未生成");
        }
        return new AiDraftView(
                suggestions(root),
                textList(root, "evidence"),
                textList(root, "risks"),
                minutes.getModel(),
                minutes.getTranscriptSegmentCount(),
                toInstant(minutes.getUpdatedAt()));
    }

    /**
     * 重开已提交评价：仅 HR_ADMIN，记录原因与操作人。
     *
     * @param evaluationId 评价 ID
     * @param form         重开入参
     * @return 重开后的评价
     */
    @Transactional
    public EvaluationView reopen(long evaluationId, EvaluationReopenForm form) {
        if (!UserContext.hasRole("HR_ADMIN")) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "仅 HR_ADMIN 可重开评价");
        }
        Evaluation evaluation = evaluationMapper.selectById(evaluationId);
        if (evaluation == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "评价不存在");
        }
        InterviewRow interview = loadInterview(evaluation.getInterviewId());
        if (!STATUS_SUBMITTED.equals(evaluation.getStatus())) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "仅已提交的评价可以重开");
        }
        if (!Objects.equals(evaluation.getVersion(), form.getVersion())) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突，请刷新后重试");
        }
        int rows = evaluationMapper.reopen(evaluationId, currentUserId(),
                form.getReason().trim(), form.getVersion());
        if (rows == 0) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突或评价状态不允许重开");
        }
        Evaluation saved = evaluationMapper.selectById(evaluationId);
        return toView(saved, loadDims(interview.getJobId(), saved.getTemplateVersion()));
    }

    private void publishIfCompleted(InterviewRow interview) {
        List<Long> interviewerIds = parseIds(interview.getInterviewerIds());
        List<Evaluation> rows = evaluationMapper.selectByInterviewId(interview.getId());
        Set<Long> submittedIds = new LinkedHashSet<>();
        List<Evaluation> submitted = new ArrayList<>(rows.size());
        for (Evaluation row : rows) {
            if (STATUS_SUBMITTED.equals(row.getStatus())) {
                submittedIds.add(row.getInterviewerId());
                submitted.add(row);
            }
        }
        if (!submittedIds.containsAll(interviewerIds)) {
            return;
        }
        TemplateDims dims = loadDims(interview.getJobId(), submitted.get(0).getTemplateVersion());
        BigDecimal sum = BigDecimal.ZERO;
        boolean allPass = true;
        for (Evaluation row : submitted) {
            sum = sum.add(totalScore(readScores(row.getDimensionScores()), dims));
            allPass = allPass && RESULT_PASS.equals(row.getResult());
        }
        BigDecimal totalScore = sum.divide(BigDecimal.valueOf(submitted.size()), 1, RoundingMode.HALF_UP);
        outboxPublisher.publish(EventTypes.INTERVIEW_RESULT_SUBMITTED,
                String.valueOf(interview.getId()), "interview",
                new InterviewResultSubmittedPayload(
                        String.valueOf(interview.getId()),
                        String.valueOf(interview.getApplicationId()),
                        interview.getRoundNo(),
                        allPass ? RESULT_PASS : RESULT_FAIL,
                        totalScore,
                        Instant.now()));
    }

    private InterviewRow loadInterview(long interviewId) {
        InterviewRow row = interviewMapper.selectRowById(interviewId);
        if (row == null) {
            throw new BizException(ErrorCode.APP_NOT_FOUND, "面试安排不存在");
        }
        DataScopeService.DataScope scope = dataScopeService.current();
        if (!scope.canSee(row.getDeptId(), row.getOwnerHrId())
                && !parseIds(row.getInterviewerIds()).contains(scope.userId())) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "无权访问该面试安排");
        }
        return row;
    }

    private TemplateDims loadDims(long jobId, int templateVersion) {
        JobStageTemplate template = templateMapper.selectByJobIdAndVer(jobId, templateVersion);
        if (template == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "评价模板版本不存在，请刷新后重试");
        }
        List<EvaluationDimension> rows = dimensionMapper.selectByTemplateId(template.getId());
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "评价模板未配置评分维度");
        }
        return new TemplateDims(template, rows);
    }

    private TemplateDims dims(long jobId, Integer templateVersion, Map<String, TemplateDims> cache) {
        if (templateVersion == null) {
            JobStageTemplate active = templateMapper.selectActiveByJobId(jobId);
            if (active == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "评价模板未配置评分维度");
            }
            templateVersion = active.getTemplateVer();
        }
        String key = jobId + ":" + templateVersion;
        TemplateDims cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        TemplateDims loaded = loadDims(jobId, templateVersion);
        cache.put(key, loaded);
        return loaded;
    }

    private Map<String, BigDecimal> normalizeScores(Map<String, BigDecimal> input, TemplateDims dims) {
        Map<String, BigDecimal> normalized = new LinkedHashMap<>();
        Map<String, String> byUpper = new LinkedHashMap<>();
        for (EvaluationDimension dim : dims.dimensions()) {
            byUpper.put(dim.getDimCode().toUpperCase(Locale.ROOT), dim.getDimCode());
        }
        for (Map.Entry<String, BigDecimal> entry : input.entrySet()) {
            String rawKey = entry.getKey() == null ? "" : entry.getKey().trim();
            String canonical = byUpper.get(rawKey.toUpperCase(Locale.ROOT));
            if (canonical == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "未知评分维度: " + rawKey);
            }
            if (normalized.containsKey(canonical)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "评分维度重复: " + canonical);
            }
            normalized.put(canonical, entry.getValue());
        }
        for (EvaluationDimension dim : dims.dimensions()) {
            BigDecimal score = normalized.get(dim.getDimCode());
            if (score == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "缺少评分维度: " + dim.getDimCode());
            }
            if (score.signum() < 0 || score.compareTo(dim.getFullScore()) > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "维度 " + dim.getDimCode() + " 分数必须在 0~" + dim.getFullScore() + " 之间");
            }
        }
        return normalized;
    }

    private static BigDecimal totalScore(Map<String, BigDecimal> scores, TemplateDims dims) {
        BigDecimal sum = BigDecimal.ZERO;
        for (EvaluationDimension dim : dims.dimensions()) {
            BigDecimal score = scores.get(dim.getDimCode());
            if (score == null) {
                continue;
            }
            sum = sum.add(score.multiply(dim.getWeight()));
        }
        return sum.setScale(1, RoundingMode.HALF_UP);
    }

    private static boolean isVetoed(Map<String, BigDecimal> scores, TemplateDims dims) {
        for (EvaluationDimension dim : dims.dimensions()) {
            if (dim.getVetoThreshold() == null) {
                continue;
            }
            BigDecimal score = scores.get(dim.getDimCode());
            if (score != null && score.compareTo(dim.getVetoThreshold()) < 0) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeResult(String value) {
        String result = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!RESULTS.contains(result)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "评价结果只能为 PASS 或 FAIL");
        }
        return result;
    }

    private EvaluationView toView(Evaluation row, TemplateDims dims) {
        Map<String, BigDecimal> scores = readScores(row.getDimensionScores());
        return new EvaluationView(
                IdSupport.asString(row.getId()),
                IdSupport.asString(row.getInterviewId()),
                row.getRoundNo(),
                IdSupport.asString(row.getInterviewerId()),
                scores,
                row.getComment(),
                row.getResult(),
                totalScore(scores, dims),
                row.getTemplateVersion(),
                row.getStatus(),
                toInstant(row.getSubmittedAt()),
                IdSupport.asString(row.getReopenedBy()),
                toInstant(row.getReopenedAt()),
                row.getReopenReason(),
                row.getVersion());
    }

    private Map<String, BigDecimal> readScores(String json) {
        if (!hasText(json)) {
            return Map.of();
        }
        try {
            Map<String, BigDecimal> scores = objectMapper.readValue(json, SCORE_MAP);
            return scores == null ? Map.of() : scores;
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYS_ERROR, "评价维度分数解析失败", e);
        }
    }

    private JsonNode readJsonNode(String json) {
        if (!hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYS_ERROR, "AI 草稿解析失败", e);
        }
    }

    private List<AiDimensionSuggestionView> suggestions(JsonNode root) {
        JsonNode array = root.get("dimensionSuggestions");
        if (array == null || !array.isArray()) {
            array = root.get("suggestions");
        }
        if (array == null || !array.isArray()) {
            return List.of();
        }
        List<AiDimensionSuggestionView> values = new ArrayList<>(array.size());
        for (JsonNode node : array) {
            JsonNode scoreNode = node.get("suggestedScore");
            if (scoreNode == null) {
                scoreNode = node.get("score");
            }
            values.add(new AiDimensionSuggestionView(
                    text(node, "dimCode"),
                    text(node, "dimName"),
                    scoreNode == null || scoreNode.isNull() ? null : scoreNode.decimalValue(),
                    text(node, "reason")));
        }
        return values;
    }

    private static List<String> textList(JsonNode root, String field) {
        JsonNode array = root.get(field);
        if (array == null || !array.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>(array.size());
        for (JsonNode node : array) {
            if (node.isNull()) {
                continue;
            }
            values.add(node.isValueNode() ? node.asText() : text(node, "text"));
        }
        return values;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYS_ERROR, "评价维度分数序列化失败", e);
        }
    }

    private static List<Long> parseIds(String json) {
        if (!hasText(json)) {
            return List.of();
        }
        try {
            JsonNode node = new ObjectMapper().readTree(json);
            List<Long> ids = new ArrayList<>(node.size());
            for (JsonNode item : node) {
                ids.add(Long.valueOf(item.asText()));
            }
            return ids;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String interviewerName(Long interviewerId) {
        return interviewerId == null ? null : "面试官-" + interviewerId;
    }

    private static LocalDateTime utcStartOfDay(LocalDate date) {
        return LocalDateTime.ofInstant(ScheduleSlotWindow.startOfDay(date), ZoneOffset.UTC);
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

    /**
     * 模板 + 维度快照，避免同一请求内重复查库。
     */
    private record TemplateDims(JobStageTemplate template, List<EvaluationDimension> dimensions) {
    }
}
