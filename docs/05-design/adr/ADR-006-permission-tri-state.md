# ADR-006：权限三态采用「一行一码 + type 主形态」

- 状态：已接受
- 对应裁决：`docs/4 §11 D26`
- 日期：2026-10-19 裁决 / 2026-11-02 定稿
- 决策人：成员 A + 评审组
- 关联：`docs/4 §1.1`、`§5.9`、`§5.10`、附录 C、`docs/05-design/p0-5-permission-registry.csv`

## 背景

同一能力在前端可能是菜单、按钮，在服务端是 API。若每个形态各建一行，会出现
`interview:job:create`（BUTTON）与 `interview:job:create`（API）两行同码，与 `permission_code` 唯一约束冲突；
若只建 API 行，菜单树又无法表达父子关系。

## 选项

| 选项 | 说明 | 取舍 |
|---|---|---|
| A. 一码三行（MENU/BUTTON/API） | 每形态独立行 | 唯一键失效；角色授权要选三行，易漏 |
| B. 一行一码，`type` 为主形态，BUTTON 可带 `api_pattern` | 单一授权单元 | 需要约定 `api_pattern` 的匹配规则 |
| C. 只做 API 权限 | 菜单由前端写死 | 动态菜单失效，与 §5.10 角色矩阵冲突 |

## 结论

采用 B：

- `iam_permission.permission_code` 全局唯一，一行一码；
- `type ∈ MENU/BUTTON/API` 表示主形态：资源查看类为 `MENU`，操作类为 `BUTTON`；
- `BUTTON` 行可携带 `api_pattern`（`METHOD /path`，支持 `{id}` 占位与 `|` 分隔多路径），供服务端注解/拦截器校验；
- 不重复建 BUTTON/API 两行；纯内部调用（`/internal/**`）不建权限码，用 `X-Internal-Token`；
- 角色授权只针对权限码，前端隐藏按钮不是安全边界。

## 影响

- seed（P1-7）：按 `docs/05-design/p0-5-permission-registry.csv` 生成 `iam_permission` 与 `iam_role_permission`；
- 菜单树（P2-4）：`parent_code` 建分组节点，分组节点不是权限码（`permission_code` 为空或 `type=MENU` 的目录行）；
- 服务端：`@PreAuthorize("hasAuthority('code')")` 或等价拦截；`api_pattern` 只做启动期校验与扫描，不替代注解；
- 前端：`v-permission` 指令按 code 控制按钮；菜单由 `/api/auth/menus` 下发；
- 校验：启动时扫描 Controller 上出现的权限码，未在注册表登记则启动失败（fail-fast）。

## 回滚方式

1. 唯一索引 `uk_permission_code` 保留；
2. 若某码确需映射多 API，新增 `iam_permission_api(permission_id, method, path)` 关联表，**不改唯一键、不拆行**；
3. 回滚 seed 用 Flyway 反向脚本或重建库，禁止手工删 `iam_role_permission` 造成悬空授权。