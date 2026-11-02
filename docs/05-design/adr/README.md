# ADR 索引（W1 决策记录）

> 对应 `docs/4.成员A开发文档-面试管理子系统.md` §11.1 与 §13 P0-5。
> 这 8 个决策已在 v1.1 裁决（§11 D 表），ADR 只记录背景、取舍与回滚方式，**不重开结论**。
> 后续如需推翻，新增 ADR 并将原 ADR 状态改为「已被 ADR-00X 取代」。

| ADR | 对应裁决 | 主题 | 状态 |
|---|---|---|---|
| [ADR-001](ADR-001-success-code.md) | D1 | 统一成功码为 `"0"` | 已接受 |
| [ADR-002](ADR-002-refresh-cookie.md) | D8 | Refresh Token 走 Cookie | 已接受 |
| [ADR-003](ADR-003-offer-declined.md) | D14 | Offer 状态统一 `DECLINED` | 已接受 |
| [ADR-004](ADR-004-interview-status.md) | D15 | 面试状态补 `ROOM_PENDING` / `NO_SHOW` | 已接受 |
| [ADR-005](ADR-005-time-utc.md) | D20 | 时间统一 UTC 存储 | 已接受 |
| [ADR-006](ADR-006-permission-tri-state.md) | D26 | 权限三态「一行一码」 | 已接受 |
| [ADR-007](ADR-007-outbox-replay-route.md) | D29 | Outbox 重放按服务路由 | 已接受 |
| [ADR-008](ADR-008-notify-dedup-db.md) | D30 | 通知去重优先数据库 | 已接受 |