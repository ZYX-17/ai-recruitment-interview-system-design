# ADR-004：面试状态补 `ROOM_PENDING` 与 `NO_SHOW`

- 状态：已接受
- 对应裁决：`docs/4 §11 D15`
- 日期：2026-10-19 裁决 / 2026-11-02 定稿
- 决策人：成员 A + 成员 C（房间）
- 关联：`docs/4 §2.3`、`§5.9`、`§6.2`、`sql/02_recruit_interview.sql` 注释

## 背景

排期成功后要先建房（room-service 异步回执），建房期间既不是「已排期可面试」，也不是「失败」；
候选人未到场也需要可统计的终态。原有状态机缺少这两个状态，导致：
建房中被迫写成 `SCHEDULED`，房间失败无法回退；未到场只能写 `CANCELLED`，统计口径失真。

## 选项

| 选项 | 说明 | 取舍 |
|---|---|---|
| A. 复用 `SCHEDULED` + 布尔字段 | 加 `room_ready` 标记 | 状态组合爆炸，前端/统计需读两个字段 |
| B. 新增 `ROOM_PENDING` / `NO_SHOW` | 状态机显式表达 | 状态数增加，但语义单一、可统计 |
| C. 建同步建房 | 排期事务内等房间创建 | 跨服务长事务，不可接受 |

## 结论

采用 B。`interview.status` 取值：
`ROOM_PENDING / SCHEDULED / IN_PROGRESS / COMPLETED / CANCELLED / NO_SHOW`。

允许流转（显式状态机，非法流转 `ITV3102`）：

- `ROOM_PENDING -> SCHEDULED`（收到 `room.created`）
- `ROOM_PENDING -> CANCELLED`（取消/建房失败人工处理）
- `SCHEDULED -> IN_PROGRESS`（进房）
- `SCHEDULED -> NO_SHOW`（到点未进房，HR 标记）
- `IN_PROGRESS -> COMPLETED`（`room.finished` 或 HR 确认）
- `SCHEDULED/IN_PROGRESS -> CANCELLED`（取消，删 slot 不删 interview）

## 影响

- 排期（P5）：创建即 `ROOM_PENDING`；收到回执置 `SCHEDULED`；超时未回执进入告警，不自动改状态。
- 房间联调（P10）：`room.created`/`room.failed` 消费必须幂等（`event_id` 唯一）。
- 通知：`ROOM_PENDING` 不发面试邀约，`SCHEDULED` 才发；改期重发。
- 统计：漏斗/工作量排除 `CANCELLED`；`NO_SHOW` 单列，不计入完成。
- 前端：状态标签与可用操作按状态机渲染；`ROOM_PENDING` 显示「建房中」。
- 数据库：`VARCHAR(32)` 无枚举约束，改注释 + 种子即可。

## 回滚方式

状态值一旦写入数据库就不能删除（历史数据无法解释）：
1. 如需回退展示，只在前端隐藏 `ROOM_PENDING`，后端保留；
2. 若要合并状态，必须写迁移脚本批量改写并保留审计，属于破坏性变更，需单独 ADR；
3. 不得把 `ROOM_PENDING` 直接改回 `SCHEDULED`，否则房间失败无法区分。