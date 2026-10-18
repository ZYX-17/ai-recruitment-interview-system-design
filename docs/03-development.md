# 招聘面试系统开发文档（v3.0）

> 配套架构：[`02-architecture.md`](02-architecture.md)（v3.0）  
> 定位：本文是**自包含的开发契约**，覆盖服务边界、数据库、API、鉴权、AI/ASR、在线面试、部署与验收。  
> 阅读方式：先看第 2 节服务/数据所有权，再按各自子系统查第 3~8 节；W1 冻结后以本文 + Flyway/OpenAPI 为准。

---

## 1. 工程基线

| 领域 | 版本/选型 |
|---|---|
| Java | JDK 17 LTS + Spring Boot 3.2.x + Spring Cloud 2023.0.x + SC Alibaba 2023.0.1 |
| 注册/配置 | Nacos 2.3.x（单机起步） |
| 网关 | Spring Cloud Gateway（WebFlux） |
| RPC | OpenFeign + Spring LoadBalancer |
| MQ | RabbitMQ 3.13（publisher confirms + 延迟插件） |
| 缓存 | Redis 7.2 |
| 数据库 | MySQL 8.0 |
| 检索/向量 | Elasticsearch 8.13 + IK |
| 对象存储 | MinIO（S3 兼容） |
| 媒体 | LiveKit SFU + Egress |
| AI 编排 | Python 3.11 + FastAPI + openai SDK |
| 推理 | vLLM Qwen2.5-7B-Instruct-AWQ（主）；云端 LLM/Ollama/Mock 降级 |
| 文档解析 | MinerU（mineru-worker） |
| ASR | FunASR（ai-worker，流式） |
| 前端 | Vue 3.4 + TypeScript + Vite + Pinia + Element Plus + livekit-client |
| 部署 | Docker Compose：base / app / gpu / profiles |

### 1.1 代码仓库

```
recruitment-interview-platform/
├── backend/
│   ├── pom.xml
│   ├── common/                 # Result/错误码/日志/traceId/雪花ID/Redis Key
│   ├── contracts/              # 跨服务 DTO、事件 Schema（不放实体/业务逻辑）
│   ├── api-gateway/
│   ├── auth-service/
│   ├── interview-service/
│   ├── room-service/
│   ├── resume-service/
│   ├── notify-service/
│   └── file-service/
├── ai/
│   ├── ai-service/             # FastAPI：LLM 路由/Prompt/任务/审计
│   ├── ai-worker/              # FunASR + BGE
│   └── mineru-worker/          # MinerU
├── frontend/                   # 单 SPA
├── infra/                      # compose/mysql/es/livekit/prometheus
├── docs/
└── scripts/                    # smoke/deploy/rollback/evaluation
```

### 1.2 服务清单

| 服务 | 语言 | 端口 | schema | Owner |
|---|---|---:|---|---|
| gateway | Java | 8080 | — | BE-A |
| auth | Java | 8081 | `recruit_auth` | BE-A |
| interview | Java | 8083 | `recruit_interview` | BE-A |
| room | Java | 8085 | `recruit_room` | BE-A（后端）+ FE（房间前端） |
| resume | Java | 8084 | `recruit_resume` | BE-B |
| ai-service | Python/FastAPI | 9010 | `recruit_ai` | BE-B |
| notify | Java | 8086 | `recruit_notify` | BE-B |
| file | Java | 8087 | `recruit_file` | BE-B |
| ai-worker | Python | 8001 | 无业务表 | BE-B |
| mineru-worker | Python | 8002 | 无业务表 | BE-B |

> vLLM 在 GPU 节点 8000；LiveKit 7880/7881 + UDP 50000-50100；Egress 独立容器。

---

## 2. 服务与数据所有权

### 2.1 数据库 schema

`recruit_auth`、`recruit_interview`、`recruit_room`、`recruit_resume`、`recruit_notify`、`recruit_file`、`recruit_ai`。gateway 无库。

规则：

- 只能写自己 schema；跨服务只存 ID + 快照，禁止跨库 JOIN/跨库写。
- 每个 schema 附 `local_message`（发件箱）与 `idempotent_consumption`（消费幂等）。
- 雪花 ID 字符串化；时间 UTC 存储、ISO 8601 输出；枚举 `VARCHAR(32)`；关键状态表 `version` 乐观锁。
- `job`/`application` 归 interview；resume 通过 `job.published/job.updated` 维护 `job_snapshot` 做匹配。
- 候选人 `person` 归 resume；interview/room 存 `person_id` + 姓名/岗位快照。

### 2.2 表清单与关键约束

#### IAM（recruit_auth）

