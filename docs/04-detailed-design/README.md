# 详细设计参考资料

> 本目录是早期详细设计（v1.0），保留用于字段、索引、接口、权限码、AI 流水线与容量计算的参考。  
> **最终技术口径以 [`../02-architecture.md`](../02-architecture.md)（v3.0）与 [`../03-development.md`](../03-development.md)（v3.0 契约版）为准。**  
> 本目录中的服务数量、schema 名、表名与 v3.0 存在差异（例如 system 服务、8 个 schema、RocketMQ 等），W1 必须完成表名/字段映射，**禁止直接照抄或混用命名**。

## 文件索引

| 文件 | 内容 |
|---|---|
| [01-database.md](01-database.md) | MySQL 表设计、索引、枚举字典、ES 索引与混合检索、种子数据 |
| [02-auth-rbac.md](02-auth-rbac.md) | 统一登录、JWT/Redis、网关鉴权、RBAC、菜单勾选、动态路由、数据范围、权限码清单 |
| [03-ai.md](03-ai.md) | vLLM、FastAPI 模块、MinerU 解析流水线、分块、Embedding、RAG、匹配、面试 AI、异步任务 |
| [04-online-interview.md](04-online-interview.md) | LiveKit、房间生命周期、录制、防作弊、AI 转写、异步面试、1v1/1v5 容量与压测 |
| [05-api.md](05-api.md) | REST 约定、接口清单、权限码、关键时序、WebSocket 协议、错误码 |
| [06-deployment-plan.md](06-deployment-plan.md) | 部署拓扑、Docker Compose、GPU 规划、测试验收、三人分工与 16 周计划、论文/答辩素材 |
| [07-requirements-traceability.md](07-requirements-traceability.md) | 三份原始需求逐条映射到模块/表/接口/测试 |

## 使用规则

1. **架构/服务/表名**：以 v3.0 文档为准；本目录只提供实现细节参考。
2. **表结构**：参考 `01-database.md` 的字段、索引、枚举与 ES mapping；按 v3.0 的 `iam_* / person / resume / job / application / room / ai_*` 命名做映射。
3. **接口**：参考 `05-api.md` 的路径/错误码/时序；v3.0 的 `room.finished`、ASR WSS、`interview_note` 等以 `03-development.md` 为准。
4. **部署/容量**：参考 `06-deployment-plan.md` 的测试方法与容量思路；硬件口径以 v3.0 的 16C64G + 24GB GPU 为准。
5. **需求覆盖**：用 `07-requirements-traceability.md` 做核对，但按 v3.0 的 P0/P1/P2 重新标注。
