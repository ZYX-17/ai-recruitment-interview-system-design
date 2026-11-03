package com.recruit.interview.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.contracts.event.EventTypes;
import com.recruit.contracts.event.payload.NotificationRequestedPayload;
import com.recruit.interview.event.OutboxPublisher;
import com.recruit.interview.mapper.row.InterviewRow;
import com.recruit.interview.support.ScheduleSlotWindow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 面试邀约/改期/取消通知（P10-6）。
 *
 * <p>通知事件与业务同事务写 Outbox。邀约在 {@code room.created} 回填房间后才发，
 * 保证 {@code joinUrl} 指向真实房间；改期同理只在已有房间时发，避免给候选人
 * 一条无法入会的链接。候选人用 EMAIL+INAPP，面试官用 INAPP；
 * 候选人邮箱注入待简历域/notify 补齐（当前 receiverEmail 为空）。</p>
 */
@Slf4j
@Service
public class InterviewNotificationService {

    static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private static final List<String> CANDIDATE_CHANNELS = List.of("EMAIL", "INAPP");
    private static final List<String> INTERVIEWER_CHANNELS = List.of("INAPP");

    public static final String BIZ_INVITE = "INTERVIEW_INVITE";
    public static final String BIZ_RESCHEDULE = "INTERVIEW_RESCHEDULE";
    public static final String BIZ_CANCELLED = "INTERVIEW_CANCELLED";

    private final OutboxPublisher outboxPublisher;
    private final ObjectMapper objectMapper;
    private final String joinBaseUrl;

    /**
     * @param outboxPublisher Outbox 写入器
     * @param objectMapper    JSON 解析
     * @param joinBaseUrl     房间页前缀，默认 {@code http://localhost:5173/room}
     */
    public InterviewNotificationService(OutboxPublisher outboxPublisher, ObjectMapper objectMapper,
                                        @Value("${recruit.interview.join-base-url:http://localhost:5173/room}")
                                        String joinBaseUrl) {
        this.outboxPublisher = outboxPublisher;
        this.objectMapper = objectMapper;
        String base = joinBaseUrl == null || joinBaseUrl.isBlank()
                ? "http://localhost:5173/room" : joinBaseUrl.trim();
        this.joinBaseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /**
     * 发面试邀约（房间回填后调用）。
     *
     * @param row 面试行（含 roomId/候选人/面试官/时间/岗位）
     */
    public void publishInvite(InterviewRow row) {
        if (row == null || row.getRoomId() == null) {
            log.warn("房间未回填，跳过邀约通知: interviewId={}",
                    row == null ? null : row.getId());
            return;
        }
        Map<String, Object> variables = baseVariables(row);
        variables.put("joinUrl", joinUrl(row.getRoomId()));
        publishCandidate(row, "interview_invite", BIZ_INVITE, variables,
                "interview_invite:" + row.getId() + ":" + row.getPersonId());
        publishInterviewers(row, "interview_invite", BIZ_INVITE, variables,
                "interview_invite:" + row.getId());
    }

    /**
     * 发改期通知（仅已有房间时发送）。
     *
     * @param row      面试行
     * @param oldStart 原开始时间（UTC）
     * @param newStart 新开始时间（UTC）
     */
    public void publishReschedule(InterviewRow row, LocalDateTime oldStart, LocalDateTime newStart) {
        if (row == null || row.getRoomId() == null || newStart == null) {
            log.warn("房间未回填或新时间为空，跳过改期通知: interviewId={}",
                    row == null ? null : row.getId());
            return;
        }
        Map<String, Object> variables = baseVariables(row);
        variables.put("oldStartTime", format(oldStart));
        variables.put("newStartTime", format(newStart));
        variables.put("joinUrl", joinUrl(row.getRoomId()));
        String suffix = String.valueOf(newStart.toInstant(ZoneOffset.UTC).getEpochSecond());
        publishCandidate(row, "interview_reschedule", BIZ_RESCHEDULE, variables,
                "interview_reschedule:" + row.getId() + ":" + row.getPersonId() + ":" + suffix);
        publishInterviewers(row, "interview_reschedule", BIZ_RESCHEDULE, variables,
                "interview_reschedule:" + row.getId() + ":" + suffix);
    }

    /**
     * 发取消通知。
     *
     * @param row    面试行
     * @param reason 取消原因，空则用默认文案
     */
    public void publishCancelled(InterviewRow row, String reason) {
        if (row == null) {
            return;
        }
        Map<String, Object> variables = baseVariables(row);
        variables.put("reason", reason == null || reason.isBlank() ? "组织安排调整" : reason.trim());
        publishCandidate(row, "interview_cancelled", BIZ_CANCELLED, variables,
                "interview_cancelled:" + row.getId() + ":" + row.getPersonId());
        publishInterviewers(row, "interview_cancelled", BIZ_CANCELLED, variables,
                "interview_cancelled:" + row.getId());
    }

    private void publishCandidate(InterviewRow row, String templateCode, String bizType,
                                  Map<String, Object> variables, String dedupKey) {
        if (row.getPersonId() == null) {
            return;
        }
        publish(templateCode, String.valueOf(row.getPersonId()), CANDIDATE_CHANNELS,
                bizType, String.valueOf(row.getId()), variables, dedupKey);
    }

    private void publishInterviewers(InterviewRow row, String templateCode, String bizType,
                                     Map<String, Object> variables, String dedupPrefix) {
        for (String interviewerId : readInterviewerIds(row.getInterviewerIds())) {
            publish(templateCode, interviewerId, INTERVIEWER_CHANNELS, bizType,
                    String.valueOf(row.getId()), variables, dedupPrefix + ":" + interviewerId);
        }
    }

    private void publish(String templateCode, String receiverId, List<String> channels,
                         String bizType, String bizId, Map<String, Object> variables,
                         String dedupKey) {
        outboxPublisher.publish(EventTypes.NOTIFICATION_REQUESTED, bizId, "interview",
                new NotificationRequestedPayload(templateCode, receiverId, null, channels,
                        bizType, bizId, variables, dedupKey));
    }

    private Map<String, Object> baseVariables(InterviewRow row) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("candidateName", text(row.getPersonName(), "-"));
        variables.put("jobTitle", text(row.getJobTitle(), "-"));
        variables.put("startTime", format(row.getStartTime()));
        variables.put("mode", "ONLINE".equals(row.getMode()) ? "线上" : text(row.getMode(), "线上面试"));
        return variables;
    }

    private List<String> readInterviewerIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> ids = objectMapper.readValue(json, STRING_LIST);
            return ids == null ? List.of() : List.copyOf(ids);
        } catch (Exception e) {
            log.warn("面试官列表解析失败，跳过面试官通知: {}", e.getMessage());
            return List.of();
        }
    }

    private String joinUrl(Long roomId) {
        return joinBaseUrl + "/" + roomId;
    }

    private static String format(LocalDateTime value) {
        if (value == null) {
            return "-";
        }
        return value.atZone(ZoneOffset.UTC).withZoneSameInstant(ScheduleSlotWindow.BUSINESS_ZONE)
                .format(DISPLAY_TIME);
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
