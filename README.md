# AI 招聘面试系统（毕业设计）

面向中小企业招聘场景的一体化面试系统，覆盖：

- **智能简历与人才管理**：PDF/Word/图片解析、结构化入库、标签、查重去重、人岗匹配、人才库、跟进提醒
- **面试管理**：自定义流程、候选人状态机、排期冲突校验、面试官协同评价、Offer、AI 纪要
- **在线面试**：LiveKit WebRTC 1v1 / 1v5、屏幕共享、聊天、录制回放、实时转写、异步录播、防作弊埋点
- **平台治理**：统一登录、RBAC、动态菜单、按钮/API 权限码、数据范围、审计

技术基线：**Vue 3 + Java 微服务（Spring Boot 3.2 / Spring Cloud Alibaba）+ Python/FastAPI AI 编排 + vLLM + MySQL 8 + Elasticsearch 8.13 + Redis 7.2 + RabbitMQ 3.13 + MinIO + LiveKit**。

> 仓库内含可运行代码骨架（v3.1）与设计文档；示例账号、密钥均为占位符/虚构数据。

## 仓库结构

```
.
├── backend/                 # Java 微服务（Maven 多模块，JDK 17）
│   ├── pom.xml              # 父 pom（SB 3.2.5 / SC 2023.0.1 / SCA 2023.0.1.0）
│   ├── common/              # Result/错误码/traceId/雪花ID/Redis Key 契约
│   ├── contracts/           # 事件信封/EventTypes（跨服务契约）
│   ├── api-gateway/         # 统一网关（8080）：路由/traceId/内部接口屏蔽
│   ├── auth-service/        # IAM（8081，库 recruit_auth）
│   ├── interview-service/   # 面试管理（8083，库 recruit_interview）——Owner A
│   ├── resume-service/      # 简历与人才（8084，库 recruit_resume）——Owner B
│   ├── room-service/        # 在线面试（8085，库 recruit_room）——Owner C
│   ├── notify-service/      # 通知（8086，库 recruit_notify）——Owner A
│   └── file-service/        # 文件（8087，库 recruit_file）——Owner B
├── ai/
│   ├── ai-service/          # FastAPI（9010）：LLM 路由/Prompt/任务/审计——Owner B
│   ├── ai-worker/           # FunASR + BGE（8001）——Owner B
│   └── mineru-worker/       # MinerU（8002）——Owner B
├── frontend/                # Vue3 单 SPA（Owner：C）
├── infra/                   # docker-compose base/app/gpu + nginx
├── scripts/                 # smoke / deploy / rollback
├── docs/                    # 设计文档（v3.1）
│   ├── 01-requirements/     # 三份原始题目
│   ├── 2.架构设计方案-招聘面试系统微服务架构.md
│   └── 3.开发文档-招聘面试系统.md
└── sql/                     # 建库建表 DDL 母版（7 库 71 表，见 sql/README.md）
```

## 快速开始（W1 骨架）

```bash
# 1. 中间件（自动建 7 库；建表执行 sql/01~07 或各服务 Flyway）
docker compose -f infra/docker-compose.base.yml up -d

# 2. Java 后端（任一服务）
cd backend && mvn -q -DskipTests -pl auth-service -am spring-boot:run

# 3. Python AI（三工程）
cd ai/ai-service && pip install -r requirements.txt && python -m app.main

# 4. 前端
cd frontend && npm install && npm run dev

# 5. 冒烟
./scripts/smoke.sh
```

## 分支与协作（见 docs 第 8 章协作契约）

- `main`：发布/冻结（必须 PR + 1 评审 + CI 绿；答辩前 `demo-frozen`）
- `dev`：**默认分支，三人日常直推**（每日 `git pull --rebase`，禁 force push，周五集成打 tag `dev-w<N>`）
- 例外开短分支 `feat|fix|chore|docs|sql/<domain>-<short>`：大改动（room 整块/common-api 契约/compose/sql00）、跨周未完成、dev→main 发版

## 文档定位

| 文档 | 定位 |
|---|---|
| `docs/01-requirements/` | 三份原始题目（需求基线） |
| `docs/2.架构设计方案-招聘面试系统微服务架构.md` | **架构基线（v3.1）** |
| `docs/3.开发文档-招聘面试系统.md` | **自包含开发文档（v3.1）** |
| `sql/` | 建库建表 DDL 母版 + 可能新增文件清单 |

## 当前待确认的关键决策

1. **GPU 可用性与 vLLM 显存**：按 vLLM 主档（Qwen2.5-7B-AWQ / 24GB）编写；拿不到 GPU 则切云主档，只改 base-url。
2. **“1 对 5”人数口径**：按“1 面试官 + 最多 5 候选人 = 6 人”设计。
3. **ASR 容量**：单 ai-worker 目标 20 路；超出部分会后补转写。
4. **答辩硬件与网络**：应用节点内存、GPU 显存、LiveKit UDP/TURN、校园网跨网连通性。
5. **裁剪/降级需求**：Offer 审批、异步录播、批量排期、邮箱收简历等优先级在需求评审确认。
