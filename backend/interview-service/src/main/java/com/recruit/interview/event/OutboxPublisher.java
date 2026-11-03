package com.recruit.interview.event;

import com.recruit.interview.entity.LocalMessage;
import com.recruit.interview.mapper.LocalMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Outbox 写入器：只负责把事件写进 {@code local_message}，由扫描器异步投递。
 *
 * <p>调用方必须处在业务事务中（{@code @Transactional}），保证“业务落库 + 事件落库”原子。</p>
 */
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final LocalMessageMapper localMessageMapper;
    private final DomainEventBuilder domainEventBuilder;

    /**
     * 发布领域事件到 Outbox。
     *
     * @param eventType     事件类型
     * @param aggregateId   聚合根 ID
     * @param aggregateType 聚合根类型
     * @param payload       业务负载
     */
    public void publish(String eventType, String aggregateId, String aggregateType, Object payload) {
        LocalMessage message = domainEventBuilder.build(eventType, aggregateId, aggregateType, payload);
        localMessageMapper.insert(message);
    }
}