| 表 | 关键字段/约束 |
|---|---|
| `iam_user` | `username` 唯一；`password_hash`；`display_name`；`user_type`（EMPLOYEE/CANDIDATE）；`status`；`token_version`；`phone_enc`/`phone_hash`；`dept_id` |
| `iam_role` | `role_code` 唯一；`role_name`；`is_system_role`；`data_scope` |
| `iam_permission` | `permission_code` 唯一；`type`（MENU/BUTTON/API）；`parent_id`；路由/组件或 API pattern |
| `iam_user_role` | `(user_id, role_id)` 唯一 |
| `iam_role_permission` | `(role_id, permission_id)` 唯一（管理员勾选落点） |
| `iam_data_scope` | `role_id`/`user_id`；`scope_type`（ALL/DEPARTMENT/ASSIGNED/SELF）；`biz_rule` JSON |
| `iam_department` | `parent_id`、`ancestors`、`dept_name`、`status` |
| `iam_refresh_session` | `token_hash` 唯一、`user_id`、`expires_at`、`revoked_at`、设备信息 |
| `iam_audit_log` | 操作者、动作、资源、结果、IP、`trace_id`（不记密码/令牌/简历全文） |

#### 面试（recruit_interview）

| 表 | 关键字段/约束 |
|---|---|
| `job` | `job_code` 唯一；`title`、`dept_id`、`jd_text`、`requirements_json`、`status`、`owner_hr_id`、`version` |
| `job_stage_template` | `job_id`；`stage_flow` JSON；`score_dims` JSON；`version`（模板版本化） |
| `application` | `(job_id, person_id)` 唯一；`current_stage`；`stage_flow` JSON；`match_score`；`status`；`reject_reason` |
| `application_stage_history` | `application_id`、`stage_no`、`round_no`、`status`、`result`、`operator_id`、时间 |
| `interview` | `(application_id, round_no)` 唯一；`interviewer_ids` JSON；`start/end_time`；`mode`；`room_type`；`room_id`；`status` |
| `schedule_slot` | **`(participant_type, participant_id, slot_date, slot_no)` 唯一**；`interview_id`；30 分钟/格；取消物理删行 |
| `evaluation` | `(interview_id, round_no, interviewer_id)` 唯一；`dimension_scores` JSON；`result`；`template_version` |
| `evaluation_dimension` | 模板 id、维度 code/name、权重、满分、否决阈值 |
| `interview_minutes` | `interview_id` 唯一；`transcript_object_key`；`minutes_md`；`ai_evaluation` JSON；`review_status` |
| `offer` | `application_id` 唯一；`confirm_token_hash` 唯一；`status`；`expire_at`；`onboarding_json` |
| `async_paper` | `application_id` 唯一；`status`；`question_count`；`settings_json` |
| `async_question` | `(paper_id, seq_no)` 唯一；题目、准备/作答时长、权重 |
| `async_answer` | `question_id` 唯一；视频/音频 file id、转写、AI 分、人工分 |
| `async_answer_score` | `answer_id` 唯一；维度分、评语、model、prompt_version |

#### 简历与人才（recruit_resume）

| 表 | 关键字段/约束 |
|---|---|
| `person` | `user_id` 唯一可空；姓名、`phone_enc`+`phone_hash`、邮箱、城市、学历、工作年限、状态 |
| `resume` | `person_id`、`file_id`、`file_key`、`sha256` 唯一、渠道、解析状态、`structured_json`、`markdown_file_id`、版本 |
| `resume_version` | `(resume_id, version_no)` 唯一；parser/model 版本；结构化 JSON；不覆盖旧版本 |
| `resume_parse_task` | `task_no` 唯一；`ai_task_no`；status/stage/progress；错误；retry（≤3→死信） |
| `resume_chunk` | `(resume_id, chunk_no)` 唯一；chunk_type（正文/表格）、section、`es_doc_id` |
| `candidate_skill` | 简历/候选人、技能、等级、年限 |
| `tag` / `candidate_tag` | 标签 code/name/dimension；`(person_id, tag_id)` 唯一；source（HR/AI） |
| `talent_profile` | `person_id` 唯一；archive_level、summary、interview_history JSON、owner、next_followup_at |
| `talent_followup` | talent_id、HR、跟进内容、下次跟进时间 |
| `screening_rule` | job_id、rules_json、match_mode、enabled、priority |
| `match_result` | job/resume/版本、分项分、证据、缺口、`cache_key` 唯一、model/prompt |
| `resume_merge_record` | source/target person、相似度、状态、证据、操作人 |
| `job_snapshot` | `job_id` 主键；job version、title、requirements_json、status（由事件维护） |

#### 在线面试（recruit_room）

| 表 | 关键字段/约束 |
|---|---|
| `room` | `room_no` 唯一；`interview_id`；`livekit_room_name` 唯一；room_type；status；`max_participants`/`current_count`；录制状态 |
| `room_participant` | `(room_id, ref_id)` 唯一；角色（HOST/INTERVIEWER/CANDIDATE/OBSERVER）；WAITING/ADMITTED/JOINED/LEFT/KICKED；join/leave |
| `room_event` | room_id、事件类型、payload、occurred_at |
| `record_meta` | room_id、interview_id 快照、`egress_id`、file key、时长、状态（录制中/完成/失败） |
| `interview_note` | room_id、作者、内容、可见范围、时间（人工笔记） |
| `chat_message` | room_id、sender、内容、类型、可见范围、时间 |
| `transcript_segment` | `(room_id, speaker_id, segment_no)` 唯一；start/end_ms、text、confidence、source |
| `cheat_event` | room_id、candidate、事件类型、severity、证据 file id、occurred_at（只采集，不自动判定） |

