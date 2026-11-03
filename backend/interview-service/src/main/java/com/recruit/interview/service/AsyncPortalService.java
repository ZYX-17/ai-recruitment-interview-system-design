package com.recruit.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.exception.BizException;
import com.recruit.common.idempotency.IdempotencyService;
import com.recruit.common.result.ErrorCode;
import com.recruit.interview.dto.AsyncQuestionView;
import com.recruit.interview.dto.PortalAsyncAnswerForm;
import com.recruit.interview.dto.PortalAsyncSubmitForm;
import com.recruit.interview.dto.PortalAsyncView;
import com.recruit.interview.entity.AsyncAnswer;
import com.recruit.interview.entity.AsyncQuestion;
import com.recruit.interview.mapper.AsyncAnswerMapper;
import com.recruit.interview.mapper.AsyncPaperMapper;
import com.recruit.interview.mapper.AsyncQuestionMapper;
import com.recruit.interview.mapper.row.AsyncPaperRow;
import com.recruit.interview.support.IdSupport;
import com.recruit.interview.support.SecretToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 候选人门户异步面试（P9-3）。
 *
 * <p>免登录，凭邀请 token 访问；token 只存 {@code sha256}。开始/提交都用
 * {@code status + version} CAS 保证并发下只有一次生效；已提交再次提交返回
 * {@code AIS5102}，不重复写作答。</p>
 *
 * <p>视频/音频只接收 file-service 文件 ID（先预签名上传），本服务不处理二进制。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncPortalService {

    private static final String SCOPE_START = "portal.async.start";
    private static final String SCOPE_SUBMIT = "portal.async.submit";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AsyncPaperMapper paperMapper;
    private final AsyncQuestionMapper questionMapper;
    private final AsyncAnswerMapper answerMapper;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    /**
     * 按邀请 token 查看试卷/进度。
     *
     * @param token 明文 token
     * @return 门户视图
     */
    public PortalAsyncView detail(String token) {
        AsyncPaperRow row = loadByToken(token);
        return toView(row);
    }

    /**
     * 开始作答：RELEASED → IN_PROGRESS；已开始则幂等返回。
     *
     * @param token 明文 token
     * @param key   Idempotency-Key，可为空
     * @return 门户视图
     */
    @Transactional
    public PortalAsyncView start(String token, String key) {
        if (hasText(key)) {
            return idempotencyService.execute(SCOPE_START, key, PortalAsyncView.class,
                    () -> doStart(token));
        }
        return doStart(token);
    }

    private PortalAsyncView doStart(String token) {
        AsyncPaperRow row = loadByToken(token);
        if (AsyncInterviewService.STATUS_IN_PROGRESS.equals(row.getStatus())) {
            return toView(row);
        }
        if (AsyncInterviewService.STATUS_SUBMITTED.equals(row.getStatus())
                || AsyncInterviewService.STATUS_SCORED.equals(row.getStatus())) {
            throw new BizException(ErrorCode.ANSWER_SUBMITTED);
        }
        if (!AsyncInterviewService.STATUS_RELEASED.equals(row.getStatus())) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "试卷尚未发布");
        }
        if (paperMapper.markStarted(row.getId(), nowUtc(), row.getVersion()) == 0) {
            AsyncPaperRow latest = paperMapper.selectRowById(row.getId());
            if (latest != null && AsyncInterviewService.STATUS_IN_PROGRESS.equals(latest.getStatus())) {
                return toView(latest);
            }
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "试卷状态已变化，请刷新后重试");
        }
        log.info("async interview started paperId={} candidateId={}", row.getId(), row.getPersonId());
        return toView(paperMapper.selectRowById(row.getId()));
    }

    /**
     * 提交作答：IN_PROGRESS → SUBMITTED；重复提交返回 {@code AIS5102}。
     *
     * @param token 明文 token
     * @param form  作答列表
     * @param key   Idempotency-Key，可为空
     * @return 提交后的门户视图
     */
    @Transactional
    public PortalAsyncView submit(String token, PortalAsyncSubmitForm form, String key) {
        if (hasText(key)) {
            return idempotencyService.execute(SCOPE_SUBMIT, key, PortalAsyncView.class,
                    () -> doSubmit(token, form));
        }
        return doSubmit(token, form);
    }

    private PortalAsyncView doSubmit(String token, PortalAsyncSubmitForm form) {
        AsyncPaperRow paper = loadByToken(token);
        if (AsyncInterviewService.STATUS_SUBMITTED.equals(paper.getStatus())
                || AsyncInterviewService.STATUS_SCORED.equals(paper.getStatus())) {
            throw new BizException(ErrorCode.ANSWER_SUBMITTED);
        }
        if (!AsyncInterviewService.STATUS_IN_PROGRESS.equals(paper.getStatus())) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "请先开始作答再提交");
        }

        List<AsyncQuestion> questions = questionMapper.selectByPaperId(paper.getId());
        Map<Long, AsyncQuestion> questionById = new HashMap<>();
        for (AsyncQuestion question : questions) {
            questionById.put(question.getId(), question);
        }
        if (form.getAnswers().size() != questions.size()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请完成全部题目后再提交");
        }

        Set<Long> seen = new HashSet<>();
        LocalDateTime now = nowUtc();
        for (PortalAsyncAnswerForm answerForm : form.getAnswers()) {
            long questionId = IdSupport.requireLong(answerForm.getQuestionId(), "questionId");
            AsyncQuestion question = questionById.get(questionId);
            if (question == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "题目不属于本试卷");
            }
            if (!seen.add(questionId)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "同一题目只能提交一次");
            }
            Long videoFileId = IdSupport.optionalLong(answerForm.getVideoFileId(), "videoFileId");
            Long audioFileId = IdSupport.optionalLong(answerForm.getAudioFileId(), "audioFileId");
            if (videoFileId == null && audioFileId == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "每道题至少上传视频或音频");
            }
            if (answerForm.getAnswerSec() != null
                    && answerForm.getAnswerSec() > question.getAnswerSec()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "作答时长超过题目上限");
            }
            AsyncAnswer answer = new AsyncAnswer();
            answer.setQuestionId(questionId);
            answer.setCandidateId(paper.getPersonId());
            answer.setVideoFileId(videoFileId);
            answer.setAudioFileId(audioFileId);
            answer.setAnswerSec(answerForm.getAnswerSec());
            answer.setStatus(AsyncInterviewService.ANSWER_SUBMITTED);
            answer.setSubmittedAt(now);
            answer.setVersion(0);
            try {
                answerMapper.insert(answer);
            } catch (DuplicateKeyException e) {
                throw new BizException(ErrorCode.ANSWER_SUBMITTED);
            }
        }

        if (paperMapper.markSubmitted(paper.getId(), now, paper.getVersion()) == 0) {
            throw new BizException(ErrorCode.ANSWER_SUBMITTED);
        }
        log.info("async interview submitted paperId={} candidateId={} answers={}",
                paper.getId(), paper.getPersonId(), form.getAnswers().size());
        return toView(paperMapper.selectRowById(paper.getId()));
    }

    private AsyncPaperRow loadByToken(String token) {
        if (!hasText(token)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "token 不能为空");
        }
        AsyncPaperRow row = paperMapper.selectRowByTokenHash(SecretToken.sha256Hex(token.trim()));
        if (row == null) {
            throw new BizException(ErrorCode.APP_NOT_FOUND, "链接无效或已过期");
        }
        if (row.getInviteExpireAt() != null && row.getInviteExpireAt().isBefore(nowUtc())) {
            throw new BizException(ErrorCode.APP_NOT_FOUND, "链接无效或已过期");
        }
        return row;
    }

    private PortalAsyncView toView(AsyncPaperRow row) {
        List<AsyncQuestionView> questions = List.of();
        if (AsyncInterviewService.STATUS_RELEASED.equals(row.getStatus())
                || AsyncInterviewService.STATUS_IN_PROGRESS.equals(row.getStatus())) {
            questions = questionViews(row.getId());
        }
        return new PortalAsyncView(
                row.getPaperNo(),
                row.getJobTitle(),
                row.getPersonName(),
                row.getStatus(),
                row.getQuestionCount(),
                row.getDurationLimitSec(),
                readJson(row.getSettingsJson()),
                toInstant(row.getInviteExpireAt()),
                toInstant(row.getStartedAt()),
                toInstant(row.getSubmittedAt()),
                questions);
    }

    private List<AsyncQuestionView> questionViews(long paperId) {
        List<AsyncQuestion> rows = questionMapper.selectByPaperId(paperId);
        List<AsyncQuestionView> views = new ArrayList<>(rows.size());
        for (AsyncQuestion row : rows) {
            views.add(new AsyncQuestionView(IdSupport.asString(row.getId()), row.getSeqNo(),
                    row.getQuestionText(), row.getPrepareSec(), row.getAnswerSec(),
                    row.getWeight()));
        }
        return views;
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

    private static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
