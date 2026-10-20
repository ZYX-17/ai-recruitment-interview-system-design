# B · API 契约

> 上级索引：[成员B开发文档](../6.成员B开发文档-智能简历与人才管理子系统.md)  
> 对应原文档：§5（含 DTO、错误码、用户上下文）

## 5. API 契约

### 5.1 通用约定

- 响应：`{"code":"0","message":"success","data":{},"traceId":"...","timestamp":...}`；已与总文档 §3.1 对齐（`code=="0"` 为成功）。
- 分页：`{list,total,pageNum,pageSize,pages}`，`pageSize ≤100`。
- ID 字符串；时间 UTC、ISO-8601；更新带 `version`；写操作支持 `Idempotency-Key`。
- 每个非公开接口登记权限码；前端隐藏按钮不是安全边界。

### 5.2 resume 端点（§3.2 实际展开）

> §1.3.2 的“resume 14”是汇总口径；下表是实际方法+路径清单。标记：P0=解析/检索主线，P1=匹配/标签/人才，P2=增强。

| # | 方法 | 路径 | 权限 | 优先级 |
|---|---|---|---|---|
| 1 | GET | `/api/resume/candidates` | `resume:candidate:view` | P1 |
| 2 | POST | `/api/resume/candidates` | `resume:candidate:create` | P1 |
| 3 | GET | `/api/resume/candidates/{id}` | `resume:candidate:view` | P1 |
| 4 | PUT | `/api/resume/candidates/{id}` | `resume:candidate:update` | P1 |
| 5 | DELETE | `/api/resume/candidates/{id}` | `resume:candidate:delete` | P2 |
| 6 | POST | `/api/resume/candidates/{id}/merge` | `resume:candidate:merge` | P1 |
| 7 | POST | `/api/resume/resumes:batch-upload` | `resume:resume:upload` | P0 |
| 8 | POST | `/api/resume/resumes` | `resume:resume:upload` | P0 |
| 9 | GET | `/api/resume/resumes` | `resume:resume:view` | P0 |
| 10 | GET | `/api/resume/resumes/{id}` | `resume:resume:view` | P0 |
| 11 | GET | `/api/resume/resumes/{id}/parse-status` | `resume:resume:view` | P0 |
| 12 | GET | `/api/resume/resumes/{id}/markdown` | `resume:resume:view` | P0 |
| 13 | POST | `/api/resume/resumes/{id}/retry-parse` | `resume:resume:retry-parse` | P0 |
| 14 | PUT | `/api/resume/resumes/{id}/structured` | `resume:resume:edit-structured` | P0 |
| 15 | POST | `/api/resume/search` | `resume:search:execute` | P0 |
| 16 | POST | `/api/resume/search/talent` | `resume:search:execute` | P1 |
| 17 | POST | `/api/resume/matches` | `resume:match:run` | P1 |
| 18 | GET | `/api/resume/matches/{jobId}` | `resume:match:view` | P1 |
| 19 | GET | `/api/resume/matches/detail/{id}` | `resume:match:view` | P1 |
| 20 | GET | `/api/resume/tags` | `resume:tag:view` | P1 |
| 21 | POST | `/api/resume/tags` | `resume:tag:create` | P1 |
| 22 | PUT | `/api/resume/tags/{id}` | `resume:tag:update` | P1 |
| 23 | DELETE | `/api/resume/tags/{id}` | `resume:tag:delete` | P2 |
| 24 | GET | `/api/resume/talents` | `resume:talent:view` | P1 |
| 25 | POST | `/api/resume/talents` | `resume:talent:create` | P1 |
| 26 | PUT | `/api/resume/talents/{id}` | `resume:talent:update` | P1 |
| 27 | DELETE | `/api/resume/talents/{id}` | `resume:talent:delete` | P2 |
| 28 | POST | `/api/resume/screening-rules/{id}/run` | `resume:screening-rule:run` | P1 |
| 29 | GET | `/api/resume/tasks` | `resume:task:view` | P0 |
| 30 | GET | `/api/resume/tasks/{id}` | `resume:task:view` | P0 |

