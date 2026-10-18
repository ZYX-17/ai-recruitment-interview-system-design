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
├── 1.题目-招聘面试系统之面试管理子系统的设计与实现(1).md
├── 1.题目-招聘面试系统之在线面试子系统的设计与实现(1).md
├── 1.题目-招聘面试系统之智能简历和人才管理子系统(1).md
├── qyf-plant/                         # 架构与开发路线指导书（vLLM 主线）
│   ├── interview_system_plan.md
│   ├── interview_system_sources_README.txt
│   └── interview_system_plan_assets/
│       ├── architecture.png
│       └── architecture.dot
└── zyh-plant/                         # 详细设计文档（数据库/接口/鉴权/AI/在线面试）
    ├── README.md
    ├── 01-architecture.md
    ├── 02-database.md
    ├── 03-auth-rbac.md
    ├── 04-ai.md
    ├── 05-online-interview.md
    ├── 06-api.md
    ├── 07-deployment-plan.md
    └── 08-requirements-traceability.md
```

## 建议阅读顺序

1. 先看三份原始题目，确认三人的子系统边界与验收范围。
2. 读 [`qyf-plant/interview_system_plan.md`](qyf-plant/interview_system_plan.md)，建立总体技术路线：
   - vLLM 作为统一推理引擎
   - Java 负责业务/权限/审计，FastAPI + Python worker 负责 MinerU/BGE/ASR
   - 精简服务边界，P0/P1/P2 分级交付
   - LiveKit SFU + Egress、1v1 / 1v5 容量目标、第一周技术 spike
3. 再读 [`zyh-plant/README.md`](zyh-plant/README.md) 及其子文档，作为实现细节参考：
   - 数据库表、索引、枚举、ES mapping
   - 统一鉴权、角色/菜单/按钮/API 权限、数据范围
   - REST/WS 接口、错误码、关键时序
   - AI 流水线、MinerU 分块、BGE、混合检索、匹配、ASR、纪要
   - LiveKit 房间、录制、防作弊、1v5 容量与压测
   - Docker Compose、GPU 规划、16 周分工与验收
4. 用 [`zyh-plant/08-requirements-traceability.md`](zyh-plant/08-requirements-traceability.md) 做需求覆盖核对，确认哪些是 MVP、哪些是增强项、哪些明确不做。

## 两套文档的定位

| 文档 | 定位 | 适合先看什么 |
|---|---|---|
| `qyf-plant/` | 架构与开发路线指导书 | 技术选型、AI 边界、服务拆分、P0/P1/P2、第一周 spike |
| `zyh-plant/` | 详细设计文档 | 数据库 DDL、接口清单、权限码、AI 流水线、在线面试容量、部署与验收 |

两套文档不是互相替代关系：前者偏“做什么、怎么分期、怎么验证”，后者偏“表怎么建、接口怎么定、权限怎么落、容量怎么算”。落地时建议先统一表名/服务名与接口契约，再分别引用。

## 当前待确认的关键决策

1. **vLLM 是否作为硬性主推理引擎**（当前 `qyf-plant` 方案为 vLLM 主线；无 GPU 时需要共享推理/租用 GPU/Mock 策略）。
2. **FastAPI 的边界**：是作为所有 AI 能力的统一网关，还是只承接 MinerU / BGE / ASR 等 Python 生态任务。
3. **“1 对 5”人数口径**：按“1 位面试官 + 最多 5 位候选人 = 6 人”设计，需在需求评审确认。
4. **答辩硬件与网络**：应用节点内存、GPU 显存、LiveKit UDP/TURN 端口、校园网跨网连通性。
5. **被裁剪/降级的需求**：Offer 审批、批量排期、邮箱收简历、实时字幕、人才画像、人脸核验、虚拟背景/白板等，需要在需求追踪矩阵中明确优先级。

## 说明

- 架构图源文件在 `qyf-plant/interview_system_plan_assets/architecture.dot`，可用 Graphviz 重新生成。
- 文档中的端口、版本、容量数字为设计建议，落地前需以实际硬件、网络、模型版本压测校准。
- 如需提交修改，请先更新接口/事件/数据库契约，再改实现与测试。
