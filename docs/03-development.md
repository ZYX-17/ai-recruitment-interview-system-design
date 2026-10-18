# 招聘面试系统开发文档（v3.0 · 契约与实现索引）

> 配套文档：《2.架构设计方案-招聘面试系统微服务架构(2).md》（v3.0）  
> 定位：把 v3.0 架构落成可并行的工程契约；**完整 DDL/接口细节**以 `04-detailed-design/` 为参考，本文负责统一 v3.0 的服务边界、表名、事件、AI/ASR/媒体契约与验收口径。  
> W1 任务：把本文与 `04-detailed-design/` 的表名/字段做一次映射，冻结后写入 Flyway 与 OpenAPI；禁止三套命名混用。

---

## 1. v3.0 相对 v2.x 的工程变更

| 变更 | v3.0 口径 | 影响 |
|---|---|---|
| 主推理引擎 | **vLLM Qwen2.5-7B-Instruct-AWQ**，24GB GPU；云端 LLM 备用、Ollama 无 GPU 档、Mock 开发档 | ai-service 只改 base-url/model；业务代码不变 |
| AI 编排 | **Python 3.11 + FastAPI + openai SDK** | Java 业务通过 OpenAPI/Feign 调用；不再有 Java 版 ai-service |
| 服务数量 | 7 个 Java + ai-service（Python）+ ai-worker + mineru-worker | 见第 2 节 |
| 硬件 | 应用节点 16C64G 推荐/32G 最低；GPU 节点 24GB | 旧 4C8G 口径废除 |
| ASR | 参会人浏览器麦克风 → WSS 直达 ai-worker；每人一条流；单实例目标 ≥20 路 | 见第 6 节容量契约 |
| 纪要触发 | `room.finished` + 转写就绪；录像可选 | 录制失败不再阻断纪要 |
| 面试笔记 | 新增 `interview_note` | 原始在线面试需求补齐 |
| 角色 | 五角色：SUPER_ADMIN / HR_ADMIN / RECRUITER / INTERVIEWER / CANDIDATE | 前后端/种子统一 |
| 分工 | notify 移交 BE-B | BE-A 4 服务 + 集成；BE-B 4 Java + 3 Python + 部署 |

> 本文不展开 GPU 可用性与 vLLM 显存压测（按 v3.0 待确认项 5 另行验证）；但所有契约按“vLLM 主档”编写，切云主档只改配置。

---

## 2. 服务与数据所有权

| 服务 | 语言 | schema | 拥有表（v3.0 统一命名） |
|---|---|---|---|
| gateway | Java | 无 | 无业务表 |
| auth (IAM) | Java | `recruit_auth` | `iam_user`、`iam_role`、`iam_permission`、`iam_user_role`、`iam_role_permission`、`iam_data_scope`、`iam_department`、`iam_refresh_session`、`iam_audit_log` |
| interview | Java | `recruit_interview` | `job`、`job_stage_template`、`application`、`application_stage_history`、`interview`、`schedule_slot`、`evaluation`、`evaluation_dimension`、`interview_minutes`、`offer`、`async_paper`、`async_question`、`async_answer`、`async_answer_score` |
| room | Java | `recruit_room` | `room`、`room_participant`、`room_event`、`record_meta`、`interview_note`、`chat_message`、`transcript_segment`、`cheat_event` |
| resume | Java | `recruit_resume` | `person`、`resume`、`resume_version`、`resume_parse_task`、`resume_chunk`、`candidate_skill`、`candidate_tag`、`tag`、`talent_profile`、`talent_followup`、`screening_rule`、`match_result`、`resume_merge_record`、`job_snapshot` |
| notify | Java | `recruit_notify` | `notification`、`notification_template`、`notification_send_log` |
| file | Java | `recruit_file` | `file_meta` |
| ai-service | **Python/FastAPI** | `recruit_ai` | `ai_task`、`prompt_version`、`model_config`、`ai_result`、`ai_call_log`、`human_review`、`agent_trace`、`ai_tool_audit` |
| ai-worker | Python | 无业务主表 | ASR/Embedding 无状态 |
| mineru-worker | Python | 无业务主表 | MinerU 无状态 |

规则：

- 只能写自己 schema；跨服务只存 ID + 快照，禁止跨库 JOIN/跨库写。
- `application`/`job` 归 interview（v3.0 保持 v2 的干净边界，不采用 qyf 方案里 application 与 stage 分属两服务的写法）。
- `resume` 做匹配需要 JD 时，消费 `JobPublished`/`JobUpdated` 事件保存 `job_snapshot`，不直连 interview 表。
- 每个 schema 附 `local_message`（发件箱）与 `idempotent_consumption`（消费幂等）。
- 雪花 ID 字符串化；时间 UTC 存储、ISO 8601 输出；枚举 VARCHAR(32)。

