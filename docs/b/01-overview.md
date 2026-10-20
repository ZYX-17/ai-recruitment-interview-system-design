# B · 总览与边界

> 上级索引：[成员B开发文档](../6.成员B开发文档-智能简历与人才管理子系统.md)  
> 对应原文档：§0、§1、§11、附录 B

## 0. 当前阶段与实施约束

1. 本文作为 **B 的开发计划/实现契约**；已裁决项可按阶段开始实现，未裁决项先改文档再写代码。
2. **Flyway 迁移、ES IK/索引初始化、MinIO 桶初始化由 B 自行落地**，不再等老师；按 P0/P1/P4 阶段实施。
3. 文档中已裁决的事项（端点、事件、`FIL7xxx`、对象存储边界、端口、Dockerfile 方案）先固定下来，按阶段实施。
4. **AI 主档与部署已裁决**：云端 DeepSeek flash（`deepseek-chat` 快档）优先 → 断网/不可用时切本地 Ollama（OpenAI 兼容）→ 最后可选 Mock；**本地 Ollama 跑在开发电脑上，不是 VM**；平台服务后续部署到虚拟机，开发期先用 minimal/slim。

---

## 1. 职责与边界

### 1.1 B 的范围

| 类型 | 工程 | 端口 | schema | 职责 |
|---|---|---:|---|---|
| Java | `resume-service` | 8084 | `recruit_resume` | 简历/人才主业务、解析编排、检索、匹配、去重、标签、规则、人才库 |
| Java | `file-service` | 8087 | `recruit_file` | 文件元数据、MinIO 对象存储、预签名、校验、下载 |
| Python | `ai-service` | 9010 | `recruit_ai` | LLM/ASR/Embedding 编排、任务、Prompt、降级、审计、人工复核 |
| Python | `ai-worker` | 8001 | 无业务表 | BGE Embedding、FunASR 流式 ASR（供 C 的在线面试） |
| Python | `mineru-worker` | 8002 | 无业务表 | MinerU 文档解析（PDF/Word/图片） |

- **3 个 schema**：`recruit_resume`（14 业务表 + 2 公共表）、`recruit_file`（1 + 2）、`recruit_ai`（8 + 2）。
- **ES**：`resume_vec` 索引，512 维、HNSW、IK、BM25 + kNN + RRF。
- **B 兼任**：AI 底座与 GPU 节点负责人（vLLM / FunASR / MinerU / ES 部署与压测）。

### 1.2 与 A / C 的边界

| 对方 | 边界 |
|---|---|
| A（gateway / auth / interview / notify） | A 提供登录、JWT、权限码、网关、通知消费；B **不实现**登录/鉴权/通知，只信任网关注入的 `X-User-Uid` / `X-User-Role` / `X-Trace-Id` 并发布事件 |
| C（room-service + 前端） | B 提供 FunASR WSS、BGE embedding、文件预签名能力；B **不实现** WebRTC/录制/房间业务 |
| interview 域 | `job` / `application` 归 A；B 只通过 `job.published` / `job.updated` 事件维护 `job_snapshot`，不跨库 JOIN |
| room 域 | `room.finished` / `recording.completed` 由 C 发布，B 消费用于纪要/评分/画像 |
| 前端 | 归 C；B 只提供 API/OpenAPI，不写 Vue 页面 |

### 1.3 数据所有权

- 只能写自己的 schema：`recruit_resume` / `recruit_file` / `recruit_ai`。
- 跨服务只传 ID + 必要快照，**禁止跨库 JOIN / 跨库写**。
- `person` 归 `resume-service`；interview/room 只存 `person_id` + 姓名快照。
- AI 输出一律为 **草稿**，人工确认后才写入正式业务状态。

---

## 11. 文档与骨架差异裁决

| # | 问题 | 裁决 |
|---|---|---|
| 1 | `resume 14 / file 3 / ai 7` 与 §3.2 展开数量不一致 | 以 §3.2 的实际方法+路径为准，本文已完整枚举；14/3/7 仅作汇总口径 |
| 2 | 事件命名 `resume.parsed`（骨架） vs `resume.parse.completed`（文档） | 统一到文档命名；`resume.parsed` 保留为兼容别名；补齐缺失事件 |
| 3 | `FIL7xxx` 错误码缺失 | 本文定义 `FIL7001~FIL7008`，实现时补到 `ErrorCode` |
| 4 | `resume-service` 缺 MinIO 依赖 | **不添加** MinIO 依赖；所有对象存储走 file-service 内部接口（见 §3.1） |
| 5 | ai-worker 端口 8001 vs compose 10095 | 统一 **8001**；compose 的 `10095:10095` 后续改为 `8001:8001` |
| 6 | 三个 Python 工程无 Dockerfile，ai-service 不在 compose | 补三个 Dockerfile；ai-service 纳入 compose；补 env/healthcheck/network |
| 7 | Flyway / ES IK / 索引 / MinIO 桶未落地 | **B 自行落地，不再等老师**；按 P0/P1/P4 推进 |
| 8 | 成功码 `"0"` vs 文档 `"OK"` | **已统一为 `"0"`**：总文档 §3.1 已改，B/骨架一致 |
| 9 | 权限码多于端点（export/assign/CRUD/portrait 等） | 本文列为 P1/P2 补齐；不实现前端隐藏按钮安全边界 |
| 10 | AI 主档与本地模型文件 | **DeepSeek flash → 本地 Ollama → Mock**；本地模型单独 `app/services/local_llm.py`，vLLM 可选；总开发文档已同步（2026-10-19） |

---

## 附录 B：本文与 v3.1 文档的关系

本文是 B 的实施细化，不替代 v3.1 开发文档。若两者冲突，以本文 §11 的裁决为准；未裁决部分回到 v3.1 文档。后续实现中新增端点/事件/字段，先更新本文与 OpenAPI/事件 Schema，再改代码。
