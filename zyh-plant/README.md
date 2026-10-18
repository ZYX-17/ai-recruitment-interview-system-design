# 招聘面试系统 · 毕业设计总体方案

> 版本：v1.0  
> 日期：2026-10-17  
> 依据：三份题目需求文档（面试管理 / 智能简历与人才管理 / 在线面试）  
> 技术基线：Vue 3 + Java 微服务 + FastAPI + vLLM + MySQL + Elasticsearch

---

## 1. 一页纸结论

### 1.1 系统定位

面向企业招聘场景的一体化平台，由三个子系统组成，共用一套账号、权限、数据字典和基础设施：

| 子系统 | 核心目标 | 主要使用者 |
|---|---|---|
| 面试管理子系统 | 把「投递 → 筛选 → 面试 → 评价 → Offer → 入职」流程化、可追踪 | HR、面试官、用人经理 |
| 智能简历与人才管理子系统 | 多渠道简历聚合、结构化解析、查重去重、人岗匹配、人才库盘活 | HR、招聘负责人 |
| 在线面试子系统 | 1v1 / 1v5 实时视频面试、异步录播面试、录制回放、AI 纪要、防作弊 | HR、面试官、候选人 |

三个子系统不是三个独立系统：候选人和岗位是共享主数据，面试流程是业务主线，在线面试是面试流程的一种执行形态。

### 1.2 技术架构结论

```
浏览器（Vue 3）
   │ HTTPS / WSS
Nginx（TLS 终止）
   │
Spring Cloud Gateway ── 统一鉴权、路由、限流
   │
├── auth-service        统一登录、令牌、SSO
├── system-service      用户/角色/菜单/按钮权限/RBAC
├── interview-service   岗位、投递、流程、排期、评价、Offer
├── resume-service      简历、解析任务、查重、匹配、人才库
├── room-service        面试房间、录制、纪要、防作弊、异步面试
├── notify-service       邮件/短信/站内信
├── file-service        MinIO 文件与预签名
└── FastAPI AI 服务     解析编排、Embedding、RAG、LLM 调用、ASR
        │                 （内部 API 由业务服务调用；ASR WSS 由 Nginx 直达）
        ├── vLLM（OpenAI 兼容）
        ├── BGE-M3（向量化）
        ├── FunASR（实时转写）
        └── MinerU（PDF/图片解析）

媒体面（与 Java 控制面解耦）：
浏览器 ◀── WebRTC ──▶ LiveKit SFU ◀── Egress ──▶ MinIO
```

**最关键的一条原则：音视频媒体流不经过 Java 服务。** Java 只负责房间控制、令牌、元数据与业务状态；媒体由 LiveKit SFU 转发。这是满足 1v1 / 1v5 并发和录制需求的前提。

### 1.3 数据与中间件

| 组件 | 用途 |
|---|---|
| MySQL 8.0 | 业务主数据；**每个微服务独立 schema**，禁止跨库 JOIN |
| Redis 7 | 登录态、权限缓存、验证码、分布式锁、房间在线状态、限流 |
| Elasticsearch 8 | 简历/JD/转写/题库的全文 + 向量混合检索，兼作向量库 |
| MinIO | 简历原件、解析产物、面试录像、异步面试视频、防作弊证据 |
| RocketMQ 5 | 跨服务领域事件；Outbox + 消费幂等 |
| Nacos | 服务注册、配置中心 |
| Sentinel | 网关与服务限流、熔断降级 |
| LiveKit + Egress | WebRTC SFU、录制、屏幕共享 |
| vLLM / FunASR / BGE / MinerU | 大模型推理、语音转写、向量化、文档解析 |

### 1.4 统一鉴权与权限（需求 3）

- 一个认证中心 `auth-service`，签发 RS256 JWT；`gateway` 统一验签并注入用户身份头。
- 账号分两类：`EMPLOYEE`（管理员/HR/面试官/用人经理）与 `CANDIDATE`（候选人）。
- RBAC：用户 → 角色 → 菜单/按钮权限；管理员在「角色管理」里勾选菜单树即可完成授权，前端据此生成动态路由，后端用权限码做接口级校验。
- **菜单可见性不等于接口安全**：每个接口都必须有 `@PreAuthorize` 权限码；前端按钮用 `v-perm` 控制。
- 数据范围：`ALL / DEPT / DEPT_AND_CHILD / SELF / CUSTOM`，通过注解 + MyBatis 拦截器落地；候选人和面试官的「只看自己的」在查询层显式约束。

