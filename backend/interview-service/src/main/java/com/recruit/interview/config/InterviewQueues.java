package com.recruit.interview.config;

import com.recruit.contracts.event.EventRouting;
import com.recruit.contracts.event.EventTypes;
import org.springframework.stereotype.Component;

/**
 * interview 消费队列名（全部由 {@link EventRouting#queue} 计算，不手写）。
 */
@Component
public class InterviewQueues {

    private final String roomCreated = EventRouting.queue("interview", EventTypes.ROOM_CREATED);
    private final String roomCreateFailed =
            EventRouting.queue("interview", EventTypes.ROOM_CREATE_FAILED);
    private final String roomFinished = EventRouting.queue("interview", EventTypes.ROOM_FINISHED);
    private final String recordingCompleted =
            EventRouting.queue("interview", EventTypes.RECORDING_COMPLETED);

    public String getRoomCreated() {
        return roomCreated;
    }

    public String getRoomCreateFailed() {
        return roomCreateFailed;
    }

    public String getRoomFinished() {
        return roomFinished;
    }

    public String getRecordingCompleted() {
        return recordingCompleted;
    }
}
