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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * room 事件消费（P10-2/P10-3/P10-5）。
 */
@ExtendWith(MockitoExtension.class)
class RoomEventServiceTest {

    @Mock
    private InterviewMapper interviewMapper;
    @Mock
    private InterviewRecordingMapper recordingMapper;
    @Mock
    private IdempotentConsumptionMapper consumptionMapper;

    private RoomEventService service;

    @BeforeEach
    void setUp() {
        service = new RoomEventService(interviewMapper, recordingMapper, consumptionMapper);
    }

    @Test
    void roomCreatedBackfillsRoomAndStatus() {
        when(consumptionMapper.insert(any(IdempotentConsumption.class))).thenReturn(1);
        when(interviewMapper.markRoomCreated(91001L, 70001L)).thenReturn(1);

        service.handleRoomCreated("evt-1",
                new RoomCreatedPayload("91001", "70001", "ONE_TO_ONE", Instant.now()));

        verify(interviewMapper).markRoomCreated(91001L, 70001L);
    }

    @Test
    void duplicateEventIsIgnored() {
        when(consumptionMapper.insert(any(IdempotentConsumption.class)))
                .thenThrow(new DuplicateKeyException("uk_group_event"));

        service.handleRoomCreated("evt-1",
                new RoomCreatedPayload("91001", "70001", "ONE_TO_ONE", Instant.now()));

        verify(interviewMapper, never()).markRoomCreated(anyLong(), anyLong());
    }

    @Test
    void roomCreatedOnTerminalStatusDoesNotRevert() {
        when(consumptionMapper.insert(any(IdempotentConsumption.class))).thenReturn(1);
        when(interviewMapper.markRoomCreated(91001L, 70001L)).thenReturn(0);

        service.handleRoomCreated("evt-2",
                new RoomCreatedPayload("91001", "70001", "ONE_TO_ONE", Instant.now()));

        verify(interviewMapper).markRoomCreated(91001L, 70001L);
    }

    @Test
    void roomFailedRecordsReason() {
        when(consumptionMapper.insert(any(IdempotentConsumption.class))).thenReturn(1);
        when(interviewMapper.markRoomFailed(91001L, "LiveKit 超时")).thenReturn(1);

        service.handleRoomFailed("evt-3",
                new RoomCreateFailedPayload("91001", "LiveKit 超时", Instant.now()));

        verify(interviewMapper).markRoomFailed(91001L, "LiveKit 超时");
    }

    @Test
    void roomFinishedMarksCompleted() {
        when(consumptionMapper.insert(any(IdempotentConsumption.class))).thenReturn(1);
        when(interviewMapper.markCompleted(91001L)).thenReturn(1);

        service.handleRoomFinished("evt-4",
                new RoomFinishedPayload("91001", "70001", Instant.now(), 1800));

        verify(interviewMapper).markCompleted(91001L);
    }

    @Test
    void recordingCompletedIsRegistered() {
        when(consumptionMapper.insert(any(IdempotentConsumption.class))).thenReturn(1);
        when(recordingMapper.insert(any(InterviewRecording.class))).thenReturn(1);

        service.handleRecordingCompleted("evt-5", new RecordingCompletedPayload(
                "91001", "70001", "rec-1", "recordings/rec-1.mp4", 1800, Instant.now()));

        ArgumentCaptor<InterviewRecording> captor = ArgumentCaptor.forClass(InterviewRecording.class);
        verify(recordingMapper).insert(captor.capture());
        assertThat(captor.getValue().getRecordId()).isEqualTo("rec-1");
        assertThat(captor.getValue().getRoomId()).isEqualTo(70001L);
    }

    @Test
    void duplicateRecordingIsIdempotent() {
        when(consumptionMapper.insert(any(IdempotentConsumption.class))).thenReturn(1);
        when(recordingMapper.insert(any(InterviewRecording.class)))
                .thenThrow(new DuplicateKeyException("uk_record"));

        service.handleRecordingCompleted("evt-6", new RecordingCompletedPayload(
                "91001", "70001", "rec-1", "recordings/rec-1.mp4", 1800, Instant.now()));
    }

    @Test
    void recordingWithoutFileKeyIsRejected() {
        when(consumptionMapper.insert(any(IdempotentConsumption.class))).thenReturn(1);

        assertThatThrownBy(() -> service.handleRecordingCompleted("evt-7",
                new RecordingCompletedPayload("91001", "70001", "rec-1", " ", 1800, Instant.now())))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARAM_INVALID);
        verify(recordingMapper, never()).insert(any(InterviewRecording.class));
    }
}
