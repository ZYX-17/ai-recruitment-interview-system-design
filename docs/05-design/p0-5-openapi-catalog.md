# P0-5 OpenAPI 冻结目录（成员 A）

> 对应任务：`docs/4.成员A开发文档-面试管理子系统.md` §13 P0-5、附录 D
> 冻结规则：`docs/4 §1.8`；端点组口径：`docs/4 §1.5`
> 权限码机器可读版：`docs/05-design/p0-5-permission-registry.csv`

## 1. 这份目录是什么

W1 冻结的是 **operation 清单**（method + path + operationId + 权限码 + 幂等 + 错误码），不是完整 YAML。
实现阶段由各服务用 SpringDoc 注解生成 YAML，CI 与本文逐项 diff；本文与实现不一致时以本文为准并回写实现。

冻结内容：

1. 111 个 operation 的标识与归属 tag；
2. 每个 operation 的权限码（公开接口显式标注）；
3. 写操作的幂等要求与更新操作的乐观锁要求；
4. 每个 operation 的错误码（基线 + 域内附加）。

不在本文冻结：请求/响应字段的完整 JSON Schema（见 `docs/4 §5` 与各服务 DTO），只在实现时按 §5 落 Schema 并补示例。

## 2. 通用约定

### 2.1 Tag

`auth`、`admin`、`interview-job`、`interview-application`、`interview-schedule`、`interview-evaluation`、`interview-minutes`、`interview-offer`、`interview-async`、`interview-stats`、`notify`、`portal`。

> `portal` 是 P0-5 新增 tag（§1.8 原表未列）：门户接口免登录或仅候选人可用，需与内部接口分开，避免误挂 `bearerAuth`。
> `internal` 不是公开 tag，仅用于本文第 16 节分组；内部接口不进公开 OpenAPI。

### 2.2 operationId

格式 `{domain}_{resource}_{action}`，全仓库唯一，例：`interview_schedule_create`、`interview_offer_confirm`、`admin_user_assign_role`。
禁止使用 `get1`、`create2` 这类生成器默认名；同一 operation 不允许出现两个 operationId。

### 2.3 参数与响应基线

| 项 | 约定 |
|---|---|
| 统一响应 | `Result<T>`：`code`（成功为 `"0"`）、`message`、`data`、`traceId` |
| 分页入参 | `pageNum`（≥1）、`pageSize`（1~100） |
| 分页出参 | `{list,total,pageNum,pageSize,pages}` |
| 时间 | ISO 8601，UTC（ADR-005） |
| 写操作 | `Idempotency-Key` 头（UUID，客户端生成，10 分钟窗口） |
| 更新操作 | body 带 `version`，冲突返回 409 |
| 认证 | 默认 `bearerAuth`；公开门户 `security: []`；内部接口不进公开 OpenAPI |
| 追踪 | 响应头与 body 均带 `traceId`；网关统一 `X-Trace-Id` |

### 2.4 错误码基线

每个 operation 至少列出 1 个成功 + 1 个失败示例（§1.8）。表中「错误」列只写**基线之外**的域内错误。

| 适用 | 必列 |
|---|---|
| 全部 | 200 成功；400 `SYS400`；403 `AUTH1101`；500 `SYS500` |
| 需登录 | 401 `GW9001` |
| 有路径资源 | 404（域码，如 `ITV3001`） |
| 写操作 | 409 `SYS409`（同一 Key 首次请求执行中） |
| 状态机操作 | 409 `ITV3102` |
| 排期写入 | 409 `ITV3101` |
| 限流 | 429 `GW9003` |
| 公开接口 | 不列 401/403 |

## 3. 统计

| Tag | operation 数 |
|---|---:|
| auth | 7 |
| admin | 29 |
| interview-job（含流程模板） | 12 |
| interview-application | 5 |
| interview-schedule | 8 |
| interview-evaluation | 11 |
| interview-minutes | 2 |
| interview-offer | 6 |
| interview-async | 10 |
| interview-stats | 3 |
| portal | 13 |
| notify | 5 |
| **合计** | **111** |

## 4. auth（tag: auth）