#### 通知 / 文件 / AI

| 表 | 关键字段/约束 |
|---|---|
| `notification` | `msg_no` 唯一；receiver、channel、biz_type+biz_id、status、read_at |
| `notification_template` | `(template_code, channel, version)` 唯一；标题/内容模板、变量 |
| `notification_send_log` | notification_id、provider、request_id、结果、耗时 |
| `file_meta` | `file_key` 唯一；bucket、object_key、sha256、mime、size、biz_type+biz_id、status |
| `ai_task` | `task_no` 唯一；biz_type、status、progress、input/output JSON、model、retry、时间 |
| `prompt_version` | `(prompt_code, version)` 唯一；content、response_schema、model、status |
| `model_config` | 名称、base_url、model、参数；**不存密钥** |
| `ai_result` | task_id、result_json、confidence、evidence |
| `ai_call_log` | trace、capability、model、prompt version、tokens、latency、status |
| `human_review` | biz_type/id、reviewer、status、comment |
| `agent_trace` / `ai_tool_audit` | Agent 调用轨迹、工具白名单审计（可选增强） |
| `local_message` | `message_id` 唯一；event_type、exchange、routing_key、payload、status、retry |
| `idempotent_consumption` | `(consumer_group, event_id)` 唯一 |

### 2.3 核心 DDL 约定（示例）

```sql
CREATE TABLE iam_user (
  id            BIGINT UNSIGNED NOT NULL,
  username      VARCHAR(64)  NOT NULL,
  password_hash VARCHAR(100) NULL,
  user_type     VARCHAR(16)  NOT NULL,
  display_name  VARCHAR(64)  NOT NULL,
  status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
  token_version INT          NOT NULL DEFAULT 0,
  phone_enc     VARCHAR(255) NULL,
  phone_hash    CHAR(64)     NULL,
  dept_id       BIGINT UNSIGNED NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version       INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username),
  UNIQUE KEY uk_phone_hash (phone_hash),
  KEY idx_dept (dept_id)
);

CREATE TABLE schedule_slot (
  id               BIGINT UNSIGNED NOT NULL,
  participant_type VARCHAR(16) NOT NULL, -- INTERVIEWER/CANDIDATE/RESOURCE
  participant_id   BIGINT UNSIGNED NOT NULL,
  slot_date        DATE        NOT NULL,
  slot_no          TINYINT     NOT NULL, -- 0~47，30分钟/格
  interview_id     BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_slot (participant_type, participant_id, slot_date, slot_no),
  KEY idx_interview (interview_id)
);

CREATE TABLE room (
  id                BIGINT UNSIGNED NOT NULL,
  room_no           VARCHAR(32)  NOT NULL,
  interview_id      BIGINT UNSIGNED NOT NULL,
  livekit_room_name VARCHAR(128) NOT NULL,
  room_type         VARCHAR(24)  NOT NULL,
  status            VARCHAR(16)  NOT NULL,
  max_participants  TINYINT      NOT NULL DEFAULT 6,
  current_count     TINYINT      NOT NULL DEFAULT 0,
  created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  version           INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_room_no (room_no),
  UNIQUE KEY uk_livekit_room (livekit_room_name),
  KEY idx_interview (interview_id)
);
```

> 其余表在 W1 由本节清单 + Flyway `V1_0__init.sql` 落地；字段类型统一使用 `BIGINT UNSIGNED`、`VARCHAR`、`DATETIME(3)`、`JSON`，不使用 MySQL `ENUM`。

---

## 3. API 契约

### 3.1 通用约定

- 前缀：`/api/auth`、`/api/interview`、`/api/resume`、`/api/room`、`/api/ai`、`/api/notify`、`/api/file`；内部接口 `/internal/**` 不对外。
- 响应：`{"code":"OK","message":"success","data":{},"traceId":"...","timestamp":...}`；错误码如 `ITV3101`、`ROM4004`、`AIS5001`。
- 分页：`{list,total,pageNum,pageSize,pages}`；`pageSize ≤100`。
- ID 字符串；时间 ISO 8601；金额/年限按字段注释。
- 写操作支持 `Idempotency-Key`；更新带 `version`；非幂等 POST 不自动重试。
- 每个非公开接口必须登记权限码；菜单/按钮/API 三态一致。

### 3.2 端点清单（按域）

> 表中 `a/b` 表示同一资源的多个动作子路径；实际 OpenAPI 为每个动作单独定义，不用含 `|` 的路径。

#### auth / IAM

