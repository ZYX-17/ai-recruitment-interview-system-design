# B · 事件契约

> 上级索引：[成员B开发文档](../6.成员B开发文档-智能简历与人才管理子系统.md)  
> 对应原文档：§6

## 6. 事件契约

### 6.1 信封与路由

```json
{
  "eventId": "uuid",
  "eventType": "resume.parse.completed",
  "eventVersion": 1,
  "occurredAt": "2026-10-19T02:00:00Z",
  "traceId": "…",
  "aggregateId": "resumeId",
  "aggregateType": "RESUME",
  "payload": {}
}
```

**路由约定（与 A 文档 §6.1 统一）**：

| 项 | 规则 |
|---|---|
| exchange | `recruit.events`（topic，持久化） |
| routingKey | 与 `eventType` 一致，如 `resume.received` |
| queue | `{service}.{event-type}` |
| DLQ | `{queue}.dlq`，绑到 `recruit.dlx` |

B 已声明的队列：

| 队列 | routingKey | 用途 |
|---|---|---|
| `mineru.resume-received` | `resume.received` | mineru-worker 消费解析任务 |
| `resume.parse-completed` | `resume.parse.completed` | resume-service 分块/Embedding/ES 索引 |
| `resume.job-published` | `job.published` | resume-service 更新 `job_snapshot` |
| `resume.job-updated` | `job.updated` | resume-service 更新 `job_snapshot` |

> Outbox 发布器每 5s 扫描 PENDING/FAILED；publisher confirm ack 后置 SENT，失败按 1/2/4/8/16 分钟退避，5 次转 DEAD。消费端先 `INSERT IGNORE` 幂等占位，再处理业务。

### 6.2 B 发布的事件

| 事件 | 载荷 | 消费方 |
|---|---|---|
| `resume.received` | resumeId, personId, taskNo, fileId, fileKey, channel | resume 解析编排 / mineru-worker |
| `resume.parse.completed` | resumeId, personId, taskNo, versionNo, chunkCount | resume 向量化/ES、ai 实体抽取 |
| `resume.parse.failed` | resumeId, taskNo, reason, attempt | notify（HR 可见） |
| `candidate.duplicated` | personId, duplicatePersonIds, similarity, dedupType | notify（HR 查重提醒） |
| `match.completed` | jobId, resumeId, personId, overallScore, model, promptVersion | 通知/前端刷新（已在总文档 §5.2 登记） |
| `talent.followup.due` | talentId, personId, hrId, nextFollowupAt | notify（回访提醒） |
| `ai.task.completed` | taskNo, bizType, bizId, status, model | resume-service（扩展，统一点分命名） |
| `ai.task.failed` | taskNo, bizType, bizId, reason | resume-service（标记失败可重试） |
| `notification.requested` | templateCode, receiverId, bizType, bizId, variables | notify |

### 6.3 B 消费的事件

| 事件 | 发布方 | B 的处理 |
|---|---|---|
| `job.published` / `job.updated` | interview | 更新 `job_snapshot`；关闭岗位停止推荐 |
| `room.finished` | room | 纪要/评价草稿触发（转写就绪） |
| `recording.completed` | room | 录像登记/可选补转写 |
| `interview.result.submitted` | interview | 人才画像/面试历史更新 |
| `offer.sent` / `offer.confirmed` / `offer.declined` / `offer.expired` | interview | 人才库状态更新（发送/确认/拒绝/过期） |
| `application.stage.changed` | interview | 人才画像/面试历史更新（可选） |

### 6.4 一致性

- 业务表与 `local_message` 同事务写入；发布器每 5s 扫描 PENDING；publisher confirms；失败退避 1/2/4/8/16 分钟，5 次 DEAD。
- 消费端先插 `idempotent_consumption(consumer_group, event_id)`，重复即跳过；手动 ack；失败进死信。
- `MQ_ENABLED=false` 时只落 Outbox，不投递；主流程不受影响。
- `resume.received` 由 resume-service 发布、mineru-worker 消费；MinerU 完成后 resume-service 发 `resume.parse.completed`，随后做向量化/ES 与 AI 实体抽取。
- 事件命名以本表为准；骨架 `resume.parsed`、`ai_task.completed` 改名并保留兼容常量。

---

