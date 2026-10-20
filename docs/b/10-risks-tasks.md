# B · 风险、待确认与任务清单

> 上级索引：[成员B开发文档](../6.成员B开发文档-智能简历与人才管理子系统.md)  
> 对应原文档：§12、§13、附录 A

## 12. 风险与待确认

### 12.1 风险

| 风险 | 影响 | 应对 |
|---|---|---|
| MinerU 依赖重、镜像大、图片 OCR 复杂 | 解析链路阻塞 | 先 mock；W1 spike；独立容器 |
| FunASR/GPU 显存与并发 | 字幕容量不达标 | BGE CPU 可跑；FunASR 需 GPU；20 路契约 |
| ES IK/HNSW 初始化复杂 | 向量检索不可用 | 自定义镜像/脚本；mysql-like 兜底 |
| RabbitMQ 延迟插件未启用 | 跟进提醒/过期延迟 | 定时扫描兜底 |
| 端点/事件契约漂移 | 跨服务联调失败 | 本文为 B 基线；变更先改文档/OpenAPI |
| 全量 B 工作量大 | 延期 | P0 主线优先；FunASR/异步/画像后置 |
| 平台暂未部署；Ollama 在开发电脑 | 无法真实联调 | 开发期最小档；平台后续上 VM；DeepSeek flash 主 + 开发机 Ollama 断网兜底 |
| 云端 API 网络/配额 | 断网或超额时 AI 不可用 | 自动切本地 OpenAI 兼容模型；Mock 兜底；答辩前测热点/额度 |
| 数据隐私 | 简历敏感信息 | 最小权限、脱敏日志、不推断敏感属性 |

### 12.2 待确认

1. Flyway/ES IK/MinIO 桶：**已由 B 自行落地**（不再等老师）；实现按 P0/P1/P4 推进。
2. AI 主档：**DeepSeek flash（deepseek-chat）+ 本地 Ollama（qwen2.5:7b）**；API Key 先留空，后续提醒补充；预算待定。
3. 部署：平台服务后续部署到虚拟机；**Ollama 在开发电脑本地**（不是 VM），VM 上以云 API 为主；开发期用 minimal/slim。
4. 与 A 的用户头/权限读取：已按 A 文档对齐为 `X-User-Uid` / `X-User-Role` / `X-Trace-Id`，权限从 `auth:perm:{uid}:{ver}` 缓存读取；待 A 后端代码落地确认缓存格式/内部接口。事件名已按总文档统一，`match.completed` 的消费方待 A/notify 确认。
5. 与 C 的 ASR 端口、Nginx rewrite、`scope=asr` 令牌联调。
6. 人才画像：按 P2/Should 保留，等需求评审最终确认。
7. 年龄字段：仅作为 HR 手填筛选条件，AI 不推断。
8. 评测集与演示 seed：F1/Top5/NDCG/CER 标注与演示数据待准备。

---

## 13. 任务清单

### P0 · 骨架与公共件
- [x] 分支 `feat/resume-talent`（已完成）
- [x] `common`：`ErrorCode` 补 RSM/FIL/AIS；`RedisKeys` 补 resume 键；`PageResult`
- [x] `contracts`：`EventTypes` 对齐
- [ ] `contracts`：事件 payload DTO
- [x] 全局异常、trace filter
- [x] file-service MP 分页/乐观锁（resume-service 待接入）
- [ ] Jackson Long→String（当前 file DTO 手动把 ID 转字符串）
- [x] Flyway 迁移（V1_0 resume/file，`enabled: true`）
- [ ] local profile / `.env.example`（AI 侧已有；Java 服务待补）

### P1 · file-service
- [x] `file_meta` 实体/Mapper/Service
- [x] MinIO 客户端、桶初始化
- [x] 预签名上传/完成/下载/内部接口
- [ ] 分片上传（录像 ≤500MB 可单次预签名 PUT，暂缓）
- [x] MIME/大小/SHA-256/秒传
- [x] 错误码与单测（`FilePolicyTest` 6 项通过）

### P2 · resume-service 核心
- [x] 核心 6 表实体/Mapper（person/resume/parse_task/version/job_snapshot/local_message）
- [ ] 其余 8 表实体/Mapper（skill/tag/talent/rule/match/merge 等）
- [x] person/resume/parse task 创建与状态
- [x] 上传编排（经 file-service 内部接口校验）+ Outbox `resume.received` 写入
- [ ] Outbox 发布器（RabbitMQ confirms/退避重试）
- [x] parse-status/markdown/structured/retry
- [x] 任务列表/详情
- [ ] 事件消费 `job.published/job.updated`

### P3 · 解析流水线
- [ ] mineru-worker mock/真实 MinerU
- [ ] MinIO 下载/产物上传（经 file-service）
- [ ] 回调/任务状态/重试/DLQ
- [ ] PDF/Word/图片三类样例

### P4 · 分块 + Embedding + ES
- [ ] 分块器（300~500 token、表格独立、表头重复、标题路径）
- [ ] ai-worker `/api/v1/embed`（mock/bge）
- [ ] ES mapping/索引初始化
- [ ] bulk 索引、混合检索、mysql-like 兜底

### P5 · ai-service
- [ ] 任务持久化/状态机
- [ ] LLM 云端优先 → 本地 OpenAI 兼容 → Mock；`local_llm.py` 单独文件；超时/降级/熔断/配额
- [ ] Prompt 版本、JSON Schema/Pydantic、修复重试
- [ ] 实体抽取/重抽取/health
- [ ] 调用审计、人工复核

### P6 · 匹配/标签/规则
- [ ] 硬过滤规则解释器
- [ ] 召回 top50 + 精排 top20
- [ ] `0.3/0.7` 加权、缓存、证据/缺口
- [ ] 标签字典/打标
- [ ] 规则 CRUD/run

### P7 · 去重/人才库
- [ ] SHA256/phone_hash/kNN 0.92
- [ ] merge record + 审核端点
- [ ] talent_profile/talent_followup
- [ ] 跟进提醒
- [ ] AI 画像草稿 + 人工复核

### P8 · ai-worker 异步
- [ ] FunASR WSS mock/funasr
- [ ] `scope=asr` 校验、20 路容量、`ASR_OVERLOAD`
- [ ] 录播评分（Should）

### P9 · 评测/测试/交付
- [ ] 评测集与指标（F1/Top5/NDCG/CER）
- [ ] 单测/集成测试
- [ ] smoke 脚本、OpenAPI 导出
- [ ] 演示 seed/README

---

## 附录 A：关键文件索引

- B 范围与里程碑：`docs/3.开发文档-招聘面试系统.md` §1.3.2、`docs/2.架构设计方案-招聘面试系统微服务架构.md` §7
- 表结构：`sql/04_recruit_resume.sql`、`sql/06_recruit_file.sql`、`sql/07_recruit_ai.sql`
- 脚手架：`backend/resume-service`、`backend/file-service`、`ai/ai-service`、`ai/ai-worker`、`ai/mineru-worker`
- 公共件：`backend/common`、`backend/contracts`
- 基础设施：`infra/docker-compose.base.yml`、`infra/docker-compose.gpu.yml`、`infra/nginx/nginx.conf`