| 方法 | 路径 | 权限 |
|---|---|---|
| POST | `/api/auth/login`、`/api/auth/register`、`/api/auth/refresh`、`/api/auth/logout` | 公开/登录 |
| GET | `/api/auth/me`、`/api/auth/menus` | 登录 |
| GET | `/api/admin/users`、`/api/admin/roles`、`/api/admin/audit-logs`、`/api/admin/dicts` | `system:user:view` / `system:role:view` / `system:log:oper:view` / `system:dict:view` |
| POST/PUT/DELETE | `/api/admin/users/**`、`/api/admin/roles/**` | 对应权限码 |
| PUT | `/api/admin/roles/{id}/permissions`、`/api/admin/roles/{id}/menus` | `system:role:assign-menu` |
| GET | `/api/admin/permissions/tree` | `system:menu:view` |
| PUT | `/api/admin/users/{id}/roles` | `system:user:assign-role` |

> 权限码体系沿用 `system:*`（IAM 域），不另造 `iam:*`，避免与旧文档混用。

#### interview

| 方法 | 路径 | 权限 |
|---|---|---|
| GET/POST/PUT/DELETE | `/api/interview/jobs/**` | `interview:job:*` |
| POST | `/api/interview/jobs/{id}/publish`、`/close` | `interview:job:publish/close` |
| GET/POST/PUT/DELETE | `/api/interview/process-templates/**` | `interview:process:*` |
| GET/POST | `/api/interview/applications`、`/applications/{id}` | `interview:application:view/create` |
| GET | `/api/interview/applications/{id}/timeline` | `interview:application:view` |
| POST | `/api/interview/applications/{id}/actions` | 按动作校验 |
| GET/POST | `/api/interview/portal/applications`、`/portal/jobs` | `portal:application:*` / 公开 |
| GET/POST/PUT | `/api/interview/schedules/**`、`/calendar` | `interview:schedule:*` |
| POST | `/api/interview/schedules/conflict-check`、`/batch` | `interview:schedule:view/create` |
| GET/POST | `/api/interview/evaluation-templates/**` | `interview:evaluation-template:*` |
| GET/POST | `/api/interview/schedules/{id}/evaluations`、`/evaluations/my` | `interview:evaluation:*` |
| GET | `/api/interview/schedules/{id}/evaluations/comparison`、`/ai-draft` | `interview:evaluation:view` |
| GET/POST | `/api/interview/offers/**`、`/portal/offers/**` | `interview:offer:*` / `portal:offer:*` |
| GET | `/api/interview/stats/funnel`、`/workload`、`/time-to-hire` | `interview:stats:view` |

#### resume

| 方法 | 路径 | 权限 |
|---|---|---|
| GET/POST/PUT/DELETE | `/api/resume/candidates/**` | `resume:candidate:*` |
| POST | `/api/resume/candidates/{id}/merge` | `resume:candidate:merge` |
| POST | `/api/resume/resumes:batch-upload`、`/api/resume/resumes` | `resume:resume:upload` |
| GET | `/api/resume/resumes`、`/{id}`、`/{id}/parse-status`、`/{id}/markdown` | `resume:resume:view` |
| POST | `/api/resume/resumes/{id}/retry-parse` | `resume:resume:retry-parse` |
| PUT | `/api/resume/resumes/{id}/structured` | `resume:resume:edit-structured` |
| POST | `/api/resume/search`、`/search/talent` | `resume:search:execute` |
| POST | `/api/resume/matches` | `resume:match:run` |
| GET | `/api/resume/matches/{jobId}`、`/matches/detail/{id}` | `resume:match:view` |
| GET/POST/PUT/DELETE | `/api/resume/tags/**`、`/talents/**` | `resume:tag:*` / `resume:talent:*` |
| POST | `/api/resume/screening-rules/{id}/run` | `resume:screening-rule:run` |
| GET | `/api/resume/tasks`、`/tasks/{id}` | `resume:task:view` |

#### room / media

| 方法 | 路径 | 权限 |
|---|---|---|
| POST | `/internal/rooms` | 内部 HMAC |
| GET | `/api/room/rooms`、`/rooms/{roomNo}`、`/rooms/{roomNo}/participants` | `room:room:view` / 参会人 |
| POST | `/api/room/rooms/{roomNo}/open`、`/join-request`、`/token`、`/ws-ticket`、`/ai-token` | 参会人/HOST |
| POST | `/api/room/rooms/{roomNo}/admit/reject/mute/kick/end/cancel` | HOST / `room:room:*` |
| GET/POST | `/api/room/rooms/{roomNo}/messages`、`/notes`、`/transcript` | 参会人 / `room:note:*` |
| POST | `/api/room/rooms/{roomNo}/recordings/start`、`/recordings/stop` | `room:recording:start/stop` |
| GET | `/api/room/rooms/{roomNo}/recordings`、`/recordings/{id}/play`、`/recordings/{id}/download` | `room:recording:*` |
| POST | `/api/room/rooms/{roomNo}/cheat-events`、`/face-verify`、`/snapshot` | 参会人/候选人 |
| GET/POST | `/api/room/async-interviews/**`、`/portal/async-invites/**` | `room:async:*` / `portal:async:*` |
| GET/POST | `/api/room/questions/**` | `room:question:*` |
| POST | `/internal/livekit/webhook`、`/internal/rooms/{roomNo}/transcript`、`/ai-callback` | 内部 HMAC/LiveKit 签名 |