---

## 3. API 与鉴权契约

### 3.1 通用约定

- 前缀沿用 v2：`/api/auth`、`/api/interview`、`/api/resume`、`/api/room`、`/api/ai`、`/api/notify`、`/api/file`；内部接口 `/internal/**` 不对外。
- 响应：`{"code":"OK","message":"success","data":{},"traceId":"...","timestamp":...}`；错误码如 `ITV3101`、`ROM4004`、`AIS5001`，**成功统一字符串 `OK`**（不再混用数字 0；`04-detailed-design/` 示例中的 `0` 在 v3.0 统一视为 `OK`）。
- 分页：`{list,total,pageNum,pageSize,pages}`；`pageSize ≤100`。
- 写操作支持 `Idempotency-Key`；更新带 `version` 乐观锁；时间 ISO-8601。
- 所有非公开接口必须登记权限码；菜单/按钮/API 三态一致。

### 3.2 鉴权

- 五角色；`SUPER_ADMIN` 保护，不可删除最后一个系统管理员。
- Access 2h / Refresh 7d；Refresh 旋转，Redis Key：`auth:refresh:{hash}`、`auth:refresh:family:{familyId}`、`auth:bl:{jti}`、`auth:tv:{userId}`。
- 网关验签 + 角色粗校验；各业务服务**再次验签**并做权限码/数据范围/对象归属校验。
- 候选人只能访问 `/api/candidate/**` 或 `/api/offer-portal/**` 等候选人入口；越权返回 403。
- Refresh Token 建议 HttpOnly+Secure+SameSite Cookie；如走响应体，前端不落 localStorage，并配置 CSP。
- 必测：候选人看他人简历/录像、面试官看未分配场次、前端隐藏按钮直调 API、禁用用户旧 token、取消授权后的权限失效。

### 3.3 关键接口（完整清单见 `04-detailed-design/05-api.md`，v3.0 增补如下）

| 领域 | 关键接口 |
|---|---|
| auth | `POST /api/auth/login`、`POST /api/auth/refresh`、`POST /api/auth/logout`、`GET /api/auth/me`、`GET /api/auth/menus`、`PUT /api/admin/roles/{id}/permissions` |
| interview | `POST /api/interview/applications`、`POST /api/interview/applications/{id}/transitions`、`POST /api/interview/schedules`、`GET /api/interview/schedules/conflict-check`、`POST /api/interview/applications/{id}/evaluations`、`POST /api/interview/offers` |
| resume | `POST /api/resume/resumes:batch-upload`、`GET /api/resume/resumes/{id}/parse-status`、`GET /api/resume/resumes`、`POST /api/resume/jobs/{jobId}/hard-filter`、`POST /api/resume/resumes/{id}/merge`、`GET /api/resume/talents/search` |
| room | `POST /api/room/rooms/{roomId}/join-token`、`POST /api/room/rooms/{roomId}/finish`、`GET /api/room/rooms/{roomId}/recordings`、`POST /internal/media/livekit/webhook` |
| AI/ASR | `POST /api/room/rooms/{roomId}/ai-token`、`WSS /api/ai/v1/asr/{sessionId}`、`GET /api/ai/transcripts/{interviewId}`、`POST /api/ai/interviews/{interviewId}/summary-tasks` |
| 笔记 | `POST/GET /api/room/rooms/{roomId}/notes` |
| 通知/文件 | `/api/notify/**`、`POST /api/file/presign/upload`、`POST /api/file/validate` |

> v3.0 的 `room.finished` 是内部事件，不暴露 REST；房间结束仍用 `POST /api/room/rooms/{roomId}/finish`。

---

## 4. 事件与一致性

### 4.1 事件清单（v3.0 增补 room.finished）

| 事件 | 发布方 → 订阅方 | 说明 |
|---|---|---|
| resume.received / parse.completed / parse.failed | resume | 解析流水线 |
| candidate.duplicated | resume → notify | 查重提醒 |
| job.published / job.updated | interview → resume | 更新 JD 快照/匹配缓存；关闭岗位停止推荐 |
| interview.scheduled / canceled | interview → room/notify | 建房/关房/邀约 |
| interview.result.submitted | interview → notify/ai | 结果通知/画像 |
| **room.finished** | room → ai/interview | 纪要/评价草稿触发；带 `transcriptStatus` |
| recording.completed | room → interview/ai | 录像登记；可选补转写 |
| offer.confirmed | interview → notify/resume | 人才库状态 |
| talent.followup.due | resume → notify | 回访提醒 |
| ai.task.failed | ai → 业务服务 | 标记失败可重试 |
| notification.requested | 各服务 → notify | 统一通知请求 |

