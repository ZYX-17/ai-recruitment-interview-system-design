# 本地服务栈部署（A 域，P12 验证用）

> 目标：在本机把 `auth-service / interview-service / notify-service / api-gateway` 跑起来，
> 用于 `P12-2` 集成链路、`P12-3` 并发压测、`P12-4` 安全测试。**不需要 Nacos。**

## 1. 组件与端口

| 组件 | 必需 | 端口 | 说明 |
|---|---|---|---|
| MySQL 8.0 | 是 | 3306 | 3 个库：`recruit_auth`、`recruit_interview`、`recruit_notify` |
| Redis 7.x | 是 | 6379 | 登录态、幂等键、限流、权限缓存 |
| RabbitMQ 3.13 | 是 | 5672 | Outbox 投递、通知消费；队列由 notify 启动时自动声明 |
| auth-service | 是 | 8081 | 登录/注册/权限/门户门禁 |
| interview-service | 是 | 8083 | 岗位/投递/排期/评价/Offer/统计/Outbox |
| notify-service | 是 | 8086 | 站内信与邮件（本地关闭邮件） |
| api-gateway | 是 | 8080 | 统一入口；`local` profile 用静态发现替代 Nacos |
| room-service | 否 | 8085 | 目前只有骨架，房间能力仍按 Mock 处理 |

## 2. 一次性准备

### 2.1 建库（只建空库，表由 Flyway 创建）

```bash
mysql -h127.0.0.1 -uroot -p < sql/00_create_databases.sql
```

### 2.2 生成 JWT 密钥（Git Bash）

```bash
bash scripts/dev/gen-jwt-keys.sh
# 产物：infra/secrets/jwt-private.pem（只给 auth）、infra/secrets/jwt-public.pem（auth + gateway）
```

### 2.3 准备环境变量

```powershell
Copy-Item scripts/dev/env.local.example.ps1 scripts/dev/env.local.ps1
# 修改 MySQL/Redis/RabbitMQ 连接信息与 SEED_ADMIN_PASSWORD（>=8 位）
```

`env.local.ps1` 已被 `.gitignore` 忽略，不会入库。

## 3. 启动

```powershell
powershell -ExecutionPolicy Bypass -File scripts/dev/start-local.ps1
```

脚本行为：

1. 离线打包 `auth / interview / notify / gateway`（`-SkipBuild` 可跳过）；
2. 按 `auth -> interview -> notify -> gateway` 顺序启动，日志在 `logs/local/<service>.log`；
3. `auth` 与 `interview` 使用 `demo` profile，自动加载 `db/demo/V9_0__seed_demo.sql`；
4. 轮询 `/actuator/health`，全部 200 后返回。

停止：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/dev/stop-local.ps1
```

## 4. 验证

```bash
curl -s http://127.0.0.1:8080/actuator/health
curl -s http://127.0.0.1:8081/actuator/health
curl -s http://127.0.0.1:8083/actuator/health
curl -s http://127.0.0.1:8086/actuator/health

SMOKE_PASSWORD=你的SEED_ADMIN_PASSWORD bash scripts/smoke.sh
```

smoke 会检查：4 个健康端点、登录、菜单、统计漏斗、Outbox 列表、门户岗位列表。

### demo 账号（密码均为 `SEED_ADMIN_PASSWORD`）

| 账号 | 角色 | 用途 |
|---|---|---|
| `admin` | SUPER_ADMIN | 管理端、Outbox 管理 |
| `hr01` / `hr02` | HR_ADMIN | 岗位/排期/Offer |
| `interviewer01~03` | INTERVIEWER | 提交评价 |
| `candidate01` / `candidate02` | CANDIDATE | 门户投递/面试 |

演示数据要点：岗位 `50001/50002` 已发布；`candidate01` 已投递 `51001/51006`；
面试 `90001` 开始时间为启动时刻 +2 小时；Offer `92001/92002` 已存在。

## 5. 关键环境变量

| 变量 | 服务 | 说明 |
|---|---|---|
| `SEED_ADMIN_PASSWORD` | auth | 必填；为空或少于 8 位时 auth 拒绝启动 |
| `JWT_PRIVATE_KEY_PATH` / `JWT_PUBLIC_KEY_PATH` | auth / gateway | 必须是同一对 RS256 密钥 |
| `INTERNAL_TOKEN` | auth / interview / notify | 三处必须一致，否则权限查询与内部接口 403 |
| `AUTH_SERVICE_URL` | interview / notify | `http://127.0.0.1:8081`，绕过服务发现直连 |
| `MAIL_ENABLED` | notify | 本地设 `false`，邮件只打日志，站内信仍落库 |
| `COOKIE_SECURE` | auth | 本地 HTTP 设 `false`，否则 Refresh Cookie 不会被发送 |
| `PHONE_ENC_KEY` | auth | 可选；不配置则不能写入手机号 |

## 6. 常见问题

- **auth 启动即退出**：99% 是 `SEED_ADMIN_PASSWORD` 未设置或少于 8 位，看 `logs/local/auth-service.log`。
- **登录后调业务接口 403**：检查 `INTERNAL_TOKEN` 是否三处一致；再确认 `AUTH_SERVICE_URL` 可达。
- **网关 503**：确认使用 `local` profile（静态发现），并确认被调用服务端口已启动。
- **排期成功但门户看不到房间号**：room-service 目前是骨架，`room.created` 回执仍需 Mock 或后续联调。
- **时间对不上**：连接串固定 `serverTimezone=UTC`，MySQL 会话时区建议保持 UTC。

## 7. 环境就绪后我会做

1. 跑通 `smoke.sh` 并修复真实启动/迁移问题；
2. 补齐并执行 `P12-2` 集成链路（登录 → 投递 → 排期 → 评价 → Offer → 通知）；
3. 执行 `P12-4` 安全用例（越权/重放/注入/头伪造）；
4. 用 `P12-3` 压测脚本验证排期冲突与网关限流，并把结果回填文档。

> 注意：本机端口只用于开发验证，不要暴露到公网；`INTERNAL_TOKEN`、数据库密码仅限本地。