#### AI / notify / file

| 方法 | 路径 | 权限 |
|---|---|---|
| POST | `/api/ai/recorded-interviews`、`/answers` | `portal:async:*` / 面试官 |
| POST | `/api/ai/interviews/{id}/hints`（SSE） | `interview:evaluation:view` |
| GET | `/api/ai/transcripts/{interviewId}` | `room:transcript:view` |
| POST | `/api/ai/resumes/{id}/re-extract` | `resume:resume:retry-parse` |
| GET | `/api/ai/health` | 运维/内网 |
| GET/PUT | `/api/notify/messages/**`、`/templates/**` | 登录 / `system:notice:*` |
| POST | `/api/file/presign/upload`、`/complete`、`/multipart/**` | 登录 |
| GET | `/api/file/{id}`、`/{id}/url` | 登录 / `file:download` |
| POST | `/internal/files/register` | 内部 HMAC |
| POST | `/api/ai/v1/asr/{sessionId}`（WSS） | 房间 `scope=asr` 令牌 |

### 3.3 错误码

| 区间 | 含义 | 示例 |
|---|---|---|
| `AUTH1xxx` | 认证/令牌 | `AUTH1001` 账密错误、`AUTH1002` Refresh 无效、`AUTH1101` 权限不足 |
| `RSM2xxx` | 简历 | `RSM2001` 简历不存在、`RSM2101` 解析失败、`RSM2201` 合并目标非法 |
| `ITV3xxx` | 面试 | `ITV3001` 投递不存在、`ITV3101` 排期冲突、`ITV3102` 状态流转非法 |
| `ROM4xxx` | 房间 | `ROM4001` 房间不存在、`ROM4003` 未到进场时间、`ROM4004` 名额已满、`ROM4005` 不在名单 |
| `AIS5xxx` | AI | `AIS5001` 模型超时、`AIS5002` 熔断降级、`AIS5102` 作答已提交 |
| `NTF6xxx` / `FIL7xxx` / `GW9xxx` | 通知/文件/网关 | `GW9001` 未认证、`GW9003` 限流、`GW9005` 内部接口禁止外访 |

---

## 4. 鉴权与 RBAC

### 4.1 五角色

`SUPER_ADMIN`（系统维护）、`HR_ADMIN`（招聘管理员）、`RECRUITER`（招聘专员）、`INTERVIEWER`（面试官）、`CANDIDATE`（候选人）。

- `SUPER_ADMIN` 受保护，不能删除最后一个系统管理员。
- `HR_ADMIN`/`RECRUITER` 的数据范围分别为 ALL / DEPARTMENT；`INTERVIEWER` 默认 ASSIGNED（只看被安排场次）；`CANDIDATE` 只能访问自己的数据。
- 候选人接口统一走 `/api/*/portal/**`、`/api/offer-portal/**`；网关做入口隔离，服务内再做对象级校验。

### 4.2 权限三态与数据范围

1. **菜单**：能否看到入口；`/api/auth/menus` 返回树。
2. **按钮/API 权限码**：能否调用接口；服务端 `@PreAuthorize` 或等效授权器校验。
3. **数据范围**：能看到哪些数据；`ALL / DEPARTMENT / ASSIGNED / SELF`，通过 `@OwnerCheck`/查询条件落实。

> 前端隐藏按钮只是体验，不是安全边界。

### 4.3 Token 与 Redis Key

- Access 2h + Refresh 7d；Refresh 旋转；`token_version` 撤销。
- Refresh 建议 `HttpOnly + Secure + SameSite=Strict` Cookie；若走响应体，前端不落 localStorage，并配置 CSP。
- Redis Key：

| Key | 用途 |
|---|---|
| `auth:refresh:{hash}` | Refresh 会话 |
| `auth:refresh:family:{familyId}` | 重放检测 |
| `auth:bl:{jti}` | Access 黑名单 |
| `auth:tv:{userId}` | token version |
| `auth:permver:{userId}` | 权限版本 |
| `auth:perm:{userId}:{ver}` | 角色+权限缓存 |
| `auth:rate:{scope}:{key}` | 限流 |

### 4.4 权限码（按域）

