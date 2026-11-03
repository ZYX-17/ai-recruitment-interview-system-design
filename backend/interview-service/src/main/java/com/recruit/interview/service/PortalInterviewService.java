package com.recruit.interview.service;

import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.PageResult;
import com.recruit.interview.dto.PortalInterviewJoinView;
import com.recruit.interview.dto.PortalInterviewPageQuery;
import com.recruit.interview.dto.PortalInterviewView;
import com.recruit.interview.mapper.InterviewMapper;
import com.recruit.interview.mapper.row.InterviewRow;
import com.recruit.interview.support.PortalGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 门户“我的面试”与进房（I-10 补充端点）。
 *
 * <p>interview 只做归属与时间窗校验，返回房间标识和前端路由；
 * LiveKit 入会 token 仍由 room-service 签发（{@code POST /api/room/rooms/{roomNo}/token}）。</p>
 */
@Service
public class PortalInterviewService {

    private static final String STATUS_SCHEDULED = "SCHEDULED";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    private final InterviewMapper interviewMapper;
    private final int joinEarlyMinutes;
    private final int joinGraceMinutes;
    private final String joinBaseUrl;

    /**
     * @param interviewMapper  面试 Mapper
     * @param joinEarlyMinutes 允许提前进房分钟数
     * @param joinGraceMinutes 结束后仍可进房的宽限分钟数
     * @param joinBaseUrl      房间页前缀，默认 {@code http://localhost:5173/room}
     */
    public PortalInterviewService(
            InterviewMapper interviewMapper,
            @Value("${recruit.interview.join-early-minutes:10}") int joinEarlyMinutes,
            @Value("${recruit.interview.join-grace-minutes:15}") int joinGraceMinutes,
            @Value("${recruit.interview.join-base-url:http://localhost:5173/room}") String joinBaseUrl) {
        this.interviewMapper = interviewMapper;
        this.joinEarlyMinutes = Math.max(0, joinEarlyMinutes);
        this.joinGraceMinutes = Math.max(0, joinGraceMinutes);
        String base = joinBaseUrl == null || joinBaseUrl.isBlank()
                ? "http://localhost:5173/room" : joinBaseUrl.trim();
        this.joinBaseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /**
     * 我的面试分页。
     *
     * @param query 分页与状态
     * @return 分页面试
     */
    public PageResult<PortalInterviewView> page(PortalInterviewPageQuery query) {
        long personId = PortalGuard.requireCandidateId();
        String status = normalizeStatus(query.getStatus());
        long total = interviewMapper.countPortalPage(personId, status);
        if (total == 0) {
            return PageResult.of(List.of(), 0, query.getPageNum(), query.getPageSize());
        }
        long offset = (long) (query.getPageNum() - 1) * query.getPageSize();
        List<InterviewRow> rows = interviewMapper.selectPortalPage(personId, status,
                query.getPageSize(), offset);
        List<PortalInterviewView> views = new ArrayList<>(rows.size());
        for (InterviewRow row : rows) {
            views.add(toView(row));
        }
        return PageResult.of(views, total, query.getPageNum(), query.getPageSize());
    }

    /**
     * 面试详情。
     *
     * @param id 面试 ID
     * @return 面试详情
     */
    public PortalInterviewView detail(long id) {
        return toView(load(id));
    }

    /**
     * 进房校验。
     *
     * @param id 面试 ID
     * @return 房间标识与房间页地址
     */
    public PortalInterviewJoinView join(long id) {
        InterviewRow row = load(id);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (row.getRoomId() == null) {
            String reason = row.getRoomFailReason() == null || row.getRoomFailReason().isBlank()
                    ? "房间尚未创建，请稍后重试"
                    : "房间创建失败，请联系招聘方";
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, reason);
        }
        if (!STATUS_SCHEDULED.equals(row.getStatus()) && !STATUS_IN_PROGRESS.equals(row.getStatus())) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "当前面试状态不可进房");
        }
        if (!withinWindow(row, now)) {
            if (row.getStartTime() != null && now.isBefore(row.getStartTime().minusMinutes(joinEarlyMinutes))) {
                throw new BizException(ErrorCode.ROOM_NOT_OPEN, "未到进场时间，请提前 "
                        + joinEarlyMinutes + " 分钟进入");
            }
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "面试已结束，不能再进房");
        }
        return new PortalInterviewJoinView(
                String.valueOf(row.getId()),
                String.valueOf(row.getRoomId()),
                joinBaseUrl + "/" + row.getRoomId(),
                row.getStatus(),
                toInstant(row.getStartTime()),
                toInstant(row.getEndTime()));
    }

    private InterviewRow load(long id) {
        long personId = PortalGuard.requireCandidateId();
        InterviewRow row = interviewMapper.selectPortalRowById(id, personId);
        if (row == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "面试不存在");
        }
        return row;
    }

    private PortalInterviewView toView(InterviewRow row) {
        return new PortalInterviewView(
                String.valueOf(row.getId()),
                String.valueOf(row.getJobId()),
                row.getJobTitle(),
                row.getRoundNo(),
                toInstant(row.getStartTime()),
                toInstant(row.getEndTime()),
                row.getMode(),
                row.getRoomType(),
                row.getRoomId() == null ? null : String.valueOf(row.getRoomId()),
                row.getStatus(),
                joinable(row, LocalDateTime.now(ZoneOffset.UTC)));
    }

    private boolean joinable(InterviewRow row, LocalDateTime now) {
        return row.getRoomId() != null
                && (STATUS_SCHEDULED.equals(row.getStatus()) || STATUS_IN_PROGRESS.equals(row.getStatus()))
                && withinWindow(row, now);
    }

    private boolean withinWindow(InterviewRow row, LocalDateTime now) {
        if (row.getStartTime() == null) {
            return false;
        }
        LocalDateTime earliest = row.getStartTime().minusMinutes(joinEarlyMinutes);
        LocalDateTime latest = row.getEndTime() == null
                ? row.getStartTime().plusHours(2) : row.getEndTime().plusMinutes(joinGraceMinutes);
        return !now.isBefore(earliest) && !now.isAfter(latest);
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
