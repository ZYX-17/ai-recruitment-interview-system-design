# B · 本地开发模式

> 上级索引：[成员B开发文档](../6.成员B开发文档-智能简历与人才管理子系统.md)  
> 对应原文档：§8

## 8. 本地可切换模式

### 8.1 能力矩阵

| 能力 | 环境变量 | 可选值 | 默认 | 真实档依赖 |
|---|---|---|---|---|
| LLM | `AI_MODE` | `auto/cloud/local/mock` | `auto`（DeepSeek flash → Ollama → Mock） | 云 Key / 本地 Ollama（可选 vLLM） |
| 解析 | `PARSER_MODE` | `mock/mineru` | `mock` | MinerU/magic-pdf |
| 向量 | `EMBED_MODE` | `mock/bge` | `mock` | BGE 模型 |
| ASR | `ASR_MODE` | `mock/funasr` | `mock` | FunASR/GPU |
| 检索 | `SEARCH_MODE` | `es/mysql-like` | `mysql-like` | ES + IK |
| MQ | `MQ_ENABLED` | `true/false` | `false` | RabbitMQ |
| 存储 | `STORAGE_MODE` | `minio/local` | `local` | MinIO |
| 基础设施 | `INFRA_MODE` | `full/slim/minimal` | `minimal` | Docker |

### 8.2 三档基础设施

| 档位 | 启动内容 | 适用 |
|---|---|---|
| `full` | MySQL + Redis + RabbitMQ + MinIO + ES + 本地 LLM（vLLM/Ollama）+ workers | 联调/答辩 |
| `slim` | MySQL + MinIO；ES 用 mysql-like；MQ 关闭 | 日常开发 |
| `minimal` | 仅 MySQL；存储本地目录；检索 SQL | 快速起步 |

### 8.3 切档方式

```bash
# 默认：DeepSeek flash 优先 → 本地 Ollama 兜底 → Mock（本地基础设施先用最小档）
AI_MODE=auto CLOUD_API_KEY=... CLOUD_MODEL=deepseek-chat \
LOCAL_LLM_BASE_URL=http://127.0.0.1:11434/v1 LOCAL_LLM_MODEL=qwen2.5:7b \
PARSER_MODE=mock EMBED_MODE=mock ASR_MODE=mock \
SEARCH_MODE=mysql-like MQ_ENABLED=false STORAGE_MODE=local

# 只用云端 / 只用本地 / 只用 Mock
AI_MODE=cloud CLOUD_BASE_URL=https://api.deepseek.com/v1 CLOUD_MODEL=deepseek-chat CLOUD_API_KEY=...
AI_MODE=local LOCAL_LLM_BASE_URL=http://127.0.0.1:11434/v1 LOCAL_LLM_MODEL=qwen2.5:7b
AI_MODE=mock

# 有 GPU 时可把本地档切到 vLLM（同一个 local_llm.py）
AI_MODE=local LOCAL_LLM_BASE_URL=http://127.0.0.1:8000/v1 LOCAL_LLM_MODEL=Qwen2.5-7B-Instruct-AWQ

# 切真实解析/向量/ES
PARSER_MODE=mineru EMBED_MODE=bge SEARCH_MODE=es ES_URI=http://127.0.0.1:9200
```

> `auto` 会先调云端；断网/超时/限流/5xx 时自动切本地模型；本地也不可用且 `AI_ALLOW_MOCK=true` 时返回 Mock。Mock 不代表最终交付。
>
> 本地 Ollama 跑在**开发电脑**上（默认 11434）；平台部署到 VM 后以 DeepSeek 云 API 为主，如需本地兜底需在 VM 上另装 Ollama 或指向开发机地址。

---

