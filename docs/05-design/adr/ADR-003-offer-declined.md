# ADR-003：Offer 状态统一为 `DECLINED`

- 状态：已接受
- 对应裁决：`docs/4 §11 D14`
- 日期：2026-10-19 裁决 / 2026-11-02 定稿
- 决策人：成员 A + 评审组
- 关联：`docs/4 §5.9`、`§1.3.1`、`sql/02_recruit_interview.sql` 注释

## 背景

文档状态机写 `DECLINED`，DDL 注释写 `REJECTED`，两者语义相同但取值不同。
Offer 状态会进入事件（`offer.declined`）、前端枚举、统计口径，必须只有一个取值。

## 选项

| 选项 | 说明 | 取舍 |
|---|---|---|
| A. `DECLINED` | 与事件名 `offer.declined`、投递动作 `REJECT` 区分 | 语义清晰；需改 DDL 注释 |
| B. `REJECTED` | 沿用 DDL 注释 | 与 `offer.declined` 事件名不一致，容易被误认为投递拒绝 |
| C. 两值并存 | 兼容历史数据 | 状态机分支翻倍，统计需合并，不可接受 |

## 结论

统一为 `DECLINED`。`offer.status` 取值：
`DRAFT / PENDING_APPROVAL / SENT / CONFIRMED / DECLINED / EXPIRED / WITHDRAWN`。

## 影响

- DDL：`offer.status` 为 `VARCHAR(32)`，无数据库枚举约束，只改注释与种子数据。
- 代码：状态机与枚举类使用 `DECLINED`；`SENT -> DECLINED` 走 CAS，重复拒绝返回 `ITV3301`。
- 事件：`offer.declined` 只在 CAS 成功时发布，payload 带 `reason/declinedAt`。
- 前端：状态标签、筛选器、导出枚举值统一；不再出现 `REJECTED`。
- 统计：拒绝率按 `DECLINED` 计算，历史脏数据（若有）在迁移脚本中一次性订正。

## 回滚方式

数据库存的是字符串，回滚代价低：
1. 若上游报表必须用 `REJECTED`，新增只读视图做值映射，**不要双写**；
2. 代码枚举改动需前后端同版本；
3. 事件名 `offer.declined` 不回滚（已登记在 `EventTypes` 与 `EventSchemaVersion`）。