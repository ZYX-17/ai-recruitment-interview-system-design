# AI 招聘面试系统 · 毕业设计文档库

面向中小企业招聘场景的一体化面试系统设计，覆盖：

- **智能简历与人才管理**：PDF/Word/图片解析、结构化入库、标签、查重去重、人岗匹配、人才库、跟进提醒、候选人画像
- **面试管理**：自定义流程、候选人状态机、排期冲突校验、面试官协同评价、Offer 审批、AI 纪要
- **在线面试**：LiveKit WebRTC 1v1 / 1v5、屏幕共享、聊天、录制回放、实时转写、AI 问题提示、异步录播面试、可选防作弊
- **平台治理**：统一登录、RBAC、动态菜单、按钮/API 权限码、数据范围、审计

技术基线：**Vue 3 + Java 微服务 + FastAPI + vLLM + MySQL + Elasticsearch + Redis + RabbitMQ + MinIO + LiveKit**。

> 本仓库是设计文档集合，**不包含可运行代码**。文档中的示例账号、密码、API Key、手机号均为占位符/虚构数据，不包含真实密钥或个人数据，不可直接用于生产环境。

## 仓库结构

```
.
├── README.md
└── docs/
    ├── 01-requirements/              # 三份原始题目（需求基线）
    │   ├── 01-interview-management.md
    │   ├── 02-online-interview.md
    │   └── 03-resume-talent.md
    ├── 02-architecture.md            # v3.0 最终架构
    └── 03-development.md             # v3.0 自包含开发文档
```

## 建议阅读顺序

1. **需求基线**：读 `docs/01-requirements/` 三份题目，确认三个子系统的边界与验收范围。
2. **最终架构**：读 [`docs/02-architecture.md`](docs/02-architecture.md)，确认 v3.0 技术路线：
   - vLLM Qwen2.5-7B-AWQ 主推理，云端/ Ollama / Mock 降级
   - Java 业务 + Python/FastAPI AI 编排 + ai-worker / mineru-worker
   - LiveKit SFU + Egress、1v1 / 1v5、ASR 20 路容量契约
   - 16 周计划、三人分工、runbook 与验收
3. **开发落地**：读 [`docs/03-development.md`](docs/03-development.md)，按服务/数据库/API/鉴权/AI/ASR/在线面试/部署章节分工开发：
   - 表清单与关键约束、核心 DDL 约定、API 端点与错误码
   - 五角色 RBAC、权限码、数据范围、Refresh Token Key
   - AI 任务/简历流水线/ES mapping/匹配/纪要/异步评分
   - ASR WSS 协议与容量契约、LiveKit 房间/录制/笔记/防作弊
   - 部署、验收口径与 W1 冻结清单

## 文档定位

| 文档 | 定位 | 适合先看什么 |
|---|---|---|
| `docs/01-requirements/` | 需求基线 | 三个子系统的题目要求与验收范围 |
| [`docs/02-architecture.md`](docs/02-architecture.md) | **当前架构基线（v3.0）** | vLLM/FastAPI/硬件/ASR/16 周/分工 |
| [`docs/03-development.md`](docs/03-development.md) | **v3.0 自包含开发文档** | 服务/表/API/权限/AI/ASR/部署/验收 |

## 当前待确认的关键决策

1. **GPU 可用性与 vLLM 显存**：v3.0 按 vLLM 主档编写（Qwen2.5-7B-AWQ / 24GB）；`max-model-len 16384` + `max-num-seqs 24` 的真实显存/并发需 W1 压测；拿不到 GPU 则切云主档，只改 base-url。
2. **FastAPI 边界**：v3.0 已定为 Python/FastAPI ai-service（LLM/ASR/Embedding 收口），需和导师确认与“FastAPI 做 AI 处理”的口径一致。
3. **“1 对 5”人数口径**：按“1 位面试官 + 最多 5 位候选人 = 6 人”设计，需在需求评审确认。
4. **ASR 容量**：单 ai-worker 目标 20 路；基线 5×1v1 + 5×1v5 = 40 路音频流，超出部分只保证会后转写；若要求全量实时字幕，需扩容并重测。
5. **答辩硬件与网络**：应用节点内存、GPU 显存、LiveKit UDP/TURN 端口、校园网跨网连通性。
6. **被裁剪/降级的需求**：Offer 审批、异步录播、批量排期、邮箱收简历、人才画像、人脸核验、虚拟背景/白板等，需要在需求评审时明确优先级；实时字幕按 20 路容量契约降级。

## 说明

- 早期路线方案、重复架构稿与旧详细设计附录已移除；当前只有 v3.0 架构 + 自包含开发文档一套基线，Git 历史仍可追溯。
- 文档中的端口、版本、容量数字为设计建议，落地前需以实际硬件、网络、模型版本压测校准。
- 如需提交修改，请先更新接口/事件/数据库契约，再改实现与测试。
