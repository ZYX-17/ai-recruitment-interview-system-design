package com.recruit.contracts.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 事件路由契约测试。
 *
 * <p>路由是“生产者和消费者各自算一遍队列名”的典型场景：
 * 只要有一边把点写成短横线的规则记错，消息就会静默进不了队列，
 * 所以这里把文档 §6.1 的规则固化成测试，修改规则必须先改测试。</p>
 */
class EventRoutingTest {

    @Test
    @DisplayName("routingKey 与 eventType 完全一致")
    void routingKeyEqualsEventType() {
        assertEquals("interview.scheduled", EventRouting.routingKey("interview.scheduled"));
        assertEquals("resume.parse.completed", EventRouting.routingKey("resume.parse.completed"));
    }

    @Test
    @DisplayName("队列名把事件类型中的点替换成短横线")
    void queueNameUsesHyphen() {
        assertEquals("room.interview-scheduled", EventRouting.queue("room", "interview.scheduled"));
        assertEquals("notify.offer-declined", EventRouting.queue("notify", "offer.declined"));
        assertEquals("resume.resume-parse-completed", EventRouting.queue("resume", "resume.parse.completed"));
    }

    @Test
    @DisplayName("死信队列名幂等：重复加 .dlq 不会叠加")
    void dlqIsIdempotent() {
        assertEquals("room.interview-scheduled.dlq", EventRouting.dlq("room.interview-scheduled"));
        assertEquals("room.interview-scheduled.dlq", EventRouting.dlq("room.interview-scheduled.dlq"));
    }

    @Test
    @DisplayName("非法服务名直接失败，避免拼出错误队列")
    void rejectsIllegalServiceName() {
        assertThrows(IllegalArgumentException.class, () -> EventRouting.queue(null, "interview.scheduled"));
        assertThrows(IllegalArgumentException.class, () -> EventRouting.queue("Room", "interview.scheduled"));
        assertThrows(IllegalArgumentException.class, () -> EventRouting.queue("room_service", "interview.scheduled"));
        assertThrows(IllegalArgumentException.class, () -> EventRouting.queue("", "interview.scheduled"));
    }

    @Test
    @DisplayName("非法事件类型直接失败：必须小写、至少两段、点分隔")
    void rejectsIllegalEventType() {
        assertThrows(IllegalArgumentException.class, () -> EventRouting.routingKey(null));
        assertThrows(IllegalArgumentException.class, () -> EventRouting.routingKey("interview"));
        assertThrows(IllegalArgumentException.class, () -> EventRouting.routingKey("Interview.scheduled"));
        assertThrows(IllegalArgumentException.class, () -> EventRouting.routingKey("interview..scheduled"));
        assertThrows(IllegalArgumentException.class, () -> EventRouting.routingKey("interview scheduled"));
    }

    @Test
    @DisplayName("空队列名不能生成 DLQ")
    void rejectsBlankQueue() {
        assertThrows(IllegalArgumentException.class, () -> EventRouting.dlq(null));
        assertThrows(IllegalArgumentException.class, () -> EventRouting.dlq("  "));
    }
}
