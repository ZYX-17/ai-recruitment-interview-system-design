# ADR-007：Outbox 重放按服务路由

- 状态：已接受
- 对应裁决：`docs/4 §11 D29`（配合 D10 管理路由）
- 日期：2026-10-19 裁决 / 2026-11-02 定稿
- 决策人：成员 A + 运维
- 关联：`docs/4 §1.6 X-03/X-12`、`§3.1.3`、`§6.4`

## 背景

auth/interview/notify 三个服务各有一张 `local_message`（Outbox）。
最初设计 `POST /api/admin/outbox/{id}/replay` 单路径，但网关无法判断该 ID 属于哪个库/服务，
只能固定转发到一个服务，另外两个服务的死信无法重放。

## 选项

| 选项 | 说明 | 取舍 |
|---|---|---|
| A. 单路径 + 网关猜服务 | 按 ID 段/前缀猜 | 不可靠，ID 生成规则可能重叠 |
| B. 每服务一条路径 | `/api/admin/outbox/auth/...` | 路由明确，但前端要按服务拼 |
| C. 路径带 `{service}` + 网关注册三条路由 | 统一形态，按前缀路由 | 需要保证路由顺序 |

## 结论

采用 C：

- 重放：`POST /api/admin/outbox/{service}/{id}/replay`
- 列表：`GET /api/admin/outbox/{service}/messages`
- `service ∈ {auth, interview, notify}`；其他值返回 `SYS400`
- 权限码统一 `system:config:update`；调用方为管理员
- 网关必须为三条 outbox 路由配置 **`Path=/api/admin/outbox/auth/**` 等精确前缀**，并排在 `auth-admin`（`/api/admin/**`）之前，否则会被兜底路由截走。

## 影响

- `api-gateway`：新增 3 条路由，顺序敏感；需路由单测（outbox 优先于 admin）。
- 服务端：三个服务各自实现重放接口，仅操作本库 `local_message`；重放保持同一 `event_id`。
- 运维：管理页先列后放，重放需二次确认并写审计。
- 安全：仅管理员；`/internal/**` 仍 403。

## 回滚方式

1. 关闭三条 outbox 路由，管理页隐藏重放入口；
2. 运维改用服务管理端口直连重放（内网）；
3. 不恢复「单路径猜服务」方案；若必须，新增 ADR 说明 ID 归属规则。