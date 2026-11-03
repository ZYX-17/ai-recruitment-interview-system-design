package com.recruit.interview.service;

import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.contracts.event.payload.RecordingCompletedPayload;
import com.recruit.contracts.event.payload.RoomCreateFailedPayload;
import com.recruit.contracts.event.payload.RoomCreatedPayload;
import com.recruit.contracts.event.payload.RoomFinishedPayload;
import com.recruit.interview.entity.IdempotentConsumption;
import com.recruit.interview.entity.InterviewRecording;
import com.recruit.interview.mapper.IdempotentConsumptionMapper;
import com.recruit.interview.mapper.InterviewMapper;
import com.recruit.interview.mapper.InterviewRecordingMapper;
import com.recruit.interview.support.IdSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * room 事件消费（P10-2/P10-3/P10-5）。
 *
 * <p>{@code idempotent_consumption(consumer_group,event_id)} 唯一索引判重；
 * 业务更新本身也是状态 CAS，晚到/重复事件不会把终态拉回。
 * CANCELLED/NO_SHOW 不会被 room.finished 改成 COMPLETED。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomEventService {

    public static final String CONSUMER_GROUP = "interview.room-event";

    private final InterviewMapper interviewMapper;
    private final InterviewRecordingMapper recordingMapper;
    private final IdempotentConsumptionMapper consumptionMapper;

    /**
     * 处理 {@code room.created}：回填 room_id 并置 SCHEDULED。
     *
     * @param eventId 事件 ID（幂等）
     * @param payload 负载
     */
    @Transactional
    public void handleRoomCreated(String eventId, RoomCreatedPayload payload) {
        if (payload == null || consumed(eventId)) {
            return;
        }
        long interviewId = IdSupport.requireLong(payload.interviewId(), "interviewId");
        long roomId = IdSupport.requireLong(payload.roomId(), "roomId");
        if (interviewMapper.markRoomCreated(interviewId, roomId) == 0) {
            log.info("room.created 幂等或状态不允许，忽略: interviewId={}, roomId={}",
                    interviewId, roomId);
            return;
        }
        log.info("room.created 已回填: interviewId={}, roomId={}", interviewId, roomId);
    }

    /**
     * 处理 {@code room.create.failed}：保持 ROOM_PENDING，记录失败原因。
     *
     * @param eventId 事件 ID（幂等）
     * @param payload 负载
     */
    @Transactional
    public void handleRoomFailed(String eventId, RoomCreateFailedPayload payload) {
        if (payload == null || consumed(eventId)) {
            return;
        }
        long interviewId = IdSupport.requireLong(payload.interviewId(), "interviewId");
        String reason = payload.reason() == null || payload.reason().isBlank()
                ? "房间创建失败" : payload.reason().trim();
        if (interviewMapper.markRoomFailed(interviewId, reason) == 0) {
            log.info("room.create.failed 幂等或状态不允许，忽略: interviewId={}", interviewId);
            return;
        }
        log.warn("房间创建失败，待人工重试: interviewId={}, reason={}", interviewId, reason);
    }

    /**
     * 处理 {@code room.finished}：面试置 COMPLETED。
     *
     * @param eventId 事件 ID（幂等）
     * @param payload 负载
     */
    @Transactional
    public void handleRoomFinished(String eventId, RoomFinishedPayload payload) {
        if (payload == null || consumed(eventId)) {
            return;
        }
        long interviewId = IdSupport.requireLong(payload.interviewId(), "interviewId");
        if (interviewMapper.markCompleted(interviewId) == 0) {
            log.info("room.finished 幂等或终态不可回退，忽略: interviewId={}", interviewId);
            return;
        }
        log.info("room.finished 已置 COMPLETED: interviewId={}, roomId={}",
                interviewId, payload.roomId());
    }

    /**
     * 处理 {@code recording.completed}：登记录像资产。
     *
     * @param eventId 事件 ID（幂等）
     * @param payload 负载
     */
    @Transactional
    public void handleRecordingCompleted(String eventId, RecordingCompletedPayload payload) {
        if (payload == null || consumed(eventId)) {
            return;
        }
        long interviewId = IdSupport.requireLong(payload.interviewId(), "interviewId");
        String recordId = payload.recordId() == null ? "" : payload.recordId().trim();
        String fileKey = payload.fileKey() == null ? "" : payload.fileKey().trim();
        if (recordId.isEmpty() || fileKey.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "录像 recordId/fileKey 不能为空");
        }
        InterviewRecording recording = new InterviewRecording();
        recording.setInterviewId(interviewId);
        recording.setRoomId(IdSupport.optionalLong(payload.roomId(), "roomId"));
        recording.setRecordId(recordId);
        recording.setFileKey(fileKey);
        recording.setDurationSec(payload.durationSec());
        try {
            recordingMapper.insert(recording);
        } catch (DuplicateKeyException e) {
            log.info("录像已登记，忽略重复事件: recordId={}", recordId);
            return;
        }
        log.info("录像已登记: interviewId={}, recordId={}", interviewId, recordId);
    }

    private boolean consumed(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        IdempotentConsumption row = new IdempotentConsumption();
        row.setConsumerGroup(CONSUMER_GROUP);
        row.setEventId(eventId);
        try {
            consumptionMapper.insert(row);
            return false;
        } catch (DuplicateKeyException e) {
            log.debug("事件已消费，跳过: group={}, eventId={}", CONSUMER_GROUP, eventId);
            return true;
        }
    }
}