### 1.5 微服务与协作边界（需求 1）

| 服务 | 端口 | 数据库 schema | 数据所有权 |
|---|---|---|---|
| gateway | 8080 | — | 只做网关 |
| auth | 8081 | `recruit_auth` | 登录日志、令牌（Redis） |
| system | 8082 | `recruit_system` | 用户、角色、菜单、部门、字典、配置、操作日志 |
| interview | 8083 | `recruit_interview` | 岗位、投递、流程实例、排期、评价、Offer |
| resume | 8084 | `recruit_resume` | 候选人、简历、标签、查重、筛选规则、匹配记录、人才库 |
| room | 8085 | `recruit_room` | 房间、参与人、录制、聊天、纪要、转写、防作弊、异步面试、题库 |
| notify | 8086 | `recruit_notify` | 消息模板、站内信、发送日志 |
| file | 8087 | `recruit_file` | 文件元数据、分片上传会话 |
| AI（FastAPI） | 9010 | `recruit_ai` | AI 任务、提示词版本、调用日志 |

跨服务只使用 **ID 引用 + 展示快照 + 领域事件**，不使用跨库外键、不跨库 JOIN。详细契约见 [01-architecture.md](01-architecture.md)。

### 1.6 在线面试并发结论（需求 6）

- 媒体面：LiveKit SFU，单房间最多 6 人（1 位主持人 + 最多 5 位参与者），同时支持「1 对 5 群面」与「1 位候选人 + 5 位面试官 Panel」两种语义，由房间角色和布局配置切换。
- 控制面：房间令牌、候场、踢人、静音、录制启停走 `room-service`；按 600 名参与者、50~100 个并发房间估算，控制面只有几十 RPS，瓶颈不在 Java，而在 SFU 出口带宽、录制编码和 AI 转写 GPU。
- 媒体面用 Simulcast + Dynacast：1v5 单房间出口从「全高清互看」的约 45 Mbps 降到约 12~20 Mbps；100 房间 / 600 人建议 2 台 SFU + 10GbE，并做容量测试验证。详细计算、降级策略与测试方法见 [05-online-interview.md](05-online-interview.md)。

### 1.7 文档索引

| 文档 | 内容 |
|---|---|
| [01-architecture.md](01-architecture.md) | 总体架构、微服务边界、调用关系、事件与一致性、三端协作契约 |
| [02-database.md](02-database.md) | MySQL 分库表设计、索引、枚举字典、ES 索引与混合检索、种子数据 |
| [03-auth-rbac.md](03-auth-rbac.md) | 统一登录、JWT/Redis、网关鉴权、RBAC、菜单勾选、动态路由、数据范围、权限码清单 |
| [04-ai.md](04-ai.md) | vLLM、FastAPI 模块、MinerU 解析流水线、分块、Embedding、RAG、匹配、面试 AI、异步任务 |
| [05-online-interview.md](05-online-interview.md) | LiveKit、房间生命周期、录制、防作弊、AI 转写、异步面试、1v1/1v5 容量与压测 |
| [06-api.md](06-api.md) | REST 约定、接口清单、权限码、关键时序、WebSocket 协议、错误码 |
| [07-deployment-plan.md](07-deployment-plan.md) | 部署拓扑、Docker Compose、GPU 规划、测试验收、三人分工与 16 周计划、论文/答辩素材 |
| [08-requirements-traceability.md](08-requirements-traceability.md) | 三份原始需求逐条映射到模块/表/接口/测试，防止遗漏 |

### 1.8 默认假设

1. 团队 3 人，对应三份题目；一人兼平台底座与面试管理，一人负责简历人才，一人负责在线面试。
2. 演示环境至少 1 张 24GB GPU；推荐 24GB（LLM）+ 16GB（MinerU/ASR）或单卡 48GB。无 GPU 时提供 Mock/降级模式。
3. 部署在校内服务器或一台高配主机，Docker Compose 为主，K8s 作为可选演进。
4. 短信/邮件/微信通道使用适配器 + Mock 模式，答辩演示不依赖真实第三方账号。
5. 浏览器端摄像头/麦克风要求 HTTPS（localhost 除外），校园网需要 TURN/TCP 443 兜底。
6. 不涉及多租户、薪酬核算、入职后的 HR 全模块；Offer 之后的「入职资料收集」只做最小闭环。