| operationId | Method | Path | 权限 | 幂等/版本 | 额外错误 |
|---|---|---|---|---|---|
| `auth_login` | POST | `/api/auth/login` | 公开 | Idempotency-Key 可选 | `AUTH1001` |
| `auth_register` | POST | `/api/auth/register` | 公开 | Idempotency-Key | — |
| `auth_refresh` | POST | `/api/auth/refresh` | 公开（Refresh Cookie） | — | `AUTH1002`、`AUTH1003` |
| `auth_logout` | POST | `/api/auth/logout` | 登录 | 幂等 | — |
| `auth_password_change` | PUT | `/api/auth/password` | 登录 | — | `AUTH1004` |
| `auth_me` | GET | `/api/auth/me` | 登录 | — | — |
| `auth_menus` | GET | `/api/auth/menus` | 登录 | — | — |

## 5. admin（tag: admin）

| operationId | Method | Path | 权限码 | 幂等/版本 | 额外错误 |
|---|---|---|---|---|---|
| `admin_user_page` | GET | `/api/admin/users` | `system:user:view` | — | — |
| `admin_user_create` | POST | `/api/admin/users` | `system:user:create` | Idempotency-Key | — |
| `admin_user_detail` | GET | `/api/admin/users/{id}` | `system:user:view` | — | — |
| `admin_user_update` | PUT | `/api/admin/users/{id}` | `system:user:update` | version | 409 版本冲突 |
| `admin_user_delete` | DELETE | `/api/admin/users/{id}` | `system:user:delete` | — | `SYS400`（删除自己/最后一个超管） |
| `admin_user_assign_role` | PUT | `/api/admin/users/{id}/roles` | `system:user:assign-role` | version | — |
| `admin_user_reset_password` | POST | `/api/admin/users/{id}/reset-password` | `system:user:reset-password` | Idempotency-Key | — |
| `admin_user_kick` | POST | `/api/admin/users/{id}/kick` | `system:user:kick` | 幂等 | — |
| `admin_role_page` | GET | `/api/admin/roles` | `system:role:view` | — | — |
| `admin_role_create` | POST | `/api/admin/roles` | `system:role:create` | Idempotency-Key | — |
| `admin_role_detail` | GET | `/api/admin/roles/{id}` | `system:role:view` | — | — |
| `admin_role_update` | PUT | `/api/admin/roles/{id}` | `system:role:update` | version | — |
| `admin_role_delete` | DELETE | `/api/admin/roles/{id}` | `system:role:delete` | — | `SYS400`（系统角色/仍有用户） |
| `admin_role_assign_permission` | PUT | `/api/admin/roles/{id}/permissions` | `system:role:assign-menu` | version | — |
| `admin_role_assign_menu` | PUT | `/api/admin/roles/{id}/menus` | `system:role:assign-menu` | version | — |
| `admin_permission_tree` | GET | `/api/admin/permissions/tree` | `system:menu:view` | — | — |
| `admin_permission_create` | POST | `/api/admin/permissions` | `system:menu:create` | Idempotency-Key | — |
| `admin_permission_update` | PUT | `/api/admin/permissions/{id}` | `system:menu:update` | version | — |
| `admin_permission_delete` | DELETE | `/api/admin/permissions/{id}` | `system:menu:delete` | — | `SYS400`（仍有子节点/角色引用） |
| `admin_dept_tree` | GET | `/api/admin/depts/tree` | `system:dept:view` | — | — |
| `admin_dept_create` | POST | `/api/admin/depts` | `system:dept:create` | Idempotency-Key | — |
| `admin_dept_update` | PUT | `/api/admin/depts/{id}` | `system:dept:update` | version | — |
| `admin_dept_delete` | DELETE | `/api/admin/depts/{id}` | `system:dept:delete` | — | `SYS400`（有子部门/用户） |
| `admin_dict_page` | GET | `/api/admin/dicts` | `system:dict:view` | — | — |
| `admin_dict_update` | PUT | `/api/admin/dicts/{id}` | `system:dict:update` | version | — |
| `admin_audit_log_page` | GET | `/api/admin/audit-logs` | `system:log:oper:view` | — | — |
| `admin_login_log_page` | GET | `/api/admin/login-logs` | `system:log:login:view` | — | — |
| `admin_outbox_page` | GET | `/api/admin/outbox/{service}/messages` | `system:config:update` | — | `SYS400`（service 非法） |
| `admin_outbox_replay` | POST | `/api/admin/outbox/{service}/{id}/replay` | `system:config:update` | 幂等 | `SYS400`（service 非法/状态不可重放） |

