# ADR-002：Refresh Token 走 HttpOnly Cookie

- 状态：已接受
- 对应裁决：`docs/4 §11 D8`（与 D13 CORS 白名单联动）
- 日期：2026-10-19 裁决 / 2026-11-02 定稿
- 决策人：成员 A + 成员 C（前端）
- 关联：`docs/4 §1.2 A-03`、`§2.2`、`§5.2.2`

## 背景

Refresh Token 放响应体时，前端必须自行持久化（localStorage），一旦 XSS 即长期失守；
放 Cookie 又涉及跨域、SameSite、网关是否透传 `Set-Cookie`、本地调试便利性。
文档此前两种写法并存，成员 C 无法确定前端实现。

## 选项

| 选项 | 说明 | 取舍 |
|---|---|---|
| A. 响应体返回 | 前端存内存/localStorage | 实现简单；XSS 风险高，不符合安全基线 |
| B. HttpOnly Cookie | 浏览器自动携带，JS 不可读 | 需要 CORS 凭据 + 网关透传；本地调试略麻烦 |
| C. 两者都返回 | 兼容两套前端 | 必然长期分叉，且等于放弃 B 的安全收益 |

## 结论

采用 B，并固定 Cookie 属性：

- `HttpOnly`；`Secure`（非 local 环境）；`SameSite=Strict`；`Path=/api/auth`；`Max-Age` 与 Refresh 有效期一致。
- 响应体仅在 `spring.profiles.active=local` 时返回 `refreshToken`，便于 curl/Postman 调试。
- Access Token 仍走 `Authorization: Bearer`，不入 Cookie。

## 影响

- 前端（C）：axios 统一 `withCredentials: true`；刷新失败按 `AUTH1002/AUTH1003` 跳登录；禁止把 Refresh 写入 localStorage。
- 网关：CORS 必须配置具体来源 + `allowCredentials`，禁止 `*`（D13）；不得剥离 `Set-Cookie`。
- 服务端：`/api/auth/refresh` 从 Cookie 读取；轮换时同时下发新 Cookie。
- 安全：`SameSite=Strict` + 仅 POST + 自定义头校验，降低 CSRF 面；`Path` 限定减少泄露面。
- 测试：集成测试需带 Cookie 容器，不能只断言响应体。

## 回滚方式

保留配置开关 `auth.refresh.body-enabled`（默认 `false`）：
1. 若前端暂时无法使用 Cookie，可在非生产环境打开开关，响应体临时返回 Refresh；
2. Cookie 逻辑不删除，开关只影响「是否额外回填响应体」；
3. 生产环境禁止打开；开关需在下个迭代删除。
数据库无变更，回滚不涉及数据迁移。