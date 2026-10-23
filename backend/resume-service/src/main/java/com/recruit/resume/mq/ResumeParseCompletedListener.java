package com.recruit.resume.mq;

import com.recruit.resume.service.ResumeIndexingService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 解析完成事件消费者：触发分块、Embedding、ES 索引。
 */
@Component
@RequiredArgsConstructor
public class ResumeParseCompletedListener {

    private final ResumeIndexingService resumeIndexingService;

    @RabbitListener(queues = "${recruit.mq.resume-parse-completed-queue:resume.parse-completed}")
    public void onParseCompleted(String body) {
        resumeIndexingService.handle(body);
    }
}