## 6. interview-job（tag: interview-job）

| operationId | Method | Path | 权限码 | 幂等/版本 | 额外错误 |
|---|---|---|---|---|---|
| `interview_job_page` | GET | `/api/interview/jobs` | `interview:job:view` | — | — |
| `interview_job_create` | POST | `/api/interview/jobs` | `interview:job:create` | Idempotency-Key | `SYS400`（jobCode 重复） |
| `interview_job_detail` | GET | `/api/interview/jobs/{id}` | `interview:job:view` | — | `ITV3001` |
| `interview_job_update` | PUT | `/api/interview/jobs/{id}` | `interview:job:update` | version | 409 版本冲突 |
| `interview_job_delete` | DELETE | `/api/interview/jobs/{id}` | `interview:job:delete` | — | `SYS400`（已有投递） |
| `interview_job_publish` | POST | `/api/interview/jobs/{id}/publish` | `interview:job:publish` | 幂等 | `ITV3102` |
| `interview_job_close` | POST | `/api/interview/jobs/{id}/close` | `interview:job:close` | 幂等 | `ITV3102` |
| `interview_process_page` | GET | `/api/interview/process-templates` | `interview:process:view` | — | — |
| `interview_process_create` | POST | `/api/interview/process-templates` | `interview:process:create` | Idempotency-Key | `SYS400`（阶段重复/顺序非法） |
| `interview_process_detail` | GET | `/api/interview/process-templates/{id}` | `interview:process:view` | — | `ITV3001` |
| `interview_process_update` | PUT | `/api/interview/process-templates/{id}` | `interview:process:update` | version | `ITV3102`（已被引用只增不改） |
| `interview_process_delete` | DELETE | `/api/interview/process-templates/{id}` | `interview:process:delete` | — | `SYS400`（已被引用） |

## 7. interview-application（tag: interview-application）

| operationId | Method | Path | 权限码 | 幂等/版本 | 额外错误 |
|---|---|---|---|---|---|
| `interview_application_page` | GET | `/api/interview/applications` | `interview:application:view` | — | — |
| `interview_application_create` | POST | `/api/interview/applications` | `interview:application:create` | Idempotency-Key | `SYS400`（`(job_id,person_id)` 重复/岗位非发布态） |
| `interview_application_detail` | GET | `/api/interview/applications/{id}` | `interview:application:view` | — | `ITV3001` |
| `interview_application_timeline` | GET | `/api/interview/applications/{id}/timeline` | `interview:application:view` | — | `ITV3001` |
| `interview_application_action` | POST | `/api/interview/applications/{id}/actions` | 按 `action` 动态校验（见 §7.1） | Idempotency-Key + version | `ITV3102`、`ITV3001` |

### 7.1 动作与权限码映射

| action | 权限码 |
|---|---|
| `ADVANCE` / `PASS` / `HOLD` | `interview:application:update` |
| `REJECT` | `interview:application:reject` |
| `TRANSFER` | `interview:application:transfer` |
| `WITHDRAW`（HR 代撤） | `interview:application:withdraw` |
| `HIRE` | `interview:application:update` |

> 门户候选人撤回走 `portal_application_withdraw`，不复用此端点。

## 8. interview-schedule（tag: interview-schedule）

| operationId | Method | Path | 权限码 | 幂等/版本 | 额外错误 |
|---|---|---|---|---|---|
| `interview_schedule_page` | GET | `/api/interview/schedules` | `interview:schedule:view` | — | — |
| `interview_schedule_create` | POST | `/api/interview/schedules` | `interview:schedule:create` | Idempotency-Key | `ITV3101`、`SYS400`（非 30 分钟对齐） |
| `interview_schedule_detail` | GET | `/api/interview/schedules/{id}` | `interview:schedule:view` | — | `ITV3001` |
| `interview_schedule_update` | PUT | `/api/interview/schedules/{id}` | `interview:schedule:update` | version | `ITV3101`、`ITV3102` |
| `interview_schedule_cancel` | DELETE | `/api/interview/schedules/{id}` | `interview:schedule:cancel` | 幂等 | `ITV3102` |
| `interview_calendar_query` | GET | `/api/interview/calendar` | `interview:schedule:view` | — | — |
| `interview_schedule_conflict_check` | POST | `/api/interview/schedules/conflict-check` | `interview:schedule:view` | — | `SYS400`（非 30 分钟对齐） |
| `interview_schedule_batch` | POST | `/api/interview/schedules/batch` | `interview:schedule:batch` | Idempotency-Key | `ITV3101`（含冲突明细）、`SYS400`（>50 条） |

