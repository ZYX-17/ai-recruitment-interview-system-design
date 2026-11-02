# ADR-008：通知去重优先数据库，不强制引入 Redis

- 状态：已接受
- 对应裁决：`docs/4 §11 D30`
- 日期：2026-10-19 裁决 / 2026-11-02 定稿
- 决策人：成员 A + 评审组
- 关联：`docs/4 §3.4.4`、`§6.5`、`§5.9`、`sql/05_recruit_notify.sql`

## 背景

`notification.requested` 可能因 MQ 重投、Outbox 重放、人工重发而重复到达。
若靠 Redis SETNX 去重，等于给 notify 增加一个强依赖：Redis 故障时通知要么重复发送，要么全阻塞；
本项目的可靠性基线已经要求「消费幂等唯一键 `(consumer_group, event_id)`」，数据库本身就能承担。

## 选项

| 选项 | 说明 | 取舍 |
|---|---|---|
| A. Redis SETNX 先占位 | 快，TTL 自动清理 | 新增依赖；Redis 故障降级策略难定 |
| B. DB 唯一约束/查询判重 | 与消费幂等表一致 | 需要一次索引查询；但可审计、可回放 |
| C. 不判重，靠下游去重 | 省事 | 用户会收到重复邮件/站内信，不可接受 |

## 结论

采用 B：

1. 消费侧先写 `idempotent_consumption(consumer_group, event_id)`，唯一键冲突即视为已处理；`unique (consumer_group, event_id)` 是最终防线。
2. 业务侧再按业务键判重（同一 `notification` 的同一 `channel` 不重复建件）；具体唯一键在 P8 迁移时与 `notification`/`notification_send_log` 字段一起定稿。
3. 人工重发（X-08）走独立接口，显式允许新增一次发送日志，不受上述判重影响，但必须写审计。
4. notify 不新增 Redis 依赖；若后续引入，只能作为一级缓存，DB 约束必须保留。

## 影响

- 依赖矩阵：notify 只需 MySQL + RabbitMQ + SMTP，不要求 Redis。
- 消费（P8-2）：先占位再建件；冲突直接 ack，不重复发送。
- 重试：发送失败重试 3 次后 `FAILED`；重试不新建 `notification`，只追加 `notification_send_log`。
- 性能：单条通知多一次唯一索引写入，量级可接受；索引按 `(consumer_group, event_id)` 建。
- 测试：并发同 `event_id` 投递两次，只产生一条通知/一封邮件。

## 回滚方式

1. 若吞吐成为瓶颈，增加 Redis SETNX 作为前置快速判重，**保留 DB 唯一约束**；
2. 若唯一键设计有误，用 Flyway 新增迁移调整索引，禁止手工 drop 生产索引；
3. 不允许改为「无判重」，否则违反 §6.5 消费幂等要求。