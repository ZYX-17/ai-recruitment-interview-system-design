# B · 服务拆解

> 上级索引：[成员B开发文档](../6.成员B开发文档-智能简历与人才管理子系统.md)  
> 对应原文档：§3

## 3. 服务拆解

### 3.1 file-service（Java，8087）

**职责**：所有对象存储操作的唯一入口。

- `file_meta`：`file_key`、`bucket`、`object_key`、`sha256`、`mime`、`size`、`biz_type`、`biz_id`、`status`（UPLOADING/OK/REJECTED/DELETED）。
- 预签名上传/完成/分片；下载 URL（10 分钟）；MIME/大小校验（简历 ≤20MB，录像 ≤500MB，按 `biz_type`）；SHA-256 秒传；上传审计。
- 桶 `recruit` 私有；对象路径由 file-service 统一生成。
- **对象存储边界（已裁决）**：`resume-service` 不直接依赖 MinIO，所有读写走 file-service 内部接口。

**对外端点**：

| 方法 | 路径 | 权限 |
|---|---|---|
| POST | `/api/file/presign/upload` | 登录 |
| POST | `/api/file/complete` | 登录 |
| POST | `/api/file/multipart/**` | 登录 |
| GET | `/api/file/{id}` | 登录 |
| GET | `/api/file/{id}/url` | 登录 / `file:download` |

**内部端点**（`X-Internal-Token` + 网关屏蔽）：

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/internal/files/register` | 登记已上传对象 |
| POST | `/internal/files/presign/upload` | 服务间获取上传 URL |
| POST | `/internal/files/presign/download` | 服务间获取下载 URL |
| GET | `/internal/files/{id}/meta` | 查询元数据 |
| POST | `/internal/files/{id}/complete` | 确认上传完成 |

**关键约束**：

- `file_key`、`object_key` 唯一；`sha256` 普通索引（用于秒传/查重）。
- `status` 只能由 UPLOADING → OK/REJECTED；下载只允许 OK。
- 预签名 TTL 固定 10 分钟，下载审计。

### 3.2 resume-service（Java，8084）

**职责**：简历与人才域的核心业务。

**表**（`recruit_resume`）：

| 表 | 关键约束 |
|---|---|
| `person` | `user_id` 唯一可空；`phone_hash` 索引；`merged_to`；`status` |
| `resume` | `resume_no` 唯一；`sha256` 唯一；`(person_id)` 索引；`parse_status` |
| `resume_version` | `(resume_id, version_no)` 唯一；parser/model/prompt 版本留痕 |
| `resume_parse_task` | `task_no` 唯一；`ai_task_no`；status/stage/progress；retry ≤3 |
| `resume_chunk` | `(resume_id, chunk_no)` 唯一；`chunk_type` TEXT/TABLE；`es_doc_id` |
| `candidate_skill` | `(person_id, skill_name)` 唯一；EXTRACTED/MANUAL |
| `tag` / `candidate_tag` | `tag_code` 唯一；`(person_id, tag_id)` 唯一；AI/HR |
| `talent_profile` | `person_id` 唯一；archive_level；summary；next_followup_at |
| `talent_followup` | `talent_profile_id`、HR、内容、下次跟进 |
| `screening_rule` | `job_id`、`rules_json`、match_mode、enabled、priority |
| `match_result` | `cache_key` 唯一；job_id/resume_id/job_version/resume_version；总分/召回/LLM/分项/证据/缺口 |
| `resume_merge_record` | source/target person、similarity、dedup_type、status |
| `job_snapshot` | `job_id` 主键；job_version、title、jd_text、requirements_json、status；由事件维护；匹配只读本库 |
| `local_message` / `idempotent_consumption` | Outbox / 消费幂等 |

**模块**：

- `PersonService`、`ResumeService`、`ParseTaskService`、`ChunkService`、`SearchService`、`MatchService`、`DedupService`、`TagService`、`ScreeningRuleService`、`TalentService`、`PortraitService`。
- `EsResumeIndexService`：索引初始化、bulk、混合检索、`mysql-like` 兜底。
- `OutboxService` / `OutboxPublisher`；`JobSnapshotConsumer` 等。
- Feign：`AiServiceClient`、`FileServiceClient`；连接 2s/读 3s；traceId 透传。
- 数据范围/审计：按 `ALL/DEPARTMENT/ASSIGNED/SELF` 过滤候选人/简历；候选人只能访问自己的数据；简历下载、合并、导出写审计。

**上传编排**：

1. 校验参数与文件元数据；
2. 创建/复用 `person`；
3. 创建 `resume`（sha256 判重）；
4. 创建 `resume_parse_task(PENDING)`；
5. 同事务写入 `local_message(resume.received)`；
6. 前端轮询 `parse-status`。

**解析阶段**：PARSE → CHUNK → EMBED → INDEX → SUCCESS；失败重试 ≤3 → DEAD；HR 可手动重试。

### 3.3 ai-service（Python/FastAPI，9010）

**职责**：所有 LLM/Embedding 编排的统一入口。

**表**（`recruit_ai`）：`ai_task`、`prompt_version`、`model_config`、`ai_result`、`ai_call_log`、`human_review`；可选 `agent_trace`、`ai_tool_audit`；公共表 `local_message`、`idempotent_consumption`。

**LLM 路由**：

```
云端 LLM（DeepSeek flash / deepseek-chat，OpenAI 兼容，主）
  → 本地 Ollama（OpenAI 兼容，qwen2.5:7b，断网/云端不可用时）
  → Mock / 模板（仅开发/CI，AI_ALLOW_MOCK=true）