### 4.2 一致性

- 本地消息表 + RabbitMQ publisher confirms + 手动 ack + 死信 + 幂等消费；不引入 Seata。
- 排期：`schedule_slot` 唯一索引兜底；预检与写入同库同事务；取消物理删 slot + interview 软删。
- **时间粒度**：面试起止必须对齐 30 分钟；如允许任意分钟，预检/写槽使用统一取整规则并写进 API。
- 建房：interview 写 `ROOM_PENDING` → `interview.scheduled` → room 建房回执；失败重试，超阈值人工处理。
- 房间名额：`current_count` 原子更新；token 签发幂等（room+user）；未加入的 token 预留 TTL；离会/房间结束/Webhook 归还；定时用 LiveKit `ListParticipants` 对账。
- AI 结果：先草稿，人工确认后写正式状态；失败/超时可重试。

---

## 5. AI 服务契约（Python/FastAPI）

### 5.1 架构与路由

```
Java 业务 → ai-service（FastAPI；鉴权/配额/Prompt/降级/审计）
  → vLLM（主，Qwen2.5-7B-AWQ）→ 云端 LLM（备）→ Ollama（无 GPU）→ 模板
  → RabbitMQ → ai-worker（FunASR/BGE）
  → RabbitMQ → mineru-worker（MinerU）
```

- ai-service 不持有招聘业务主表；结果通过内部 API/事件回写。
- 所有模型调用统一 OpenAI 兼容客户端；换供应商只改 `base-url` + `model`。
- 结构化输出使用 JSON Schema + 校验；失败修复一次，再失败标记任务失败。
- 不把菜单/权限树塞进 Prompt；简历/转写按不可信数据定界，防 Prompt Injection。
- AI 分数只作建议，人工确认；不推断年龄/性别/婚育等岗位无关属性。

### 5.2 关键任务

| 任务 | 触发 | 输出 |
|---|---|---|
| 简历解析 | `resume.received` | Markdown + 结构化 JSON + chunk/embedding |
| 实体抽取 | `resume.parse.completed` | 教育/经历/技能/年限 + 置信度 |
| 人岗匹配 | resume Feign/异步 | 分项分 + 证据 + 缺口 |
| 面试纪要/评价草稿 | `room.finished`（转写就绪） | 摘要 + 证据 + 建议分 + 待确认项 |
| 录播评分 | 作答齐 | 逐题分 + 证据 |
| 实时问题提示 | 转写新增 | 2~3 条追问；仅面试官可见 |

### 5.3 纪要触发

- 主触发：`room.finished` + 转写就绪（实时转写或会后补转写）。
- `recording.completed` 只用于录像资产登记与可选补转写。
- 无录像/未同意录制/录制失败：仍可用转写文本生成草稿；两者都无 → `INSUFFICIENT_TRANSCRIPT`，提示人工填写，不阻塞面试结束。

---

## 6. ASR WSS 契约

### 6.1 连接与令牌

- room-service 签发 `scope=asr` 短时 JWT（5 分钟），绑定 `roomId/userId/role`；浏览器连接：

```
WSS /api/ai/v1/asr/{sessionId}?token={asrToken}
```

- Nginx 直达 ai-worker（不进 gateway），并把 `/api/ai/v1/asr/{sessionId}` rewrite 到 ai-worker `/ws/asr/{sessionId}`；ai-worker 校验令牌、房间成员与角色。
- 每条流独立；每人一条；1v5=6 路，1v1=2 路；不抓 LiveKit 混音轨。

### 6.2 协议

```json
// 客户端首帧
{"type":"start","roomId":"...","role":"CANDIDATE","sampleRate":16000,"channels":1}
// 二进制：Int16LE PCM，100~200ms/帧
// 客户端尾帧
{"type":"stop"}
// 服务端
{"type":"partial","text":"...","startMs":12000,"endMs":14500}
{"type":"final","segmentId":"...","text":"...","startMs":12000,"endMs":15000,"confidence":0.91}
{"type":"error","code":"ASR_OVERLOAD","message":"转写繁忙，已暂停实时字幕"}
```

- partial 不落库；final 经 ai-service → room-service 写 `transcript_segment`，按 `(room_id, speaker_id, segment_no)` 幂等。
- 断线重连带 `lastSegmentId`；无法续传则新开流并标记断点；字幕不回补。
- 建议参会人戴耳机，减少扬声器被本地麦克风二次采集。