| 域 | 权限码 |
|---|---|
| IAM | `system:user:view/create/update/delete/reset-password/assign-role/kick`、`system:role:view/create/update/delete/assign-menu`、`system:menu:view/create/update/delete`、`system:dept:view/create/update/delete`、`system:dict:view/update`、`system:config:view/update`、`system:log:login:view`、`system:log:oper:view`、`system:notice:view/update` |
| 面试 | `interview:job:view/create/update/delete/publish/close`、`interview:process:view/create/update/delete`、`interview:application:view/create/update/withdraw/reject/transfer`、`interview:schedule:view/create/update/cancel/batch`、`interview:evaluation-template:view/create/update/delete`、`interview:evaluation:view/submit`、`interview:offer:view/create/approve/send/withdraw`、`interview:stats:view` |
| 简历人才 | `resume:candidate:view/create/update/delete/merge/export`、`resume:resume:view/upload/delete/retry-parse/edit-structured/download`、`resume:tag:view/create/update/delete/assign`、`resume:screening-rule:view/create/update/delete/run`、`resume:match:view/run`、`resume:search:execute`、`resume:talent:view/create/update/delete/follow/export`、`resume:portrait:view/generate`、`resume:task:view/retry` |
| 在线面试 | `room:room:view/create/update/cancel/end/admit/kick/mute`、`room:recording:view/start/stop/play/download`、`room:note:view/create`、`room:transcript:view`、`room:cheat:view`、`room:monitor:view`、`room:async:view/create/update/publish/delete`、`room:async-invite:view/create/cancel`、`room:async-review:view/submit`、`room:question:view/create/update/delete` |
| 候选人门户 | `portal:job:view`、`portal:resume:view/upload`、`portal:application:view/create/withdraw`、`portal:interview:view/join`、`portal:async:view/start/submit`、`portal:offer:view/respond`、`portal:message:view` |
| AI/文件 | `ai:task:view/retry`、`ai:prompt:view/update`、`file:upload/download/delete` |

### 4.5 必测鉴权用例

- 候选人看他人简历/结果/录像 → 403。
- 面试官访问未分配场次 → 403。
- 前端隐藏按钮后直调 API → 403。
- 管理员取消角色授权后，权限在缓存版本更新后失效。
- 禁用用户/过期令牌/撤销 Refresh 均不能新建会话。
- 文件/录像仅短期签名 URL，下载有审计。

---

## 5. 事件与一致性

### 5.1 事件信封

```json
{
  "eventId": "uuid",
  "eventType": "interview.scheduled",
  "eventVersion": 1,
  "occurredAt": "2026-11-02T02:00:00Z",
  "traceId": "…",
  "aggregateId": "applicationId",
  "payload": { "只带 ID 与必要快照" }
}
```

### 5.2 关键事件

| 事件 | 发布方 → 订阅方 | 用途 |
|---|---|---|
| `resume.received` / `resume.parse.completed` / `resume.parse.failed` | resume | 解析流水线 |
| `candidate.duplicated` | resume → notify | 查重提醒 |
| `job.published` / `job.updated` | interview → resume | 更新 JD 快照/匹配缓存 |
| `interview.scheduled` / `interview.canceled` | interview → room/notify | 建房/关房/邀约 |
| `interview.result.submitted` | interview → notify/ai | 结果通知/画像 |
| `room.finished` | room → ai/interview | 纪要/评价草稿（转写就绪） |
| `recording.completed` | room → interview/ai | 录像登记；可选补转写 |
| `offer.confirmed` | interview → notify/resume | 人才库状态 |
| `talent.followup.due` | resume → notify | 回访提醒 |
| `ai.task.failed` | ai → 业务服务 | 标记失败可重试 |
| `notification.requested` | 各服务 → notify | 统一通知请求 |

### 5.3 一致性规则

- 本地消息表 + RabbitMQ publisher confirms + 手动 ack + 死信 + 幂等消费；不引入 Seata。
- 排期：`schedule_slot` 唯一索引兜底；预检与写入同库同事务；取消物理删 slot + interview 软删。
- **时间粒度**：面试起止必须对齐 30 分钟；如允许任意分钟，预检/写槽使用统一取整规则并写进 API。
- 建房：interview 写 `ROOM_PENDING` → `interview.scheduled` → room 建房回执；失败重试，超阈值人工处理。
- 名额：`current_count` 原子更新；token 签发幂等（room+user）；未加入 token 预留 TTL；离会/房间结束/Webhook 归还；定时用 LiveKit `ListParticipants` 对账。
- AI 结果：草稿 + 人工确认后写正式状态；失败/超时可重试。

---

## 6. AI 服务

### 6.1 路由与边界

```
Java 业务 → ai-service（FastAPI；鉴权/配额/Prompt/降级/审计）
  → vLLM（主，Qwen2.5-7B-AWQ）→ 云端 LLM（备）→ Ollama（无 GPU）→ 模板
  → RabbitMQ → ai-worker（FunASR/BGE）
  → RabbitMQ → mineru-worker（MinerU）
```

- ai-service 不持有招聘业务主表；结果通过内部 API/事件回写。
- 所有模型调用统一 OpenAI 兼容客户端；换供应商只改 `base-url` + `model`。
- 结构化输出用 JSON Schema + 校验；失败修复一次，再失败标记任务失败。
- 简历/转写按不可信数据定界，防 Prompt Injection；AI 分数只作建议，人工确认。
- 不推断年龄/性别/婚育等岗位无关属性。

### 6.2 任务

| 任务 | 触发 | 输出 |
|---|---|---|
| 简历解析 | `resume.received` | Markdown + 结构化 JSON + chunk/embedding |
| 实体抽取 | `resume.parse.completed` | 教育/经历/技能/年限 + 置信度 |
| 人岗匹配 | resume 服务调用 | 分项分 + 证据 + 缺口 |
| 面试纪要/评价草稿 | `room.finished`（转写就绪） | 摘要 + 证据 + 建议分 + 待确认项 |
| 录播评分 | 作答齐 | 逐题分 + 证据 |
| 实时问题提示 | 转写新增 | 2~3 条追问；仅面试官可见 |