### 1.9 MVP 与可选功能

| 优先级 | 范围 |
|---|---|
| **MVP（必须）** | 统一登录/RBAC/菜单勾选、用户角色管理、岗位与投递、面试流程与排期、评价表与对比、Offer 审批、简历上传与 MinerU 解析、结构化抽取、查重去重、标签、人岗匹配、筛选规则、人才库与跟进、1v1 与 1v5 在线房间、候场、屏幕共享、房间录制与回放、聊天、AI 实时转写与纪要、AI 评价草稿、异步录播面试、基础防作弊（切屏/人脸缺席/多人脸）、邮件/站内信通知 |
| **增强（可选）** | 虚拟背景、美颜、降噪、白板协同、共享代码编辑器、活体检测、人脸比对、录屏上传、微信通知、人才地图、招聘漏斗大屏、K8s 部署、多语言、移动端适配 |
| **不建议做** | 自研 SFU、自研 ASR 模型、复杂微前端、多租户、全流程薪酬/考勤、真实第三方招聘网站爬虫 |

> 答辩时「做了多少」不如「链路完整、指标可测、设计有取舍」。先把 MVP 打通，再挑 1~2 个增强点做深（推荐：表格感知的简历分块 + 1v5 容量压测）。

---

## 2. 三个子系统的需求映射

| 原始需求 | 落地模块 | 主要服务 | 设计文档 |
|---|---|---|---|
| 面试流程配置（初筛→笔试→一面→二面→HR 面→Offer） | 流程模板、阶段实例、状态机 | interview | 01 / 02 / 06 |
| 面试安排（时间、面试官、会议室/线上链接） | 排期、冲突校验、日历、批量安排 | interview | 01 / 02 / 06 |
| 面试通知（短信/邮件自动推送） | 模板 + 事件 + 重试 | notify | 02 / 06 |
| 面试官协同（评价表、打分、对比视图） | 评价模板、评价记录、聚合对比 | interview | 02 / 06 |
| 状态流转（通过/待定/淘汰/转岗） | 状态机 + 流转日志 + 规则 | interview | 01 / 02 |
| Offer 审批与候选人确认 | Offer、审批任务、候选人响应 | interview | 02 / 06 |
| 多渠道简历聚合 | 上传、邮箱收取、渠道适配器 | resume / file | 01 / 04 |
| MinerU 解析（表格/公式/图片标题） | 解析流水线、Markdown 产物 | AI + resume | 04 |
| 结构化入库 | LLM 结构化抽取 + 人工校正 | resume / AI | 02 / 04 |
| 简历去重 / 查重 | 证件/联系方式精确匹配 + 向量相似 | resume / AI / ES | 04 |
| 人岗匹配评分、AI 筛选 | JD 解析、混合检索、LLM 评分 | resume / AI | 04 |
| 自动分类/标签 | 规则 + LLM 标签体系 | resume | 04 |
| 筛选规则引擎 | JSON 规则 DSL + 解释 | resume | 02 / 04 |
| 人才库、标签检索、跟进提醒 | 人才池、跟进记录、定时任务 | resume / notify | 02 |
| AI 候选人画像 | 简历 + 面试评价聚合生成 | resume / AI | 04 |
| WebRTC 实时音视频 | LiveKit SFU + Vue SDK | room / LiveKit | 05 |
| 录制、回放、存档 | LiveKit Egress → MinIO | room / file | 05 |
| 屏幕共享、白板 | LiveKit 屏幕轨 + 可选 Yjs 白板 | room | 05 |
| 虚拟背景/美颜/降噪 | 客户端 Track Processor / WebAudio | web | 05 |
| 面试笔记、AI 纪要 | 实时 ASR + 建议 + 总结 | room / AI | 04 / 05 |
| 防作弊 | 人脸核验、切屏、多人脸、录屏 | room / AI | 05 |
| AI 面试助手（关键问题提示） | 转写上下文 + 题库 RAG | AI | 04 |
| AI 面试评价 | 转写 + 评分维度 → 草稿 | AI | 04 |
| 异步 AI 面试（录播） | 题目集、录制上传、AI 评分、人工复核 | room / AI | 04 / 05 |
| 面试房间管理（1v1 / 群面） | 房间、角色、候场、权限 | room | 05 |
| 并发 1v1 / 1v5 | SFU 容量模型、Simulcast、压测 | room / LiveKit | 05 / 07 |