## 9. interview-evaluation（tag: interview-evaluation）

| operationId | Method | Path | 权限码 | 幂等/版本 | 额外错误 |
|---|---|---|---|---|---|
| `interview_evaluation_template_page` | GET | `/api/interview/evaluation-templates` | `interview:evaluation-template:view` | — | — |
| `interview_evaluation_template_create` | POST | `/api/interview/evaluation-templates` | `interview:evaluation-template:create` | Idempotency-Key | `SYS400`（权重/满分非法） |
| `interview_evaluation_template_detail` | GET | `/api/interview/evaluation-templates/{id}` | `interview:evaluation-template:view` | — | `ITV3001` |
| `interview_evaluation_template_update` | PUT | `/api/interview/evaluation-templates/{id}` | `interview:evaluation-template:update` | version | `ITV3102`（生成新版本） |
| `interview_evaluation_template_delete` | DELETE | `/api/interview/evaluation-templates/{id}` | `interview:evaluation-template:delete` | — | `SYS400`（已被引用） |
| `interview_evaluation_list` | GET | `/api/interview/schedules/{id}/evaluations` | `interview:evaluation:view` | — | `ITV3001` |
| `interview_evaluation_submit` | POST | `/api/interview/schedules/{id}/evaluations` | `interview:evaluation:submit` | Idempotency-Key | `ITV3102`（重复提交/非本人） |
| `interview_evaluation_mine` | GET | `/api/interview/evaluations/my` | `interview:evaluation:view` | — | — |
| `interview_evaluation_comparison` | GET | `/api/interview/schedules/{id}/evaluations/comparison` | `interview:evaluation:view` | — | `ITV3001`（无评价） |
| `interview_evaluation_ai_draft` | GET | `/api/interview/schedules/{id}/evaluations/ai-draft` | `interview:evaluation:view` | — | `INSUFFICIENT_TRANSCRIPT`（`AIS5xxx`） |
| `interview_evaluation_reopen` | PUT | `/api/interview/evaluations/{id}/reopen` | `interview:evaluation:reopen` | version | `ITV3102` |

## 10. interview-minutes（tag: interview-minutes）

| operationId | Method | Path | 权限码 | 幂等/版本 | 额外错误 |
|---|---|---|---|---|---|
| `interview_minutes_detail` | GET | `/api/interview/schedules/{id}/minutes` | `interview:minutes:view` | — | `ITV3001` |
| `interview_minutes_confirm` | PUT | `/api/interview/minutes/{id}/confirm` | `interview:minutes:confirm` | version | `ITV3102`（已确认/驳回） |

## 11. interview-offer（tag: interview-offer）

| operationId | Method | Path | 权限码 | 幂等/版本 | 额外错误 |
|---|---|---|---|---|---|
| `interview_offer_page` | GET | `/api/interview/offers` | `interview:offer:view` | — | — |
| `interview_offer_create` | POST | `/api/interview/offers` | `interview:offer:create` | Idempotency-Key | `SYS400`（applicationId 已存在 Offer） |
| `interview_offer_detail` | GET | `/api/interview/offers/{id}` | `interview:offer:view` | — | `ITV3001` |
| `interview_offer_send` | POST | `/api/interview/offers/{id}/send` | `interview:offer:send` | 幂等 | `ITV3102`、`ITV3301` |
| `interview_offer_approve` | POST | `/api/interview/offers/{id}/approve` | `interview:offer:approve` | 幂等 | `ITV3102` |
| `interview_offer_withdraw` | POST | `/api/interview/offers/{id}/withdraw` | `interview:offer:withdraw` | 幂等 | `ITV3102`、`ITV3301` |

## 12. interview-async（tag: interview-async）

