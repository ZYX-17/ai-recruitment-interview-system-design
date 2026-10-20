# B · 总体架构与数据流

> 上级索引：[成员B开发文档](../6.成员B开发文档-智能简历与人才管理子系统.md)  
> 对应原文档：§2

## 2. 总体架构与数据流

```mermaid
flowchart TB
    HR[HR / 候选人] --> FE[C 的 Vue 前端]
    FE --> GW[A 的 gateway]
    GW --> RSM[resume-service 8084]
    GW --> FIL[file-service 8087]
    GW --> AIS[ai-service 9010]

    FIL -- 预签名直传 --> MINIO[(MinIO)]
    RSM -- 内部接口 --> FIL
    RSM -- 解析任务/事件 --> MQ[(RabbitMQ)]
    MQ --> MWK[mineru-worker 8002]
    MWK -- 下载原件/上传产物 --> FIL
    MWK -- 解析结果回调 --> RSM

    RSM -- 分块文本 --> AIW[ai-worker 8001]
    AIW -- BGE 向量 --> RSM
    RSM -- bulk index --> ES[(Elasticsearch resume_vec)]

    RSM -- LLM 实体抽取/匹配 --> AIS
    AIS -- OpenAI 兼容 --> LLM[DeepSeek flash 优先 → 本地 Ollama → Mock]

    AIS -. room.finished / recording.completed .- CROOM[C 的 room-service]
    AIS -. interview.result.submitted .- AITV[A 的 interview-service]
```

### 2.1 简历解析流水线（题目硬约束）

```
file-service 预签名上传
  → MinIO 原件
  → file_meta(OK)
  → resume-service 创建 person/resume/resume_parse_task + Outbox resume.received
  → mineru-worker 消费：下载原件 → MinerU → Markdown/表格/公式/图注 → 上传 MinIO
  → resume-service 分块（300~500 token；表格独立成块；标题路径前缀）
  → ai-worker BGE 批量向量化（512 维）
  → ES bulk 写入 resume_vec
  → ai-service LLM 实体抽取（Schema 校验）
  → resume.structured_json 入库；parse_status=SUCCESS
```

### 2.2 人岗匹配流水线

```
规则硬过滤（学历/年限/地点/技能/年龄按规则配置）
  → ES 混合召回 top50（BM25 text^2 + skills + kNN + RRF）
  → ai-service LLM 精排 top20（技能/经验/学历/稳定性/潜力，五维+证据）
  → final = 0.3 × 召回分 + 0.7 × LLM 分
  → match_result 缓存（job/resume/prompt/model 版本）
  → 前端展示评分理由、证据片段、缺口
```

### 2.3 查重/去重流水线

```
SHA256 文件级（同文件秒传/命中）
  → phone_hash 精确层
  → ES kNN 摘要向量相似层（阈值 0.92）
  → resume_merge_record(PENDING)
  → HR 人工确认合并 / 拒绝
  → person.merged_to、resume_version 保留旧版本
```

### 2.4 人才库

```
标签（AI/HR） → talent_profile 归档
  → 人才检索/二次挖掘
  → 跟进记录 + next_followup_at
  → talent.followup.due 提醒（延迟 MQ 或定时扫描）
  → AI 画像草稿（简历 + 面试表现）→ 人工复核
```

---