---

## 3. 角色与权限速览

| 角色 | 说明 | 默认数据范围 |
|---|---|---|
| `SUPER_ADMIN` | 平台管理员，拥有全部权限（含角色/菜单配置） | ALL |
| `HR_ADMIN` | 招聘管理员，管理岗位、流程、简历、面试、Offer | ALL |
| `HR` | 招聘专员，负责自己部门/自己的岗位与候选人 | DEPT / SELF |
| `INTERVIEWER` | 面试官，只看分配给自己的面试与候选人材料 | SELF（显式查询） |
| `DEPT_MANAGER` | 用人经理，看本部门流程、评价与审批 | DEPT_AND_CHILD |
| `CANDIDATE` | 候选人，只使用候选人门户 | SELF |

默认角色与菜单矩阵、权限码清单见 [03-auth-rbac.md](03-auth-rbac.md)。

---

## 4. 三个人的协作方式（摘要）

- **契约先行**：第 2 周冻结数据库表、REST 接口、事件 Schema、权限码；后续变更走评审。
- **数据所有权**：每张表只有一个服务可写；跨服务读用 API/缓存/快照，不直连别人数据库。
- **认证统一**：只有 `auth-service` 发令牌，只有 `system-service` 管 RBAC；各业务服务只做权限码校验。
- **前端分工**：`web-admin` 公共壳与动态路由由 A 维护；B、C 各自提交页面模块；`web-candidate` 按业务归属拆分页面。
- **每周集成**：周一合主线、周三联调、周五演示；每天至少一次全链路冒烟。
- **AI 服务共用**：一个 FastAPI 仓库，B 负责简历/检索/匹配模块，C 负责 ASR/面试模块，A 负责 LLM 网关与部署。

详细分工、里程碑、验收指标见 [07-deployment-plan.md](07-deployment-plan.md)。

---

## 5. 关键取舍（答辩可讲）

1. **不自研 SFU**：WebRTC 的媒体转发、拥塞控制、Simulcast 工程量大，采用 LiveKit 把精力放在业务与 AI 上；Java 只做控制面，架构职责清晰。
2. **ES 同时承担全文与向量检索**：需求指定 ES 作向量库；简历/JD 场景需要「关键词 + 语义 + 元数据过滤」，ES 8 的 BM25 + kNN + RRF 正好覆盖，避免再引入 Milvus。
3. **LLM 只做「建议」，不做「决策」**：匹配分、评价、画像都保留证据与人工确认，规避幻觉与招聘公平性风险。
4. **异步优先**：解析、匹配、总结、异步面试评分全部任务化；面试和录制不依赖 AI 可用性，AI 故障只降级不阻断。
5. **逻辑外键 + 事件最终一致**：不引入 Seata 强事务，用 Outbox、幂等消费、状态机与补偿换取微服务解耦；论文里可以对比 AT/TCC 说明取舍。
6. **统一账号、两个前端**：HR 后台与候选人门户安全边界、交互差异大，拆成两个 Vue 应用共用 `packages/shared`，但共用同一认证中心与 RBAC。

---

## 6. 快速开始（设计落地顺序）

1. 按 [02-database.md](02-database.md) 建 8 个 schema，执行 Flyway 脚本，导入角色/菜单/字典种子。
2. 按 [01-architecture.md](01-architecture.md) 搭 Nacos + Gateway + auth + system，跑通登录与动态菜单。
3. 按 [06-api.md](06-api.md) 冻结 OpenAPI，三个子系统并行开发。
4. 按 [04-ai.md](04-ai.md) 部署 vLLM/BGE/ASR/MinerU，先用 Mock 模式联调业务。
5. 按 [05-online-interview.md](05-online-interview.md) 部署 LiveKit + Egress，先跑通 1v1，再做 1v5 与录制。
6. 按 [07-deployment-plan.md](07-deployment-plan.md) 做压测、验收、论文与答辩。