| operationId | Method | Path | 权限码 | 幂等/版本 | 额外错误 |
|---|---|---|---|---|---|
| `interview_async_page` | GET | `/api/interview/async-interviews` | `interview:async:view` | — | — |
| `interview_async_create` | POST | `/api/interview/async-interviews` | `interview:async:create` | Idempotency-Key | `SYS400`（application 已存在试卷） |
| `interview_async_detail` | GET | `/api/interview/async-interviews/{id}` | `interview:async:view` | — | `ITV3001` |
| `interview_async_update` | PUT | `/api/interview/async-interviews/{id}` | `interview:async:update` | version | `ITV3102`（非 DRAFT） |
| `interview_async_publish` | POST | `/api/interview/async-interviews/{id}/publish` | `interview:async:publish` | 幂等 | `ITV3102` |
| `interview_async_question_create` | POST | `/api/interview/async-interviews/{id}/questions` | `interview:async:update` | Idempotency-Key | `ITV3102`（非 DRAFT/序号重复） |
| `interview_async_invite_link` | POST | `/api/interview/async-interviews/{id}/invite-link` | `interview:async:publish` | version | `ITV3102` |
| `interview_async_answers` | GET | `/api/interview/async-interviews/{id}/answers` | `interview:async:view` | — | `ITV3001` |
| `interview_async_review` | PUT | `/api/interview/async-interviews/{id}/answers/{answerId}/review` | `interview:async:update` | version | `ITV3102` |
| `interview_async_reset` | POST | `/api/interview/async-interviews/{id}/reset` | `interview:async:update` | version | `ITV3102` |

## 13. interview-stats（tag: interview-stats）

| operationId | Method | Path | 权限码 | 幂等/版本 | 额外错误 |
|---|---|---|---|---|---|
| `interview_stats_funnel` | GET | `/api/interview/stats/funnel` | `interview:stats:view` | — | `SYS400`（时间范围非法） |
| `interview_stats_workload` | GET | `/api/interview/stats/workload` | `interview:stats:view` | — | `SYS400`（时间范围非法） |
| `interview_stats_time_to_hire` | GET | `/api/interview/stats/time-to-hire` | `interview:stats:view` | — | `SYS400`（时间范围非法） |

## 14. portal（tag: portal，`security: []` 或候选人本人）

| operationId | Method | Path | 权限码 | 幂等/版本 | 额外错误 |
|---|---|---|---|---|---|
| `portal_job_page` | GET | `/api/interview/portal/jobs` | `portal:job:view`（公开只读） | — | — |
| `portal_job_detail` | GET | `/api/interview/portal/jobs/{id}` | `portal:job:view`（公开只读） | — | `ITV3001`（未发布不返回） |
| `portal_application_page` | GET | `/api/interview/portal/applications` | `portal:application:view` | — | — |
| `portal_application_create` | POST | `/api/interview/portal/applications` | `portal:application:create` | Idempotency-Key | `SYS400`（重复投递/岗位未发布） |
| `portal_application_withdraw` | POST | `/api/interview/portal/applications/{id}/withdraw` | `portal:application:withdraw` | 幂等 | `ITV3102` |
| `portal_interview_page` | GET | `/api/interview/portal/interviews` | `portal:interview:view` | — | — |
| `portal_interview_detail` | GET | `/api/interview/portal/interviews/{id}` | `portal:interview:view` | — | `SYS404`（非本人面试） |
| `portal_interview_join` | POST | `/api/interview/portal/interviews/{id}/join` | `portal:interview:join` | 幂等 | `ITV3102`（未到可进房时间） |
| `portal_async_detail` | GET | `/api/interview/portal/async-invites/{token}` | `portal:async:view`（免登录） | — | `ITV3001`（token 无效） |
| `portal_async_start` | POST | `/api/interview/portal/async-invites/{token}/start` | `portal:async:start`（免登录） | Idempotency-Key | `ITV3102` |
| `portal_async_submit` | POST | `/api/interview/portal/async-invites/{token}/submit` | `portal:async:submit`（免登录） | Idempotency-Key | `AIS5102`（防重复提交） |
| `portal_offer_detail` | GET | `/api/interview/portal/offers/{token}` | `portal:offer:view`（免登录） | — | `ITV3301`（过期/已处理） |
| `portal_offer_respond` | POST | `/api/interview/portal/offers/{token}/respond` | `portal:offer:respond`（免登录） | Idempotency-Key（CAS） | `ITV3301` |

