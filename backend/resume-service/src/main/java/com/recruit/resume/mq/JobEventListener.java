package com.recruit.resume.mq;

import com.recruit.resume.service.JobSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 岗位事件消费者。
 *
 * <p>队列由 {@code RabbitTopologyConfig} 声明：resume.job-published / resume.job-updated，
 * 绑到 recruit.events，routingKey 分别为 job.published / job.updated。</p>
 *
 * <p>异常策略：解析/字段错误在 Service 内消化（记录日志后 return）；数据库异常向上抛，
 * 由 listener 容器 reject 到 DLQ（{queue}.dlq），避免无限重投。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobEventListener {

    private final JobSnapshotService jobSnapshotService;

    @RabbitListener(queues = {
            "${recruit.mq.job-published-queue:resume.job-published}",
            "${recruit.mq.job-updated-queue:resume.job-updated}"
    })
    public void onJobEvent(String body) {
        jobSnapshotService.handle(body);
    }
}
