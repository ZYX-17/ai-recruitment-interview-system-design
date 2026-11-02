package com.recruit.contracts.event;

import java.util.regex.Pattern;

/**
 * 事件路由契约（开发文档 §6.1）。
 *
 * <p>集中定义 exchange、队列名和死信规则，避免各服务手写字符串导致路由不上。
 * 规则：</p>
 * <ul>
 *   <li>exchange：{@code recruit.events}（topic）；</li>
 *   <li>routingKey：与 eventType 完全一致，例如 {@code interview.scheduled}；</li>
 *   <li>queue：{@code {service}.{eventType}}，其中 eventType 的点替换为短横线，
 *       例如 {@code room.interview-scheduled}；</li>
 *   <li>DLQ：{@code {queue}.dlq}；</li>
 *   <li>延迟消息：{@code recruit.delay} + {@code x-delay} 头（毫秒）。</li>
 * </ul>
 */
public final class EventRouting {

    /** 业务事件 topic exchange。 */
    public static final String EXCHANGE = "recruit.events";

    /** 延迟消息 exchange（RabbitMQ delayed-message 插件）。 */
    public static final String DELAY_EXCHANGE = "recruit.delay";

    /** 死信队列后缀。 */
    public static final String DLQ_SUFFIX = ".dlq";

    /** 延迟毫秒数 header。 */
    public static final String DELAY_HEADER = "x-delay";

    /** 事件类型：小写字母/数字/短横线，至少两段，点分隔。 */
    private static final Pattern EVENT_TYPE_PATTERN =
            Pattern.compile("^[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)+$");

    /** 服务名：小写字母/数字/短横线。 */
    private static final Pattern SERVICE_PATTERN =
            Pattern.compile("^[a-z][a-z0-9-]*$");

    private EventRouting() {
    }

    /**
     * 计算 routingKey。当前约定 routingKey 与 eventType 完全相同。
     *
     * @param eventType 事件类型，例如 interview.scheduled
     * @return routingKey
     * @throws IllegalArgumentException 事件类型格式非法时抛出
     */
    public static String routingKey(String eventType) {
        return requireEventType(eventType);
    }

    /**
     * 计算消费队列名：{@code {service}.{eventType 的点换成短横线}}。
     *
     * <p>示例：{@code queue("room", "interview.scheduled") -> "room.interview-scheduled"}。</p>
     *
     * @param service   服务名，例如 room / notify
     * @param eventType 事件类型
     * @return 队列名
     * @throws IllegalArgumentException 服务名或事件类型非法时抛出
     */
    public static String queue(String service, String eventType) {
        if (service == null || !SERVICE_PATTERN.matcher(service).matches()) {
            throw new IllegalArgumentException("服务名非法: " + service);
        }
        return service + "." + requireEventType(eventType).replace('.', '-');
    }

    /**
     * 计算死信队列名：{@code {queue}.dlq}。
     *
     * @param queue 正常队列名
     * @return 死信队列名
     * @throws IllegalArgumentException 队列名为空时抛出
     */
    public static String dlq(String queue) {
        if (queue == null || queue.isBlank()) {
            throw new IllegalArgumentException("队列名不能为空");
        }
        return queue.endsWith(DLQ_SUFFIX) ? queue : queue + DLQ_SUFFIX;
    }

    /**
     * 校验事件类型格式。
     *
     * @param eventType 事件类型
     * @return 原值
     */
    private static String requireEventType(String eventType) {
        if (eventType == null || !EVENT_TYPE_PATTERN.matcher(eventType).matches()) {
            throw new IllegalArgumentException("事件类型非法: " + eventType);
        }
        return eventType;
    }
}