**建议补齐的端点（原文档缺失，P1/P2）**：

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/resume/merge-records` | `resume:candidate:merge` | 合并审核列表 |
| POST | `/api/resume/merge-records/{id}/confirm` | `resume:candidate:merge` | 确认合并 |
| POST | `/api/resume/merge-records/{id}/reject` | `resume:candidate:merge` | 拒绝合并 |
| POST | `/api/resume/candidates/{id}/tags` | `resume:tag:assign` | 人工打标 |
| GET/POST/PUT/DELETE | `/api/resume/screening-rules/**` | `resume:screening-rule:*` | 规则 CRUD |
| GET/POST | `/api/resume/talents/{id}/followups` | `resume:talent:follow` | 跟进记录 |
| POST | `/api/resume/talents/{id}/portrait` | `resume:portrait:generate` | AI 画像草稿 |
| POST | `/api/resume/tasks/{id}/retry` | `resume:task:retry` | 任务重试 |
| GET | `/api/resume/candidates/export` | `resume:candidate:export` | 导出（P2） |
| GET | `/api/resume/resumes/{id}/download` | `resume:resume:download` | 下载原件（P2） |

### 5.3 file 端点

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/api/file/presign/upload` | 登录 | 获取预签名上传 URL |
| POST | `/api/file/complete` | 登录 | 完成上传、校验、落元数据 |
| POST | `/api/file/multipart/**` | 登录 | 分片上传 |
| GET | `/api/file/{id}` | 登录 | 元数据 |
| GET | `/api/file/{id}/url` | 登录 / `file:download` | 10 分钟下载 URL |
| POST | `/internal/files/register` | `X-Internal-Token` | 服务间登记 |
| POST | `/internal/files/presign/upload` | `X-Internal-Token` | 服务间上传 |
| POST | `/internal/files/presign/download` | `X-Internal-Token` | 服务间下载 |
| GET | `/internal/files/{id}/meta` | `X-Internal-Token` | 元数据 |
| POST | `/internal/files/{id}/complete` | `X-Internal-Token` | 完成上传 |

### 5.4 ai 端点

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/api/ai/recorded-interviews` | `portal:async:*` / 面试官 | 录播面试创建/评分 |
| POST | `/api/ai/answers` | `portal:async:*` / 面试官 | 逐题评分 |
| POST | `/api/ai/interviews/{id}/hints`（SSE） | `interview:evaluation:view` | 实时问题提示 |
| GET | `/api/ai/transcripts/{interviewId}` | `room:transcript:view` | 转写查询 |
| POST | `/api/ai/resumes/{id}/re-extract` | `resume:resume:retry-parse` | 重新抽取 |
| GET | `/api/ai/health` | 运维/内网 | 健康检查 |
| WSS | `/api/ai/v1/asr/{sessionId}` | `scope=asr` 令牌 | 由 ai-worker 承接（Nginx rewrite） |
| POST | `/api/ai/v1/tasks` | 内部 | 任务提交（骨架已有，补契约） |
| GET | `/api/ai/v1/tasks/{taskNo}` | 内部/登录 | 任务状态轮询 |
| POST | `/api/v1/embed` | 内部 | BGE 批量 embedding（ai-worker） |
| GET/PUT | `/api/ai/prompts/**` | `ai:prompt:view/update` | Prompt 管理（P2） |
| GET/POST | `/api/ai/tasks/**` | `ai:task:view/retry` | 任务管理（P2） |

**纪要回写/复核（与 A 的边界）**：

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/internal/interviews/{id}/minutes` | `X-Internal-Token` | ai-service 回写纪要草稿，按 taskId 幂等 |
| GET | `/api/interview/schedules/{id}/minutes` | `interview:minutes:view` | 由 A 的 interview-service 提供，B 只读/联调 |
| PUT | `/api/interview/minutes/{id}/confirm` | `interview:minutes:confirm` | 由 A 的人工复核提供；B 不实现 |

### 5.5 关键 DTO 草案

**上传完成 `POST /api/file/complete`**：

```json
{
  "fileKey": "string",
  "bizType": "RESUME",
  "bizId": "string?",
  "fileName": "张三-简历.pdf",
  "mime": "application/pdf",
  "size": 123456,
  "sha256": "hex string"
}
```

**解析任务状态 `GET /api/resume/resumes/{id}/parse-status`**：

```json
{
  "resumeId": "string",
  "taskNo": "string",
  "parseStatus": "PENDING|PARSING|STRUCTURING|INDEXING|SUCCESS|FAILED",
  "stage": "PARSE|CHUNK|EMBED|INDEX",
  "progress": 0,
  "errorMsg": "string?",
  "retryCount": 0
}
```

**简历搜索 `POST /api/resume/search`**：

```json
{
  "keyword": "Java 后端",
  "filters": {"city": "上海", "education": "本科", "workYearsMin": 3, "skills": ["Java", "Spring"]},
  "pageNum": 1,
  "pageSize": 20
}
```

**人岗匹配 `POST /api/resume/matches`**：

```json
{
  "jobId": "string",
  "resumeIds": ["string"],
  "matchMode": "HYBRID",
  "topK": 50
}
```

**匹配结果**：

```json
{
  "id": "string",
  "jobId": "string",
  "resumeId": "string",
  "personId": "string",
  "overallScore": 86.5,
  "recallScore": 78.0,
  "llmScore": 90.2,
  "scoreDetail": {"skill": 90, "experience": 85, "education": 80, "stability": 88, "potential": 86},
  "evidence": [{"dim": "skill", "text": "5 年 Java/Spring 微服务经验", "source": "工作经历"}],
  "gaps": ["缺少大规模团队管理经验"],
  "model": "qwen2.5-7b",
  "promptVersion": 3
}
```

**AI 任务提交 `POST /api/ai/v1/tasks`**：

```json
{
  "bizType": "RESUME_EXTRACT|MATCH|MINUTES|ANSWER_SCORE|PROFILE|TAG",
  "bizId": "string",
  "caller": "resume-service",
  "input": {},
  "promptCode": "resume_extract",
  "promptVersion": 3
}
```

### 5.6 错误码

**RSM2xxx**

| 码 | 含义 |
|---|---|
| `RSM2001` | 简历不存在（已有） |
| `RSM2002` | 候选人不存在 |
| `RSM2003` | 候选人已存在/重复投递 |
| `RSM2004` | 简历版本冲突（乐观锁失败，刷新后重试） |
| `RSM2101` | 简历解析失败（已有） |
| `RSM2102` | 解析任务不存在 |
| `RSM2103` | 解析重试次数超限 |
| `RSM2201` | 合并目标非法（已有） |
| `RSM2202` | 合并记录不存在 |
| `RSM2301` | 筛选规则非法 |
| `RSM2302` | 匹配结果不存在 |
| `RSM2401` | 人才档案不存在 |
| `RSM2501` | ES 不可用（已降级 mysql-like） |

**FIL7xxx（本期补齐）**

| 码 | 含义 |
|---|---|
| `FIL7001` | 文件不存在 |
| `FIL7002` | 文件过大 |
| `FIL7003` | MIME 类型不支持 |
| `FIL7004` | 上传未完成 |
| `FIL7005` | 预签名失败 |
| `FIL7006` | 对象存储异常 |
| `FIL7007` | 校验和不匹配 |
| `FIL7008` | 无权下载 |

**AIS5xxx**

| 码 | 含义 |
|---|---|
| `AIS5001` | 模型调用超时（已有） |
| `AIS5002` | 熔断降级（已有） |
| `AIS5003` | AI 任务不存在 |
| `AIS5004` | 输出 Schema 校验失败 |
| `AIS5005` | Prompt 版本不存在 |
| `AIS5006` | 配额/预算超限 |
| `AIS5101` | ASR 过载（`ASR_OVERLOAD`） |
| `AIS5102` | 作答已提交（已有） |
| `AIS5103` | Embedding 失败 |

### 5.7 用户上下文与权限（与 A 对齐）

- 网关注入：`X-User-Uid`、`X-User-Role`（逗号分隔）、`X-Trace-Id`；B 服务只信任这三个头。
- **不使用 `X-User-Permissions`**：A 的 Access Token 只放角色码，权限码不入 Token；B 从 `auth:perm:{uid}:{ver}` 缓存读取，未命中回源 auth-service 内部接口（具体接口待 A 落地确认）。
- 本地开发：Mock 头使用 `X-User-Uid` / `X-User-Role`，权限集合按 A 的 `auth:perm` 格式 Mock。
- 网关必须剥离客户端伪造的 `X-User-*`；B 服务直接端口访问时只接受内部网络请求。
- **注意**：A 的后端鉴权代码当前仍是骨架（`auth-service` 仅 `PingController`，网关仅 TraceId/Internal 过滤）；以上为 A 文档契约，A 落地后以实际代码为准。

---

