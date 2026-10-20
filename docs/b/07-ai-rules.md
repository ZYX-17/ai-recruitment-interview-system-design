# B · 核心算法与规则

> 上级索引：[成员B开发文档](../6.成员B开发文档-智能简历与人才管理子系统.md)  
> 对应原文档：§7

## 7. 核心算法与规则

### 7.1 解析

- file-service 校验 MIME/大小/分片；对象入 MinIO。
- mineru-worker 下载原件，MinerU 输出 Markdown/表结构/公式/图注；产物经 file-service 上传。
- 解析任务状态：PENDING/RUNNING/SUCCESS/FAILED/DEAD；stage：PARSE/CHUNK/EMBED/INDEX；progress 0~100；重试 ≤3。

### 7.2 分块

- 300~500 token/块；按标题/语义切分。
- **表格整体独立成块**；超长表格按行分组并重复表头。
- 每块前缀注入标题路径：`[工作经历>腾讯>2019-2022]`。
- 工作经历尽量保持“公司+职位+时间+职责”同块。
- 写入 `resume_chunk`；`chunk_type=TEXT/TABLE`；`es_doc_id` 与 ES 文档对应。

### 7.3 Embedding 与 ES

- BGE `bge-small-zh-v1.5`，512 维，批量 32~64，CPU <20ms/条（目标）。
- `EMBED_MODE=mock` 时用确定性哈希生成 512 维，仅供开发。
- ES bulk 写入 `resume_vec`；每条文档带 `emb_model`。
- 检索参数/模型版本/人工反馈留痕，供 W13 评测复现。

### 7.4 混合检索

- BM25：`multi_match`（content、skills^2、section_path）+ IK。
- kNN：`embedding` topK。
- RRF 融合；召回 top50。
- ES 不可用 → `mysql-like`（SQL LIKE + 字段过滤），30 秒内切换。

### 7.5 人岗匹配

- 硬过滤：学历/年限/地点/技能（年龄按规则配置，注意 AI 纪律）；白名单操作符，≤1s。
- 召回：BM25 + kNN + RRF，top50。
- 精排：LLM top20，五维（技能/经验/学历/稳定性/潜力）+ 证据 + 缺口。
- 必备条件未命中 → 总分封顶。
- `final = 0.3 × recallScore + 0.7 × llmScore`。
- 缓存键：job 版本 + resume 版本 + prompt 版本 + model；`cache_key` 唯一。
- 结果可解释、可覆盖；人工确认后生效。

### 7.6 查重/去重

- SHA256 文件级：同文件命中已有 resume，返回重复提示；不自动合并 person，是否合并由 HR 确认。
- `phone_hash` 精确层：同手机号提示合并。
- ES kNN 摘要向量层：阈值 0.92；正负样本定标。
- 只提示，人工确认合并；`resume_merge_record` PENDING → CONFIRMED/REJECTED。
- 保留 `resume_version`；`person.merged_to` 指向主档案。

### 7.7 标签

- `tag`：职能/级别/技能/来源等维度。
- `candidate_tag`：AI/HR、置信度、操作人。
- AI 打标（`TAG`）产出草稿；HR 可覆盖。

### 7.8 筛选规则

- `screening_rule.rules_json`：`[{field,op,value}]`。
- 字段：学历、工作年限、城市、技能；年龄仅作为 HR 手填条件，AI 不推断。
- 操作符白名单：`EQ/NE/GT/GTE/LT/LTE/IN/NOT_IN/CONTAINS`。
- `match_mode`：RECALL_ONLY（只召回）/ HYBRID（召回 + LLM 精排）。

### 7.9 人才库与画像

- `talent_profile`：归档等级 SILVER/GOLD/PLATINUM、摘要、面试历史、负责人、下次跟进。
- `talent_followup`：HR、内容、下次跟进时间。
- 跟进提醒：`talent.followup.due`；MQ 延迟插件未启用时用定时扫描。
- AI 画像：简历 + 面试表现 → 草稿 → 人工确认写 `summary`；不推断敏感属性。

### 7.10 AI 路由与审计

- 云端优先 → 本地 OpenAI 兼容模型 → Mock；OpenAI 协议统一，切档只改 base-url + model 或 `LOCAL_LLM_*`。
- Prompt 版本、JSON Schema、Pydantic 二次校验、失败修复一次。
- `ai_call_log`：traceId、taskNo、capability、provider/model、prompt、tokens、latency、status、degraded_to。
- 配额/预算熔断；日志脱敏；不记录简历全文/密钥。

---