### 6.3 简历流水线

1. 上传 → file 校验/对象存储 → `resume_parse_task(PENDING)` + `resume.received`。
2. mineru-worker 解析 PDF/Word/图片 → Markdown/JSON/表格/公式/图注，写 MinIO。
3. 分块：300~500 token/块；按标题/语义切；**表格整体独立成块**，超长按行分组且重复表头；每块前缀注入标题路径；工作经历尽量保持公司+职位+时间+职责同块。
4. BGE 向量化 → ES `resume_vec`；resume 服务校验版本后写入/更新。
5. LLM 结构化抽取 → Schema 校验 → 低置信字段标“待人工确认” → MySQL。
6. kNN 自查去重 → 只提示，人工确认合并；保留旧版本。

### 6.4 ES 索引

- 索引 `resume_vec`：512 维（bge-small-zh-v1.5），`doc_type=resume|jd`；`emb_model` 版本字段。
- HNSW 参数必须在 `index_options`：

```json
"embedding": {
  "type": "dense_vector",
  "dims": 512,
  "index": true,
  "similarity": "cosine",
  "index_options": { "type": "hnsw", "m": 16, "ef_construction": 200 }
}
```

- 混合检索：`multi_match` BM25 + `knn` + RRF；保存检索参数/模型版本/人工反馈。
- 切换 BGE-M3（1024 维）时新建索引 + 别名切换，不混存不同维度。

### 6.5 人岗匹配

```
规则硬过滤（学历/年限/地点） → 混合召回 top50 → LLM 精排 top20
→ final = 0.3×召回分 + 0.7×LLM 分
```

- 五维：技能/经验/学历/稳定性/潜力；每维给证据；必备条件未命中封顶。
- 缓存键：job/resume 版本 + prompt 版本 + model；结果可解释、可覆盖。

### 6.6 纪要与异步评分

- 纪要触发：`room.finished` + 转写就绪；录像可选；无转写时 `INSUFFICIENT_TRANSCRIPT`。
- 输出：摘要、关键问答、证据引用、维度建议分、风险/待确认；面试官确认后才归档。
- 异步面试：逐题 ASR + 相关性/结构/深度/表达/专业性评分；人工复核可覆盖。

---

## 7. ASR 与在线面试

### 7.1 ASR WSS

- 令牌：room-service 签发 `scope=asr` 5 分钟 JWT（room/user/role）；浏览器连接
  `WSS /api/ai/v1/asr/{sessionId}?token=...`；Nginx rewrite 到 ai-worker `/ws/asr/{sessionId}`。
- 音频：每位参会人浏览器本地麦克风，16kHz/16bit/单声道 PCM，100~200ms/帧；1v5=6 路，1v1=2 路；不抓 LiveKit 混音轨。
- 协议：

```json
{"type":"start","roomId":"...","role":"CANDIDATE","sampleRate":16000,"channels":1}
// 二进制 Int16LE PCM 帧
{"type":"stop"}
// 服务端
{"type":"partial","text":"...","startMs":12000,"endMs":14500}
{"type":"final","segmentId":"...","text":"...","startMs":12000,"endMs":15000,"confidence":0.91}
{"type":"error","code":"ASR_OVERLOAD","message":"转写繁忙，已暂停实时字幕"}
```

- partial 不落库；final 经 ai-service → room-service 写 `transcript_segment`，按 `(room_id,speaker_id,segment_no)` 幂等；断线重连带 `lastSegmentId`。

### 7.2 容量契约

| 场景 | 音频流 | 实时字幕 |
|---|---:|---|
| 1×1v1 | 2 | 全量 |
| 1×1v5 | 6 | 全量 |
| 冒烟 2×1v1+1×1v5 | 10 | 全量 |
| 基线 5×1v1+5×1v5 | 40 | 前 20 路；其余降级 |
| 扩展 10+10 | 80 | 前 20 路；其余降级 |

- 单 ai-worker 目标 ≥20 路；多实例水平扩展；W1 压测校准。
- 超限 `ASR_OVERLOAD`：暂停实时字幕、会后补转写；面试/录制/评价不阻断。
- 要求基线全量实时 → 扩容并重测，不能沿用 20 路目标。

### 7.3 房间与录制

- 状态：`CREATED → WAITING → LIVE → FINISHED`（异常 `CANCELED/FAILED`）；1v1 上限 2，1v5 上限 6。
- 入会：`POST /api/room/rooms/{roomNo}/token`；校验登录、时间窗、名单、状态、名额；LiveKit token 只授指定 room+identity+能力。
- 信令：优先 `livekit.<domain>`；同域 `/livekit` 必须 rewrite 到 `/rtc`；UDP 直暴露。
- 录制：Egress 合成 MP4 → MinIO；`record_meta` 记录；失败不阻塞面试，可重试；回放需鉴权；保留 90 天。
- 笔记：`interview_note` 支持实时记录/编辑/查询；AI 纪要只作补充。
- 聊天：`chat_message` 落库后广播；支持离线拉取。
- 防作弊：`cheat_event` 只采集展示，不自动判定；人脸/虚拟背景/白板按 P2 处理。

