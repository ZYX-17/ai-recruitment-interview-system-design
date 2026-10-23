# B · 数据、索引与迁移

> 上级索引：[成员B开发文档](../6.成员B开发文档-智能简历与人才管理子系统.md)  
> 对应原文档：§4

## 4. 数据、索引与迁移

### 4.1 Flyway / Alembic

| schema | 归属 | 迁移方式 | 说明 |
|---|---|---|---|
| `recruit_resume` | resume-service | Flyway `V1_0__init.sql` | 复制 `sql/04_recruit_resume.sql`，移除 `USE` |
| `recruit_file` | file-service | Flyway `V1_0__init.sql` | 复制 `sql/06_recruit_file.sql` |
| `recruit_ai` | ai-service | SQLAlchemy + Alembic | 复制 `sql/07_recruit_ai.sql`；Python 侧用 Alembic，resume/file 用 Flyway |

> Flyway / ES IK / 索引 / MinIO 桶由 B 自行落地，不再等老师；按 P0/P1/P4 任务推进。

### 4.2 ES `resume_vec` mapping

```json
{
  "settings": { "number_of_shards": 1, "number_of_replicas": 0 },
  "mappings": {
    "properties": {
      "doc_type":      { "type": "keyword" },
      "resume_id":     { "type": "long" },
      "person_id":     { "type": "long" },
      "job_id":        { "type": "long" },
      "chunk_no":      { "type": "integer" },
      "chunk_type":    { "type": "keyword" },
      "section_path":  { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
      "content":       { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
      "skills":        { "type": "keyword" },
      "emb_model":     { "type": "keyword" },
      "embedding": {
        "type": "dense_vector",
        "dims": 512,
        "index": true,
        "similarity": "cosine",
        "index_options": { "type": "hnsw", "m": 16, "ef_construction": 200 }
      }
    }
  }
}
```

- 混合检索：`multi_match`（`content^1`、`skills^2`）+ `knn` + RRF。
- 升级 BGE-M3（1024 维）：新建索引 + 别名切换，不混存不同维度。
- `emb_model` 版本字段必须写入每条文档。
- ES 不可用：`SEARCH_MODE=mysql-like`，30 秒内切换（Nacos 配置）。

### 4.3 MinIO 对象布局（由 file-service 统一管理）

```
recruit/
├── resume/{personId}/{resumeId}/original/{fileName}
├── resume/{personId}/{resumeId}/{versionNo}/markdown.md
├── resume/{personId}/{resumeId}/{versionNo}/tables.json
├── resume/{personId}/{resumeId}/{versionNo}/images/{name}
└── recording/...（C 的录制，B 不负责）
```

### 4.3.1 ES 文档字段（resume_vec）

| 字段 | 类型 | 说明 |
|---|---|---|
| `doc_id` | keyword | `{resumeId}-{chunkNo}` |
| `doc_type` | keyword | `resume` / `jd` |
| `resume_id` / `person_id` | keyword | 业务 ID（字符串） |
| `chunk_no` | integer | 块序号 |
| `chunk_type` | keyword | TEXT / TABLE |
| `section_path` | text | 标题路径（IK，缺失回退 standard） |
| `content` | text | 带标题路径前缀的块内容 |
| `embedding` | dense_vector | 512 维，cosine，HNSW m=16/ef_construction=200 |
| `embedding_model` | keyword | bge-small-zh-v1.5 / mock-local |
| `updated_at` | date | 索引时间 |

### 4.4 Redis Key

| Key | 用途 |
|---|---|
| `resume:parse:{resumeId}` | 解析状态缓存 |
| `resume:match:{cacheKey}` | 匹配结果缓存 |
| `resume:embed:{sha256}` | Embedding 幂等 |
| `resume:merge:{personId}` | 去重提示缓存（可选） |
| `file:presign:{fileKey}` | 预签名幂等（可选） |
| `idem:{scope}:{key}` | `Idempotency-Key` SETNX |

---

