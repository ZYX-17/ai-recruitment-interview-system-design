package com.recruit.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.context.UserContext;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.interview.dto.MinutesReviewForm;
import com.recruit.interview.dto.MinutesView;
import com.recruit.interview.dto.MinutesWriteForm;
import com.recruit.interview.entity.Interview;
import com.recruit.interview.entity.InterviewMinutes;
import com.recruit.interview.mapper.InterviewMapper;
import com.recruit.interview.mapper.InterviewMinutesMapper;
import com.recruit.interview.mapper.row.InterviewRow;
import com.recruit.interview.support.IdSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

/**
 * 面试纪要（P10-4/P10-8、X-02/X-04/X-05）。
 *
 * <p>AI 只写草稿；人工确认后才参与决策。写回按 {@code taskId} 幂等，
 * 已确认/驳回的纪要不会被 AI 重跑覆盖。确认/驳回用 {@code version} CAS。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinutesService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_INSUFFICIENT = "INSUFFICIENT_TRANSCRIPT";

    private static final Set<String> OPEN_STATUSES = Set.of(STATUS_PENDING, STATUS_INSUFFICIENT);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final InterviewMapper interviewMapper;
    private final InterviewMinutesMapper minutesMapper;
    private final DataScopeService dataScopeService;
    private final ObjectMapper objectMapper;

    /**
     * AI 回写纪要/评价草稿（内部接口）。
     *
     * @param interviewId 面试 ID
     * @param form        回写入参
     * @return 纪要视图
     */
    @Transactional
    public MinutesView write(long interviewId, MinutesWriteForm form) {
        Interview interview = interviewMapper.selectById(interviewId);
        if (interview == null) {
            throw new BizException(ErrorCode.APP_NOT_FOUND, "面试不存在");
        }
        int segments = form.getTranscriptSegmentCount() == null ? 0 : form.getTranscriptSegmentCount();
        String reviewStatus = segments > 0 ? STATUS_PENDING : STATUS_INSUFFICIENT;

        InterviewMinutes existing = minutesMapper.selectByInterviewId(interviewId);
        if (existing == null) {
            InterviewMinutes minutes = new InterviewMinutes();
            minutes.setInterviewId(interviewId);
            minutes.setAiTaskId(form.getTaskId());
            minutes.setTranscriptObjectKey(trimToNull(form.getTranscriptObjectKey()));
            minutes.setTranscriptSegmentCount(segments);
            minutes.setMinutesMd(trimToNull(form.getMinutesMd()));
            minutes.setAiEvaluation(writeJson(form.getAiEvaluation()));
            minutes.setReviewStatus(reviewStatus);
            minutes.setModel(trimToNull(form.getModel()));
            minutes.setPromptVersion(form.getPromptVersion() == null ? 1 : form.getPromptVersion());
            minutes.setVersion(0);
            try {
                minutesMapper.insert(minutes);
                audit("write", minutes.getId(), interviewId, "status=" + reviewStatus);
                return toView(minutesMapper.selectByInterviewId(interviewId));
            } catch (DuplicateKeyException e) {
                existing = minutesMapper.selectByAiTaskId(form.getTaskId());
                if (existing != null) {
                    return toView(existing);
                }
                existing = minutesMapper.selectByInterviewId(interviewId);
                if (existing == null) {
                    throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "纪要已存在，请重试");
                }
            }
        }
        if (form.getTaskId().equals(existing.getAiTaskId())) {
            return toView(existing);
        }
        if (!OPEN_STATUSES.contains(existing.getReviewStatus())) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "纪要已确认/驳回，不能被 AI 覆盖");
        }
        if (minutesMapper.updateFromAi(existing.getId(), form.getTaskId(),
                trimToNull(form.getTranscriptObjectKey()), segments, trimToNull(form.getMinutesMd()),
                writeJson(form.getAiEvaluation()), reviewStatus, trimToNull(form.getModel()),
                form.getPromptVersion() == null ? 1 : form.getPromptVersion(),
                existing.getVersion()) == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT);
        }
        audit("write", existing.getId(), interviewId, "status=" + reviewStatus);
        return toView(minutesMapper.selectByInterviewId(interviewId));
    }

    /**
     * 纪要详情（HR/本场面试官可见）。
     *
     * @param interviewId 面试 ID
     * @return 纪要视图
     */
    public MinutesView detail(long interviewId) {
        loadVisibleInterview(interviewId);
        InterviewMinutes minutes = minutesMapper.selectByInterviewId(interviewId);
        if (minutes == null) {
            throw new BizException(ErrorCode.APP_NOT_FOUND, "纪要不存在");
        }
        return toView(minutes);
    }

    /**
     * 人工确认/驳回。
     *
     * @param id   纪要 ID
     * @param form 复核入参
     * @return 复核后的纪要
     */
    @Transactional
    public MinutesView review(long id, MinutesReviewForm form) {
        InterviewMinutes minutes = minutesMapper.selectById(id);
        if (minutes == null) {
            throw new BizException(ErrorCode.APP_NOT_FOUND, "纪要不存在");
        }
        loadVisibleInterview(minutes.getInterviewId());
        String target = "CONFIRM".equalsIgnoreCase(form.getDecision())
                ? STATUS_CONFIRMED : STATUS_REJECTED;
        if (target.equals(minutes.getReviewStatus())) {
            return toView(minutes);
        }
        if (!OPEN_STATUSES.contains(minutes.getReviewStatus())) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "纪要已处理，不能重复确认/驳回");
        }
        requireVersion(minutes.getVersion(), form.getVersion());
        long reviewerId = Long.parseLong(UserContext.requireUserId());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (minutesMapper.review(id, target, trimToNull(form.getComment()), reviewerId,
                now, form.getVersion()) == 0) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突，请刷新后重试");
        }
        audit("review", id, minutes.getInterviewId(), "status=" + target);
        return toView(minutesMapper.selectById(id));
    }

    private InterviewRow loadVisibleInterview(long interviewId) {
        InterviewRow row = interviewMapper.selectRowById(interviewId);
        if (row == null) {
            throw new BizException(ErrorCode.APP_NOT_FOUND, "面试不存在");
        }
        DataScopeService.DataScope scope = dataScopeService.current();
        if (scope.canSee(row.getDeptId(), row.getOwnerHrId()) || UserContext.hasRole("HR_ADMIN")) {
            return row;
        }
        String userId = UserContext.getUserId();
        if (userId != null && row.getInterviewerIds() != null
                && row.getInterviewerIds().contains("\"" + userId + "\"")) {
            return row;
        }
        throw new BizException(ErrorCode.AUTH_FORBIDDEN, "无权访问该面试纪要");
    }

    private MinutesView toView(InterviewMinutes minutes) {
        return new MinutesView(
                IdSupport.asString(minutes.getId()),
                IdSupport.asString(minutes.getInterviewId()),
                minutes.getAiTaskId(),
                minutes.getTranscriptObjectKey(),
                minutes.getTranscriptSegmentCount(),
                minutes.getMinutesMd(),
                readJson(minutes.getAiEvaluation()),
                minutes.getReviewStatus(),
                minutes.getReviewComment(),
                IdSupport.asString(minutes.getReviewerId()),
                toInstant(minutes.getReviewedAt()),
                minutes.getModel(),
                minutes.getPromptVersion(),
                minutes.getVersion());
    }

    private Map<String, Object> readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYS_ERROR, "纪要 AI 结果解析失败", e);
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYS_ERROR, "纪要 AI 结果序列化失败", e);
        }
    }

    private static void requireVersion(Integer current, int expected) {
        int value = current == null ? 0 : current;
        if (value != expected) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突，请刷新后重试");
        }
    }

    private static void audit(String action, Long minutesId, long interviewId, String detail) {
        log.info("interview minutes audit action={} minutesId={} interviewId={} detail={}",
                action, minutesId, interviewId, detail);
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