### 6.3 容量契约

| 场景 | 音频流 | 实时字幕 |
|---|---:|---|
| 1×1v1 | 2 | 全量 |
| 1×1v5 | 6 | 全量 |
| 冒烟 2×1v1+1×1v5 | 10 | 全量 |
| 基线 5×1v1+5×1v5 | 40 | 前 20 路；其余降级 |
| 扩展 10+10 | 80 | 前 20 路；其余降级 |

- 单 ai-worker 实例目标 ≥20 路；W1 压测校准；多实例水平扩展。
- 超限 `ASR_OVERLOAD`：暂停实时字幕、会后补转写；面试/录制/评价不阻断。
- 若答辩要求基线 40 路全部实时，必须按 40 路扩容并重测；不能沿用 20 路目标。

---

## 7. 在线面试与数据细节

- 房间状态：`CREATED → WAITING → LIVE → FINISHED`（异常 `CANCELED/FAILED`）；1v1 上限 2，1v5 上限 6；入会校验时间窗、名单、状态、名额。
- LiveKit token 只授予指定 room+identity+能力；候选人默认不可共享屏幕（主持人按需放开）。
- 信令：独立子域名 `livekit.<domain>` 优先；同域 `/livekit` 必须 rewrite `^/livekit/(.*)$ /$1`；UDP 50000-50100 直暴露，不经 Nginx。
- 录制：Egress 合成 MP4 → MinIO；录制失败不阻塞面试；录像元数据 `record_meta`；回放需鉴权；保留 90 天。
- 面试笔记：`interview_note` 保存人工笔记，支持实时编辑与查询；AI 纪要只作补充。
- 防作弊：事件只采集展示，不自动判定；人脸/虚拟背景/白板按 v3.0 P2 处理。
- ES：索引 `resume_vec`，512 维（bge-small-zh-v1.5）；mapping 的 HNSW 参数必须放在 `index_options`：

```json
"embedding": {
  "type": "dense_vector",
  "dims": 512,
  "index": true,
  "similarity": "cosine",
  "index_options": { "type": "hnsw", "m": 16, "ef_construction": 200 }
}
```

- 混合检索：BM25 + kNN + RRF；保存检索参数/模型版本；切 BGE-M3（1024 维）时新建索引 + 别名切换，不混存维度。
- 手机号 AES-GCM + HMAC 盲索引；对象存储私有；短期签名 URL；下载审计；录像 90 天、简历原件 180 天。

---

## 8. 测试与验收

| 项 | 口径 |
|---|---|
| 鉴权 | 五角色；越权/隐藏按钮直调 API/禁用用户/取消授权全部 403/401 |
| 面试流程 | 申请→一面→评价→下一阶段待办；三方冲突拦截；Offer 确认 |
| 简历 | PDF/Word/图片各 ≥3 份，≥9 成解析；表格保留行列；失败可重试；重复合并 |
| 匹配 | Top5 命中率 ≥80%；F1 ≥85%；消融对比 |
| 在线面试 | 2×1v1 + 1×1v5 稳定 10 分钟；未邀请/超员被拒；断线重连 |
| 录制 | 结束后 ≤2 分钟可回放；鉴权；失败可重试 |
| 实时字幕 | 冒烟 10 路全量；基线 40 路按 20 路契约；超出降级不算失败 |
| AI 故障 | 停 ai-worker/vLLM/ES，主业务继续；任务排队/可重试 |
| 容量 | HTTP P95 <300~500ms；WebRTC 基线 5+5；扩展 10+10 如实报告 |

---

## 9. W1 冻结清单

1. 统一表名/字段：本文第 2 节 vs `04-detailed-design/01-database.md`，产出映射表；Flyway V1 通过。
2. OpenAPI：auth/menus、interview schedules/conflicts、resume upload/parse、room join-token、ASR token、notes、AI summary。
3. 事件 Schema：`room.finished`、`recording.completed`、`interview.scheduled`、`notification.requested`。
4. ASR 20 路压测与 WSS 协议联调。
5. LiveKit 1v5 + 跨网 + 录制 spike。
6. 口径：1v5=6 人、五角色、权限码注册表、Refresh Redis Key。

> 待确认（按 v3.0 第 11 节）：GPU 可用性与 vLLM 显存压测、1v5 人数口径、FunASR CER、异步/Offer 考点、备份盘。GPU/vLLM 显存验证完成前，不宣称“最终容量达标”；切云主档只改 base-url。
