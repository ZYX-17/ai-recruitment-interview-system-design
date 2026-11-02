package com.recruit.contracts.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 事件版本登记完整性测试。
 *
 * <p>这里用反射遍历 {@link EventTypes} 的所有 public static final String 常量，
 * 而不是手写一份事件清单。原因是：新增事件时最容易犯的错就是“常量加了、
 * 版本表忘了加”，手写清单也会跟着漏。反射能保证只要常量存在就必须登记版本。</p>
 */
class EventSchemaVersionTest {

    @Test
    @DisplayName("EventTypes 中每个事件常量都已登记版本")
    void everyEventTypeIsRegistered() throws Exception {
        List<String> missing = new ArrayList<>();
        for (Field field : EventTypes.class.getDeclaredFields()) {
            int mod = field.getModifiers();
            if (!Modifier.isPublic(mod) || !Modifier.isStatic(mod)
                    || !Modifier.isFinal(mod) || field.getType() != String.class) {
                continue;
            }
            String eventType = (String) field.get(null);
            try {
                assertTrue(EventSchemaVersion.current(eventType) >= EventSchemaVersion.V1,
                        "版本号必须 >= V1: " + eventType);
            } catch (IllegalArgumentException e) {
                missing.add(field.getName() + "=" + eventType);
            }
        }
        assertTrue(missing.isEmpty(), "以下事件常量未在 EventSchemaVersion 登记: " + missing);
    }

    @Test
    @DisplayName("所有已登记事件都必须能通过路由格式校验")
    void everyEventTypeMatchesRoutingPattern() throws Exception {
        for (Field field : EventTypes.class.getDeclaredFields()) {
            int mod = field.getModifiers();
            if (!Modifier.isPublic(mod) || !Modifier.isStatic(mod)
                    || !Modifier.isFinal(mod) || field.getType() != String.class) {
                continue;
            }
            String eventType = (String) field.get(null);
            assertEquals(eventType, EventRouting.routingKey(eventType),
                    "事件类型不符合 routingKey 规范: " + field.getName());
            // 队列名形如 room.job-published：第一个点是“服务名分隔符”，
            // 之后不允许再出现点，否则说明事件类型的点没有被替换成短横线。
            String queueName = EventRouting.queue("room", eventType);
            assertEquals("room." + eventType.replace('.', '-'), queueName,
                    "队列名转换规则不符合契约: " + eventType);
            assertFalse(queueName.substring(queueName.indexOf('.') + 1).contains("."),
                    "队列名中不应残留事件类型的点: " + eventType);
        }
    }

    @Test
    @DisplayName("未登记事件查询版本直接抛错")
    void unknownEventTypeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> EventSchemaVersion.current("unknown.event"));
        assertThrows(IllegalArgumentException.class,
                () -> EventSchemaVersion.current(null));
    }
}