---

## 8. 前端

- 单 SPA + 后端动态菜单驱动路由；登录后拉 `/api/auth/menus`，`import.meta.glob` 映射组件，按钮用 `v-perm`。
- 页面：候选人（岗位/投递/简历/面试/Offer/异步）、面试官（日程/房间/评分/回放）、HR（看板/岗位/流程/排期/评价/Offer/人才库）、管理员（用户/角色/权限/审计）。
- LiveKit：`livekit-client`；预检设备、1v5 网格、Simulcast 低轨、屏幕共享、断线重连、弱网提示；聊天/字幕走业务 WS。
- 上传：file-service 预签名/分片；解析状态轮询；任务失败可重试。

---

## 9. 部署与验收

### 9.1 部署

- Compose：`base.yml`（Nacos/MySQL/Redis/RabbitMQ/ES/MinIO）+ `app.yml`（7 Java + ai-service + workers + LiveKit/Egress + Nginx）+ `gpu.yml`（vLLM）。
- 端口：仅 Nginx 80/443 公网；LiveKit 7880/7881 + UDP 50000-50100；管理/DB/ES/MinIO/vLLM 内网。
- 硬件：应用节点 16C64G 推荐（最低 32GB）+ GPU 节点 24GB；ES 堆 2~4G；Egress 录制单独计 CPU/带宽。
- 密钥：Nacos 加密/环境变量；`.env` 不入库；演示数据全部虚构。

### 9.2 验收

| 项 | 口径 |
|---|---|
| 鉴权 | 五角色；越权/隐藏按钮直调/禁用用户/取消授权全部 403/401 |
| 面试流程 | 申请→一面→评价→下一阶段；三方冲突拦截；Offer 确认 |
| 简历 | PDF/Word/图片各 ≥3 份，≥9 成解析；表格保留行列；失败可重试；重复合并 |
| 匹配 | Top5 命中率 ≥80%；F1 ≥85%；消融对比 |
| 在线面试 | 2×1v1+1×1v5 稳定 10 分钟；未邀请/超员被拒；断线重连 |
| 录制 | 结束后 ≤2 分钟可回放；鉴权；失败可重试 |
| 实时字幕 | 冒烟 10 路全量；基线 40 路按 20 路契约 |
| AI 故障 | 停 ai-worker/vLLM/ES，主业务继续；任务排队/可重试 |
| 容量 | HTTP P95 <300~500ms；WebRTC 基线 5+5；扩展 10+10 如实报告 |

### 9.3 W1 冻结清单

1. 表名/字段映射 + Flyway V1 通过；本文第 2 节为契约。
2. OpenAPI：auth/menus、interview schedules/conflicts、resume upload/parse、room join-token、ASR token、notes、AI summary。
3. 事件 Schema：`room.finished`、`recording.completed`、`interview.scheduled`、`job.published/updated`、`notification.requested`。
4. vLLM/ASR 并发、LiveKit 1v5 + 跨网 + 录制 spike。
5. 口径：1v5=6 人、五角色、权限码注册表、Refresh Redis Key。

> 待确认：GPU 可用性与 vLLM 显存、1v5 人数口径、FunASR CER、异步/Offer 考点、备份盘。GPU/显存验证完成前不宣称“最终容量达标”；切云主档只改 base-url。

---

## 10. 需求追踪摘要

| 子系统 | 核心需求 | 落地位置 | 验收 |
|---|---|---|---|
| 面试管理 | 自定义流程/状态机/排期三方冲突/通知/评价模板/Offer/AI 纪要 | `interview` + `notify` + `ai-service` | 申请→一面→评价→下一阶段；冲突 409；Offer 确认 |
| 简历人才 | 多渠道/解析/表格保留/结构化/查重去重/匹配/规则/标签/人才库/跟进/画像 | `resume` + `file` + `ai-service` + ES | 三格式解析；表格块可检索；重复合并；Top5 命中 |
| 在线面试 | LiveKit 1v1/1v5、屏幕共享、聊天、笔记、录制回放、实时字幕、AI 评价、异步面试、防作弊 | `room` + `ai-service` + LiveKit/Egress | 2+6 人稳定；录制可回放；字幕按 20 路契约；异步评分 |
| 平台治理 | 统一登录、五角色、菜单/按钮/API 权限码、数据范围、审计 | `auth` + `gateway` | 角色勾选即时生效；隐藏按钮直调 403；候选人隔离 |

> 被裁剪/降级项（Offer 审批、异步录播、批量排期、邮箱收简历、人才画像、人脸核验、虚拟背景/白板等）需在需求评审时明确 P0/P1/P2；实时字幕按 20 路容量契约降级。