> `portal_interview_page` / `portal_interview_join` 是 P0-5 补登记的端点：`docs/4` 附录 C 已列 `portal:interview:view/join`，但 §1.3 未给路径；实现按本表落地，房间 token 仍由 room-service 签发，interview 只做归属校验后转发。

## 15. notify（tag: notify）

| operationId | Method | Path | 权限码 | 幂等/版本 | 额外错误 |
|---|---|---|---|---|---|
| `notify_message_page` | GET | `/api/notify/messages` | 登录（本人；门户候选人同用） | — | — |
| `notify_message_read` | PUT | `/api/notify/messages/{id}/read` | 登录（本人） | 幂等 | — |
| `notify_message_resend` | POST | `/api/notify/messages/{id}/resend` | `system:notice:update` | 幂等 | `NTF6002` |
| `notify_template_page` | GET | `/api/notify/templates` | `system:notice:view` | — | — |
| `notify_template_update` | PUT | `/api/notify/templates/{id}` | `system:notice:update` | version | `NTF6001` |

> `portal:message:view` 复用 `notify_message_page`，不单独建 operation；权限码保留用于门户菜单。

## 16. internal（tag: internal，不进入公开 OpenAPI，网关一律 403）

| operationId | Method | Path | 调用方 → 提供方 | 鉴权 |
|---|---|---|---|---|
| `internal_offer_confirm_link` | POST | `/internal/offers/{id}/confirm-link` | notify → interview | `X-Internal-Token` |
| `internal_interview_minutes_write` | POST | `/internal/interviews/{id}/minutes` | ai-service → interview | `X-Internal-Token` |
| `internal_async_ai_result` | POST | `/internal/async-interviews/answers/{answerId}/ai-result` | ai-service → interview | `X-Internal-Token` |
| `internal_async_invite_link` | POST | `/internal/async-interviews/{id}/invite-link` | notify → interview | `X-Internal-Token` |
| `internal_interview_participants` | GET | `/internal/interviews/{id}/participants` | room-service → interview | `X-Internal-Token` |
| `internal_users_authz` | GET | `/internal/users/{userId}/authz` | interview/notify → auth | `X-Internal-Token` |

> 内部接口不建权限码、不进前端 SDK、不在 OpenAPI 暴露；字段与幂等规则见 `docs/4 §1.6`。

## 17. 冻结清单（W1 评审）

- [x] `operationId` 全量唯一（111 个）
- [x] 每个非公开 operation 均登记权限码
- [x] 每个写操作标注 `Idempotency-Key`，每个更新标注 `version`
- [x] 每个 operation 有「成功 + 400/403/409」示例要求
- [x] 公开门户接口显式 `security: []`
- [x] 内部接口不进公开 OpenAPI
- [ ] 字段级 Schema 与 `docs/4 §5` DTO 逐项对齐（P1 起按服务补）
- [ ] CI 生成 YAML 与本文 diff（P0-6 接）

## 18. 与 §1.5「41 个端点组」的映射

| 文档端点组 | 本文 operation 数 | 说明 |
|---|---:|---|
| auth 15 组 | 36 | 7 个会话接口（P1-5 增补自助改密）+ 29 个管理端接口（GET/POST/PUT/DELETE 拆分） |
| interview 22 组 | 57 | I-22 子表（Offer/异步/统计）展开 |
| notify 4 组 | 5 | 拆分模板列表/更新、增加重发 |
| portal（附录 C） | 13 | 门户端点独立成 tag（含面试详情） |
| **合计** | **111** | 41 是任务分解口径，不是 operation 上限（§1.5） |

## 19. 变更流程

1. 新增/修改 operation 必须改本文 + `p0-5-permission-registry.csv`，同 PR 提交；
2. 新增权限码必须同步 `docs/4` 附录 C 与 auth seed（P1-7）；
3. 删除/改语义按 ADR 流程，新增 ADR 并在本文标注废弃 operation；
4. 每次发布前 CI 跑 SpringDoc 生成 + diff，不一致即失败（P0-6/P12-5）。