# sql/ 文件夹说明 —— 招聘面试系统建表 DDL（v3.1）

> 生成日期：2026-10-19 ｜ 依据：《3.开发文档-招聘面试系统.md》2.1 数据库 schema、2.2 表清单与关键约束、2.3 核心 DDL 约定
> 全库共 **71 张表**：57 张业务表 + 每库必备的 `local_message`（Outbox）与 `idempotent_consumption`（幂等消费）×7 库

## 1. 文件清单与执行顺序

| 顺序 | 文件 | 库 | 服务 | Owner | 表数 |
|---|---|---|---|---|---|
| 1 | `00_create_databases.sql` | 7 库建库 | — | — | 0（仅 CREATE DATABASE） |
| 2 | `01_recruit_auth.sql` | recruit_auth | auth-service (IAM) | **A** | 9 + 2 = 11 |
| 3 | `02_recruit_interview.sql` | recruit_interview | interview-service | **A** | 14 + 2 = 16 |
| 4 | `03_recruit_room.sql` | recruit_room | room-service | **C** | 8 + 2 = 10 |
| 5 | `04_recruit_resume.sql` | recruit_resume | resume-service | **B** | 14 + 2 = 16 |
| 6 | `05_recruit_notify.sql` | recruit_notify | notify-service | **A** | 3 + 2 = 5 |
| 7 | `06_recruit_file.sql` | recruit_file | file-service | **B** | 1 + 2 = 3 |
| 8 | `07_recruit_ai.sql` | recruit_ai | ai-service (Python) | **B** | 8 + 2 = 10 |

执行方式（二选一）：

```bash
# 方式一：本文件夹作为母版直接初始化
mysql -u root -p < sql/00_create_databases.sql
for f in sql/0{1..7}_*.sql; do mysql -u root -p < "$f"; done

# 方式二（推荐）：W1 起各服务 Flyway 托管——从本文件夹抽取对应库内容，
# 放入各服务 src/main/resources/db/migration/V1_0__init.sql，此后只前向迁移
```

## 2. 全库通用约定（与开发文档 2.3 一致）

- **主键**：`BIGINT UNSIGNED`，雪花 ID 应用层生成，**无 AUTO_INCREMENT**（前端一律按 string 处理）；
- **枚举**：一律 `VARCHAR`，取值写在列注释，**不用 MySQL ENUM**；
- **时间**：`DATETIME(3)`（毫秒），连接时区统一 UTC（JDBC `serverTimezone=UTC`），API 输出 ISO 8601；
- **乐观锁**：关键状态表带 `version INT NOT NULL DEFAULT 0`，流转/更新必须带 version 条件；
- **JSON**：用 MySQL 原生 `JSON` 类型（结构化字段、事件 payload、评分明细等）；
- **字符集**：utf8mb4 / utf8mb4_0900_ai_ci（建库时统一指定）；
- **跨库规则**：只写自己 schema；跨服务只存 ID + 快照（如 `person_name`、`job_title`），**禁止跨库 JOIN/跨库写**；
- **公共表**：7 个库均含同构的 `local_message`（事务内写入 → publisher confirms 后 CONFIRMED，retry ≤3 转死信）与 `idempotent_consumption`（`(consumer_group, event_id)` 唯一）。

## 3. 与文档 2.3 示例的差异（增强项，均已注释标明）

| 位置 | 差异 | 原因 |
|---|---|---|
| `room` 表 | 增加 `recording_status` 快照列 | 列表页免 JOIN `record_meta` 展示录制状态；详情仍以 `record_meta` 为准 |
| `local_message`（全部库） | 增加了 `event_version / aggregate_id / trace_id` 列 | 与开发文档 5.1 事件信封规范（eventId/eventVersion/aggregateId/traceId）对齐 |
| `iam_refresh_session` | DB 登记 + Redis 主存 | Redis Key 契约（`auth:refresh:{hash}` / family / blacklist）不变，本表承担会话追溯与审计 |

## 4. seed 数据归属（本文件夹不包含，避免版本混乱）

| seed | 落地方式 | 时机 | Owner |
|---|---|---|---|
| 五角色 + 权限码全集（4.4） | auth-service Flyway `V2_0__*.sql` 或启动 seed | W2 | A |
| 36 页菜单路由 | auth-service `V2_1__*.sql` | W2 | A |
| 通知模板（邀约/Offer/提醒） | notify-service `V2_0__*.sql` | W5~6 | A |
| 模型四档（vLLM/云×2/Ollama/Mock） | ai-service 初始化脚本 | W1 | B |
| Prompt 初始版本（4 类） | ai-service 初始化脚本 | W3~4 | B |
| 演示数据（岗位/投递/排期/3 简历） | `V9_0__seed_demo.sql`（独立可重复执行） | W15 | A |

后续可能新增的文件（含 SQL 演进、部署脚本、评测资产等）统一登记在 **`可能新增的文件.md`**。