```

- 云端与本地都走 OpenAI 协议；本地 provider 单独放在 `app/services/local_llm.py`，默认 Ollama，有 GPU 时可切 vLLM（只改 `LOCAL_LLM_BASE_URL` + `LOCAL_LLM_MODEL`）。
- 云端失败（断网/超时/限流/5xx）自动切本地；认证/参数错误不降级，避免掩盖配置问题。
- 超时、重试、熔断、配额、调用审计。
- `prompt_version` 从数据库读取；Prompt/模型变更走 PR + 固定评测集回归。
- 结构化输出：JSON Schema + Pydantic 二次校验；失败修复一次，再失败标记 FAILED。
- 简历/转写按不可信数据定界，防 Prompt Injection。
- 不推断年龄/性别/婚育等岗位无关属性。

**任务状态**：PENDING → RUNNING → SUCCESS / FAILED / DEAD；`progress`、`retry_count`、`error_msg`。

**能力**：

| 能力 | biz_type | 说明 |
|---|---|---|
| 简历实体抽取 | `RESUME_EXTRACT` | 教育/经历/技能/年限 + 置信度 + 证据 |
| 人岗匹配精排 | `MATCH` | 五维评分 + 证据 + 缺口 |
| 面试纪要 | `MINUTES` | 摘要/关键问答/风险/待确认 |
| 录播评分 | `ANSWER_SCORE` | 逐题相关性/结构/深度/表达/专业性 |
| 人才画像 | `PROFILE` | 简历 + 面试表现摘要 |
| 打标 | `TAG` | 职能/级别/技能/来源 |

### 3.4 ai-worker（Python，8001）

- `POST /api/v1/embed`：BGE `bge-small-zh-v1.5` 批量 embedding（512 维，批量 32~64）。
- `WS /ws/asr/{sessionId}`：FunASR 流式 ASR（Paraformer + VAD + 标点 + 热词），供 C 的在线面试。
- ASR 协议：`start` → 二进制 Int16LE PCM（16kHz/16bit/单声道，100~200ms/帧）→ `partial/final/error`。
- 单实例目标 ≥20 路；超限返回 `ASR_OVERLOAD`，主流程不阻断。
- `scope=asr` 短时 JWT 校验（由 C 的 room-service 签发）。
- 模式：`EMBED_MODE=mock|bge`、`ASR_MODE=mock|funasr`；Mock 用于无 GPU/无重依赖开发。

### 3.5 mineru-worker（Python，8002）

- 消费解析任务（RabbitMQ）或接受 `POST /parse`。
- 下载原件 → MinerU/magic-pdf → Markdown + 表格 + 公式 + 图注 → 上传 MinIO（经 file-service）。
- 回调/事件通知 resume-service；状态、进度、重试 ≤3、失败原因。
- 模式：`PARSER_MODE=mock|mineru`；Mock 用 PDFBox/Tika 或样例 Markdown，保证链路可跑。
- 与 ai-worker 分离部署，避免 MinerU 的 torch/paddle 依赖影响 ASR 服务。

---

