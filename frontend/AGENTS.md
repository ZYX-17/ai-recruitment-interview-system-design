# AGENTS.md — 前端开发规范（通用版）

> 适用范围：Vue 3 + TypeScript + Vite 的单页应用。
> 读者：参与前端开发的 AI 与工程师。**写代码前先读完本文。**
> 定位：这是一份**规范**，不是项目现状说明书。目录、命名、分层以本文为目标；现有代码逐步靠拢，**不要为了改目录而改目录**。

---

## 0. 规则优先级与参考标准

冲突时按以下顺序取：

1. 明确需求 / OpenAPI / 后端 DTO / Controller / 开发文档
2. 本文件
3. 仓库的 ESLint / Prettier / tsconfig 配置
4. 现有代码风格
5. 个人偏好

仍然冲突时**停下来问**，不要自行选边，更不要“两种都兼容”。

参考标准：

- Vue 官方风格指南（Priority A/B）
- Google TypeScript Style Guide
- Airbnb JavaScript Style Guide
- Alibaba Front-end Coding Guidelines
- Conventional Commits 1.0
- WCAG 2.1 AA
- OpenAPI 3.1

---

## 1. 技术栈与工程基线

默认技术栈：

| 类别 | 选型 |
|---|---|
| 框架 | Vue 3.4+，Composition API |
| 语言 | TypeScript 5+，`strict: true` |
| 构建 | Vite 5+ |
| 路由 | Vue Router 4+ |
| 状态 | Pinia 2+ |
| UI | Element Plus 2+ |
| HTTP | axios（统一封装） |
| 测试 | Vitest + Vue Test Utils + Playwright（项目配置后遵守） |

工程要求：

- Node 版本以 `package.json` 的 `engines` 和 `.nvmrc` 为准；禁止用低于要求的版本构建。
- 包管理器以团队锁定为准（npm/pnpm/yarn 只选一个）；**lockfile 必须提交**。
- 必备脚本：`dev`、`build`（先类型检查再构建）、`lint`、`test`、`preview`。
- 新增/升级依赖必须经确认；禁止直接 import 未在 `dependencies`/`devDependencies` 声明的包（包括传递依赖）。
- 构建配置（Vite、TS、ESLint）修改必须经确认；不要为了解决局部问题改全局配置。

---

## 2. 架构原则

### 2.1 分层与依赖方向

```text
views / components
        ↓
composables / stores
        ↓
api modules
        ↓
request（axios 实例、拦截器）
```

- 依赖只能向下，禁止反向引用。
- 页面不直接使用 axios/fetch；组件不直接拼 URL。
- `utils` 保持纯函数，不依赖 Vue、Pinia、路由。
- `api` 只负责请求与 DTO，不写业务判断、不弹窗、不跳路由。
- `stores` 负责跨页面共享状态；页面局部状态留在组件内。

### 2.2 业务域优先

按业务域组织代码：`auth`、`interview`、`resume`、`room`、`notify`、`file`、`ai`。
同一域的 API、类型、store、页面尽量同名、同域，避免“按技术类型散落一地”。

### 2.3 三条硬规则

1. **契约先行**：先确认端点、字段、枚举、分页、错误码，再写代码。
2. **三次原则**：同一逻辑出现 3 次以上再抽组件/Composable/工具函数。
3. **最小改动**：只改与任务相关的文件；不顺手重构、不批量格式化。

### 2.4 不做过早设计

- 不为“以后可能复用”预留 props、插槽、配置项。
- 不建万能 `BaseTable` / `BaseForm` / `useCrud`。
- 不给 Element Plus 做无行为的二次封装。
- 不为了“解耦”给一个接口再包一层 Service 类。
- 能写 20 行直白代码的，不写成 3 个高阶函数。

---

## 3. 目录结构（推荐基线）

```text
frontend/
├── public/                     # 不经构建的静态资源
├── src/
│   ├── api/
│   │   ├── request.ts          # axios 实例、拦截器、错误归一化
│   │   └── modules/            # 按业务域：auth.ts / interview.ts / room.ts ...
│   ├── assets/                 # 参与构建的图片、字体、图标
│   ├── components/
│   │   ├── base/               # 跨项目基础组件（确有必要时）
│   │   └── business/           # 跨页面业务组件
│   ├── composables/            # useXxx，可复用的有状态逻辑
│   ├── constants/              # 常量、枚举、字典、正则
│   ├── directives/             # 全局指令（能不用就不用）
│   ├── layouts/                # 布局组件
│   ├── plugins/                # Element Plus、监控等初始化
│   ├── router/
│   │   ├── index.ts            # createRouter
│   │   ├── routes/             # 静态路由、动态路由
│   │   └── guards/             # 全局守卫、权限守卫
│   ├── stores/
│   │   ├── index.ts            # createPinia
│   │   └── modules/            # 按业务域：user.ts / room.ts ...
│   ├── styles/
│   │   ├── index.scss          # 全局入口
│   │   ├── variables.scss      # 设计 token
│   │   ├── mixins.scss
│   │   └── reset.scss
│   ├── types/                  # 跨 3+ 模块共享的类型
│   ├── utils/                  # 纯函数工具
│   ├── views/
│   │   └── <module>/           # 业务域，kebab-case
│   │       └── <page>/         # 页面，kebab-case
│   │           ├── index.vue
│   │           └── components/ # 仅该页面使用的组件
│   ├── App.vue
│   └── main.ts
├── tests/
│   ├── unit/
│   └── e2e/
├── AGENTS.md
├── index.html
├── package.json
├── tsconfig.json
└── vite.config.ts
```

说明：

- 页面局部组件放在页面自己的 `components/`，不要放到全局 `src/components/`。
- 只有被 2 个以上页面使用的组件才进 `src/components/business/`。
- `src/components/base/` 只放真正与业务无关的基础组件；**不要**在这里重复实现 Element Plus。
- 跨 3+ 模块共享的类型才进 `src/types/`；否则就近定义在 API 模块或组件旁。
- 不建 `index.ts` barrel（容易循环依赖、影响 tree-shaking）。
- 目录不是一次性重构目标；新增代码按本结构放置，存量逐步迁移。

### 3.1 规模化演进

模块/团队变大时，可从“按类型分层”演进为 feature-first：

```text
src/modules/interview/
├── api.ts
├── components/
├── composables/
├── pages/
├── stores/
└── types.ts
```

无论采用哪种，**依赖方向与命名规则不变**。不要在同一个项目里混用两套结构。

---

## 4. 命名规范

### 4.1 总则

- 名称表达“是什么/做什么”，不用拼音、不用无意义缩写。
- 允许的通用缩写：`id`、`url`、`api`、`http`、`html`、`css`、`db`、`ui`。
- 同一概念全项目同名：不要 `user` / `member` / `account` 混用。
- 禁止 `data1`、`temp`、`obj`、`list2`、`test` 这类名称。

### 4.2 文件与目录

| 对象 | 规则 | 示例 |
|---|---|---|
| 目录 | kebab-case | `interview-management/` |
| Vue 组件文件 | PascalCase，至少两个单词 | `RoomChat.vue`、`InterviewCard.vue` |
| 页面 | 目录 kebab-case + `index.vue` | `views/interview/list/index.vue` |
| 组合式函数 | `useXxx.ts` | `useRoomSocket.ts` |
| Pinia store | `xxx.ts`，导出 `useXxxStore` | `stores/modules/user.ts` → `useUserStore` |
| API 模块 | 业务域名 | `api/modules/interview.ts` |
| 类型文件 | `xxx.ts` | `types/interview.ts` |
| 常量文件 | `xxx.ts` | `constants/interview.ts` |
| 工具函数 | `xxx.ts`，动词或名词 | `formatDate.ts`、`storage.ts` |
| 测试 | `*.spec.ts` / `*.e2e.ts` | `interview.spec.ts` |

### 4.3 Vue 组件与模板

| 对象 | 规则 | 示例 |
|---|---|---|
| 组件名 | PascalCase，多单词 | `InterviewList` |
| props（脚本） | camelCase | `roomNo`、`readonly` |
| props（模板） | kebab-case | `:room-no="roomNo"` |
| emits | camelCase，事件名用动词 | `submit`、`update:modelValue` |
| 模板监听 | kebab-case | `@update:model-value` |
| 事件处理函数 | `handleXxx` | `handleSubmit` |
| 布尔变量 | `is/has/can/should` 前缀 | `isLoading`、`hasPermission` |
| 插槽 | kebab-case | `#empty-state` |

### 4.4 API、类型与常量

| 对象 | 规则 | 示例 |
|---|---|---|
| API 函数 | `get/list/create/update/delete` + 资源 | `listInterviews`、`getRoom` |
| 查询参数类型 | `XxxQuery` | `InterviewQuery` |
| 表单模型 | `XxxForm` | `InterviewForm` |
| 后端 DTO | `Xxx`；需要区分时用 `XxxDTO` | `Interview`、`RoomDTO` |
| 联合类型 | `XxxStatus` | `RoomStatus` |
| 常量对象 | UPPER_SNAKE_CASE | `ROOM_STATUS`、`MAX_PAGE_SIZE` |
| 枚举值 | UPPER_SNAKE_CASE 字符串 | `'WAITING'` |
| 正则常量 | `XXX_REGEXP` | `EMAIL_REGEXP` |

- 不用 `I` 前缀（`IUser`）、不用 `Type`/`Interface` 后缀。
- 不用 TS `enum`，用 `as const` 对象 + 联合类型。
- 状态值必须与后端契约完全一致，不自己造词。

### 4.5 路由与 URL

| 对象 | 规则 | 示例 |
|---|---|---|
| 路由 name | PascalCase，唯一 | `InterviewList` |
| 路由 path | kebab-case | `/interview/list` |
| 动态参数 | camelCase | `/interview/:interviewId` |
| 查询参数 | camelCase | `?pageNum=1&pageSize=20` |
| 页面文件 | 与路由语义一致 | `views/interview/list/index.vue` |

### 4.6 样式

- class 用 kebab-case。
- 推荐 BEM：`interview-card`、`interview-card__title`、`interview-card--active`。
- 不使用无意义 class：`.box`、`.wrap2`、`.red`。
- 颜色、间距、圆角、字号优先使用设计 token（CSS 变量/SCSS 变量）。

---

## 5. 契约与 API 层

### 5.1 唯一事实来源

写接口前按顺序找：

1. `contract/openapi/*.yaml`
2. 后端 Controller / DTO
3. 开发文档的端点表与字段说明
4. 项目冲突/裁决文档

找不到就停，写：

```ts
// TODO(contract): 等待后端确认 GET /api/xxx 的 response 字段
```

并提问。**禁止先猜一个字段继续写。**

### 5.2 禁止的猜测

- ❌ 字段名：`userName` / `username` / `nickname` 混着试
- ❌ 枚举值：自己编 `PENDING/PROCESSING/DONE`
- ❌ 分页结构：`rows`、`records`、`items`、`content` 到处兼容
- ❌ 成功码：同时判断 `'0'` 和 `'OK'`
- ❌ ID 类型：一会儿 `number` 一会儿 `string`
- ❌ 时间格式：有时时间戳、有时 ISO 字符串
- ❌ 用中文标签反推字段
- ❌ 为了“容错”写 `data.list ?? data.rows ?? data.records`

### 5.3 通用响应契约

以项目后端 `Result` 为准。通用形态：

```ts
interface ApiResult<T> {
  code: string        // 成功码以契约/后端为准；本项目为 "0"
  message: string
  data: T
  traceId?: string
  timestamp?: number
}

interface PageResult<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}
```

- 成功码只判断一次，集中在 `request.ts`。
- 分页字段照抄契约；`pageSize ≤ 100`（若项目有更严格限制，从项目）。
- ID 按字符串处理，避免 JS 精度问题。
- 时间按 ISO 8601 字符串处理，展示层再格式化。
- 金额、年限等单位不做前端猜测。

### 5.4 DTO 规则

- DTO 直接照抄 JSON：字段名、大小写、可空性一致。
- 可空字段写 `field: string | null`；后端可能省略的字段才写 `field?: string`。
- 不用 `any`；边界用 `unknown` + 类型守卫。
- 不为每个 DTO 再建一套 UI Model；需要格式化/合并时，在 API 层做一次映射，且只做一次。
- 后端字段变化时，先更新类型/契约，再改业务代码；不要用 `as` 临时糊住。

### 5.5 API 模块写法

```ts
// src/api/modules/interview.ts
import request from '@/api/request'

export interface InterviewQuery {
  pageNum: number
  pageSize: number
  status?: string
}

export interface Interview {
  id: string
  status: string
  scheduledAt: string
}

export function listInterviews(query: InterviewQuery): Promise<PageResult<Interview>> {
  return request.get('/api/interview/interviews', { params: query })
}

export function getInterview(interviewId: string): Promise<Interview> {
  return request.get(`/api/interview/interviews/${interviewId}`)
}
```

要求：

- 一个端点一个函数；函数名能直接看出动作和资源。
- 返回**已解包的 `data`**；组件不处理 `code/message`。
- 不在 API 层做业务判断、弹窗、跳转。
- 查询参数不传 `undefined`/空字符串；调用前处理。
- 非幂等 POST 不自动重试；需要时带 `Idempotency-Key`。
- 更新操作按契约带 `version`。

### 5.6 request.ts 职责

`request.ts` 统一负责：

- `baseURL`、超时、请求头
- Access Token 注入
- 响应 envelope 解包与成功码判断
- 401 刷新队列（并发只刷新一次，失败跳登录）
- 403 跳转/提示
- 错误归一化为 `ApiError`（含 `code`、`message`、`traceId`）
- 取消请求（`AbortController`）
- 不在这里写业务逻辑

业务组件只感知“成功数据”或“抛出的错误”。

### 5.7 错误处理

- 统一错误类型：

```ts
class ApiError extends Error {
  code: string
  traceId?: string
}
```

- 组件只处理需要特别交互的错误；其余统一提示。
- 禁止空 `catch`；至少记录并提示一次。
- 不重复弹窗：拦截器提示过的错误，组件不要再次提示。
- 保留 `traceId`，便于联调排查。

### 5.8 上传、下载与 Mock

- 上传用 `FormData`，单独设置更长 timeout；类型/大小前端只做提示，以后端为准。
- 文件下载走预签名 URL 或 Blob，不在前端保存永久链接。
- Mock 只能放在 `mock/` 或测试目录，并通过环境变量开启；**生产构建不得包含 Mock 数据**。
- 不在生产代码里写 `if (import.meta.env.DEV) return fakeData` 长期分支。

---

## 6. Vue 组件规范

### 6.1 SFC 结构

```vue
<script setup lang="ts">
// 1. imports
// 2. props / emits
// 3. state / computed
// 4. lifecycle
// 5. methods
</script>

<template>
  <!-- ... -->
</template>

<style scoped lang="scss">
/* ... */
</style>
```

- 一律 `<script setup lang="ts">`，不写 Options API。
- 组件名至少两个单词；页面组件可用 `index.vue`，但路由 name 必须清晰。

### 6.2 Props / Emits / Slots

```ts
interface Props {
  roomNo: string
  readonly?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  readonly: false,
})

const emit = defineEmits<{
  (e: 'leave', reason?: string): void
  (e: 'update:modelValue', value: string): void
}>()
```

- props 只读，不直接修改；派生值用 `computed`。
- emits 用类型声明，不用运行时数组。
- 插槽命名 kebab-case；默认插槽优先。
- 不用 `defineExpose`，除非父组件确实需要通过 ref 调用。

### 6.3 模板

- `v-for` 必须用稳定唯一 key；可变列表禁止用 index。
- 不在模板里调用有副作用的函数。
- 派生值用 `computed`；事件处理才用 method。
- 不在同一元素上同时使用 `v-if` 和 `v-for`。
- 权限/敏感内容用 `v-if`，不要用 `v-show` 留在 DOM。
- 频繁切换用 `v-show`。
- 禁止 `v-html`；确需富文本时先做 XSS 清洗并说明原因。
- 一组兄弟节点用 `<template v-if>` 包裹。

### 6.4 响应式与副作用

- 基本类型用 `ref`，对象用 `reactive` 或 `ref`；团队内保持一致。
- 大对象/第三方实例用 `shallowRef`，必要时 `markRaw`。
- 派生状态用 `computed`，不要用 `watch` 手工同步。
- `watch` 只用于副作用；避免深度监听大对象。
- `watchEffect` 少用；依赖不清晰时改用 `watch`。
- `onUnmounted` 必须清理：定时器、事件监听、WebSocket、WebRTC、AudioContext、Worklet、AbortController。

### 6.5 组件拆分

- 单文件超过 ~300 行或承担多个职责时拆分。
- 拆分依据是职责，不是行数；不要一个弹窗拆 6 个文件。
- 页面局部组件放页面目录；跨页面复用才上移。
- 不为“可复用”提前抽组件。

---

## 7. 状态管理（Pinia）

- 新代码统一使用 **setup store**；存量 options store 可保留，不强制一次性重写。
- store 只放**跨页面共享**状态：
  - 当前用户、角色、权限、菜单
  - 全局字典（确有必要）
  - 跨页面保持的筛选条件（确有必要）
- 页面列表、表单、弹窗开关留在组件内。
- 服务端数据只存一份；不要 store + 组件 state 双写。
- getters 只做派生，不产生副作用。
- actions 调用 `src/api/`，不直接写 axios。
- 登出时 `$reset()`，清理 token、菜单、动态路由、定时器。
- 持久化只白名单少量非敏感字段；不把权限、个人信息、token 全量塞进 localStorage。

```ts
export const useUserStore = defineStore('user', () => {
  const userId = ref('')
  const roles = ref<string[]>([])
  const permissions = ref<string[]>([])

  const hasRole = (role: string) => roles.value.includes(role)
  const hasAnyRole = (list: string[]) => list.some((role) => roles.value.includes(role))
  const hasPermission = (code: string) => permissions.value.includes(code)

  function reset() {
    userId.value = ''
    roles.value = []
    permissions.value = []
  }

  return { userId, roles, permissions, hasRole, hasAnyRole, hasPermission, reset }
})
```

> 存量若使用 options store，新增 action/getter 保持同样命名；不要在同一模块混用两种写法。

---

## 8. 路由与权限

### 8.1 路由

- 路由组件一律懒加载。
- 静态路由只放：登录、布局、403/404、必要的独立页。
- 业务页面由后端菜单驱动，`router.addRoute` 动态注册；不要手写几十条业务路由。
- 动态路由只注册一次；登出时移除或刷新。
- 路由 `meta` 用类型扩展，禁止魔法字符串散落：

```ts
declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    roles?: string[]
    permissions?: string[]
    title?: string
  }
}
```

- 路由 name 唯一；path 使用 kebab-case。
- 可分享的筛选/分页状态放 query；纯 UI 状态不要塞 URL。

### 8.2 守卫顺序

1. 公开路由直接放行
2. 无 token → `/login?redirect=...`
3. 有 token 但用户信息未加载 → 先 `GET /api/auth/me`
4. 校验 `meta.roles` / `meta.permissions`
5. 放行

- 刷新页面后 Pinia 会清空，必须能通过 `/me` 恢复；否则权限判断全部失效。
- 防止无限重定向：登录页、403、404 必须标记 `public` 或跳过权限检查。
- 403 与 404 分开；不要把所有失败都跳首页。

### 8.3 权限模型

- 身份来源：登录响应或 `GET /api/auth/me` 返回的 `roles` / `permissions`。
- **不要解析 token 判断身份**：token 可能是不透明字符串、可能在 HttpOnly Cookie 中、可能是过期快照。
- 页面级用角色，按钮级用权限码；不要两者混用到失控。
- 模板优先：

```vue
<el-button v-if="userStore.hasRole('HR_ADMIN')">批量排期</el-button>
<el-button v-if="userStore.hasPermission('interview:application:transfer')">转岗</el-button>
```

- 路由级：

```ts
meta: { roles: ['SUPER_ADMIN'] }
```

- 不推荐自定义 `v-permission` 指令：它在 `mounted` 后删除 DOM，权限异步加载时不会恢复，且绕过 Vue 渲染器。需要复用复杂区块时，用内部 `v-if` 的包装组件。
- 前端隐藏只是 UX；**后端必须校验每个接口**。
- 角色名、权限码重复出现时抽到 `src/constants/`，不要到处写魔法字符串。
- 超级管理员是否绕过权限由后端决定；前端不要自行 `if (role === 'SUPER_ADMIN') return true`，除非契约明确。

---

## 9. 样式与 UI

- 使用 Element Plus 组件，不重复造按钮/表格/弹窗。
- 样式一律 `<style scoped lang="scss">`；全局样式只放 reset、变量、mixin。
- 颜色、间距、圆角、字号、层级使用设计 token；禁止到处写魔法值。
- `:deep()` 只在确需穿透时使用，并缩小范围。
- 不随意覆盖 Element Plus 全局样式；需要主题定制时集中到 `styles/`。
- 每个异步页面必须有：loading、空状态、错误状态。
- 表单必须有 label、校验规则、提交中禁用；删除操作二次确认。
- 表格分页、排序、筛选优先服务端处理；不要一次拉全量。
- 弹窗关闭时重置表单；避免脏数据残留。
- 中文文案统一；日期时间在展示层格式化。
- 响应式：至少覆盖 1280 / 768 两档；候选人页面优先移动端可用。
- 可访问性：按钮有可读文本、表单有 label、图片有 alt、键盘可操作、颜色对比度达标。

---

## 10. TypeScript

- `strict: true`；禁止 `any`、`@ts-ignore`、`@ts-expect-error`（除非有明确原因和注释）。
- 禁止 `as unknown as` 双重强转。
- 边界用 `unknown` + 类型守卫：

```ts
function isInterview(value: unknown): value is Interview {
  return typeof value === 'object' && value !== null && 'id' in value
}
```

- 用 `interface` 描述对象契约，用 `type` 描述联合/别名。
- 不用 TS `enum`；用 `as const` 对象 + 联合类型。
- 可空性按契约写；不用 `!` 掩盖可能为空。
- 泛型保持最小，不写类型体操。
- 导出的函数显式标注返回类型。
- 类型只在跨 3+ 文件复用或语义显著提升时抽取。
- 用 `import type` 导入类型。
- 用 `satisfies` 校验字面量，而不是 `as`。

---

## 11. 代码风格与可读性

- 以仓库 ESLint/Prettier 配置为准；没有配置时，新增配置需团队确认。
- 不批量格式化无关文件；不因个人偏好制造大 diff。
- import 顺序：Vue → 第三方 → 项目内；类型导入用 `import type`。
- 路径别名统一 `@/`：**必须同时在 `vite.config.ts` 的 `resolve.alias` 和 `tsconfig.json` 的 `paths` 中配置**；若工程未配置，先补配置，或暂用相对路径并说明，禁止两种混用。
- 函数用动词；布尔用 `is/has/can/should`。
- 早返回，少嵌套；避免三层以上三元表达式。
- 单函数尽量 ≤ 50 行；单文件尽量 ≤ 300 行（超出时按职责拆分）。
- 注释只解释“为什么”，不复述代码；中文注释可以。
- TODO 格式：`// TODO(W2, A): 接入 /api/auth/me`。
- 不提交 `console.log`、调试代码、临时 mock、注释掉的代码。
- 错误处理：不空 `catch`；不吞异常；必要时保留 `cause`。

### 11.1 Git 与协作

- 一个提交只做一件事；提交信息用 Conventional Commits：`feat:`、`fix:`、`refactor:`、`docs:`、`test:`、`chore:`。
- 不提交生成物、密钥、`.env`、真实个人数据。
- 不 force push 公共分支；不 amend 他人提交。
- PR 描述写：改了什么、为什么、如何验证、未确认项。
- Code Review 重点：契约是否正确、是否有冗余抽象、错误/空/加载状态、资源清理、权限、性能。

---

## 12. 性能

- 路由懒加载；ECharts、编辑器、音视频等重模块按需加载。
- 长列表用分页或虚拟滚动；不要一次渲染上千行。
- 搜索输入防抖 300ms；切换筛选取消旧请求。
- 大对象用 `shallowRef`；不需要响应式的常量用普通对象。
- 避免深度 `watch`；避免在模板里做重计算。
- 定时器、轮询、WS 必须有清理；离开页面不能继续跑。
- 不整包引入 lodash / ECharts；按需 import。
- 图片压缩、懒加载、合理尺寸；首屏关键资源优先。
- 不做过早优化；先测量（bundle analyzer、Performance 面板）再改。

---

## 13. 安全与数据红线

- 仓库不提交密钥、token、真实个人信息；演示数据全部虚构。
- 不在 URL、日志、埋点里放 token / 手机号 / 邮箱 / 音频 / 简历内容。
- 文件走私有桶 + 短时预签名 URL；不把永久链接存入 state/localStorage。
- 禁止 `v-html`；必须用时先清洗（DOMPurify 等）。
- 前端权限判断不是安全边界；后端每个接口必须校验。
- Token 优先 HttpOnly + Secure Cookie；若暂存 localStorage，必须评估 XSS 风险，且不扩散敏感数据。
- 依赖升级后跑安全审计；不引入来源不明的包。
- 生产构建关闭 sourcemap 或限制访问（按团队策略）。

---

## 14. 实时通信与媒体（可选章节）

适用于 WebSocket、WebRTC、LiveKit、实时字幕等场景；不涉及可跳过。

- WebSocket 封装集中在 `src/rtc/` 或 `src/composables/`，不要散落在页面。
- 必须定义：鉴权方式、心跳间隔、超时、重连退避、消息序号/去重、关闭码处理。
- 连接/断开成对；`onUnmounted` 必须关闭连接、移除监听。
- 消息协议用判别联合类型，不用 `any`：

```ts
type RoomMessage =
  | { type: 'CHAT'; seq: number; payload: ChatPayload }
  | { type: 'SUBTITLE'; seq: number; payload: SubtitlePayload }
  | { type: 'SYSTEM'; payload: SystemPayload }
```

- WebRTC/LiveKit：
  - token 由后端签发；前端不生成、不硬编码密钥。
  - `Room` 实例用 `shallowRef`/`markRaw`。
  - 离开页面停止本地轨道、关闭 `AudioContext`、销毁 Worklet。
  - 不打印 token、SDP、音频数据、个人信息。
- 音频采集：AudioWorklet，16kHz / 16bit / 单声道 PCM，100~200ms 帧；与播放/混音轨道分开管理。
- 过载/断线：有降级策略，不无限重连；向用户展示明确状态。
- 录制/隐私：开始前取得同意；没有 consent 不启动录制。

---

## 15. 测试与质量门禁

### 15.1 测试

- 工具：Vitest + Vue Test Utils + Playwright（项目配置后遵守）。
- 必测：纯函数、composables、stores、关键业务组件、核心用户流程。
- 不为覆盖率写无意义断言；优先测行为与边界。
- 新增测试与代码同 PR；修 bug 时先补复现测试（条件允许）。
- 项目未配置测试框架时，不擅自引入；先与负责人确认。

### 15.2 Definition of Done

提交前必须：

1. `npm run build` 通过（类型检查无错误）。
2. `npm run lint` 通过（若已配置）。
3. 相关测试通过（若已配置）。
4. 没有未使用的 import / 变量 / 文件。
5. 没有 `console.log`、`any`、`@ts-ignore`。
6. 异步页面有 loading / empty / error。
7. 权限相关页面/按钮在无权限时不渲染；后端接口有权限码。
8. 所有接口字段都能在契约/DTO 中找到出处；没有猜测。
9. 定时器、监听器、WS、媒体资源已清理。
10. 手动验证：登录、刷新、401、无权限、核心流程。
11. 改动范围最小；没有顺手重构无关代码。

---

## 16. AI 编码工作流

1. **先读**：相关页面、API 模块、store、路由、OpenAPI/后端 DTO、项目文档。
2. **先确认契约**：端点、字段、枚举、分页、错误码、权限码。找不到就停。
3. **给最小方案**：能改一个文件不改三个；能加一个函数不建一层抽象。
4. **不做无关重构**：不重命名、不批量格式化、不升级依赖、不改构建配置。
5. **写完自检**：跑类型检查/构建/lint；检查 loading/error/cleanup/权限。
6. **汇报**：改了哪些文件、依据哪个契约、还有什么 `TODO(contract)`。

### 契约缺失时的正确写法

```ts
// TODO(contract): 后端尚未提供 /api/room/rooms/{roomNo}/notes 的 response 字段
// 已确认：路径、方法、权限码；未确认：id 类型、createdAt 格式
```

然后向用户提问。**不要**自己编一个 `Note` 类型继续写。

### 遇到需求模糊时

- 先给一个推荐方案 + 理由，再问是否确认。
- 不要一次性抛出多个开放式问题。
- 不要为了“显得完整”实现未被要求的功能。

---

## 17. 明确禁止清单

- ❌ 猜后端字段、枚举、分页结构、响应码、ID/时间类型
- ❌ 在组件里直接 axios / fetch / 手写 URL
- ❌ `any`、`@ts-ignore`、`as unknown as`
- ❌ 空 `catch`、吞错误
- ❌ 自造 `BaseXxx` / `useCrud` / 万能组件
- ❌ 每个页面一个 store、同一数据存多份
- ❌ 自造 `v-permission` 指令、解析 token 判断角色
- ❌ 用 index 作为可变列表的 key
- ❌ `v-html`（未清洗）
- ❌ `console.log`、调试代码、生产路径里的 mock
- ❌ 未清理的定时器 / 监听器 / WS / 媒体轨道
- ❌ 新增依赖、升级依赖、改构建配置（未经确认）
- ❌ 手改自动生成的 `*.d.ts`
- ❌ 硬编码 API base、端口、媒体服务器地址
- ❌ 用 `setTimeout` 等后端/等渲染
- ❌ 顺手重构无关代码
- ❌ 为了“兼容”同时支持多套字段/状态/响应结构

---

## 附录 A：本仓库文档索引

写前端代码前，优先查阅：

| 文档 | 用途 |
|---|---|
| `docs/2.架构设计方案-招聘面试系统微服务架构.md` | 服务边界、事件、部署 |
| `docs/3.开发文档-招聘面试系统.md` | API、数据库、权限码、页面 |
| `docs/4.成员B开发文档-智能简历与人才管理子系统.md` | resume/file/ai 契约 |
| `docs/5.成员C开发文档-在线面试子系统.md` | room 契约、WS、LiveKit、ASR |
| `docs/6.口径冲突清单.md` | 已知冲突与建议口径；未决项不要自行选边 |
| `contract/openapi/*.yaml` | 最终 API 契约（若已生成） |
| 后端 Controller / DTO | 字段的最终事实来源 |

本项目已确认的通用契约：

- 成功码：`"0"`（以 `Result.java` 为准；文档中的 `"OK"` 为过期口径）。
- 分页：`{ list, total, pageNum, pageSize, pages }`。
- ID：字符串；时间：ISO 8601。
- 权限：角色用于页面级，权限码用于按钮/接口级；前端不作为安全边界。
- 房间路由参数：`roomNo`（不是数据库主键 `roomId`）。

## 附录 B：落地提示（非规范，随项目变化）

- 当前仓库是骨架，目录可按第 3 节逐步演进；不要以“现有目录不是这样”为由拒绝规范。
- `@/` alias 需要先在 Vite 与 tsconfig 同时配置；未配置时先用相对路径，不要混用。
- `components.d.ts` / `auto-imports.d.ts` 由插件生成，不要手改。
- 登录契约未最终确定前，不提前实现完整权限路由；先约定 `roles` / `permissions` / `menus` 的返回结构。
- `request.ts` 的响应解包与类型需要统一后再铺量写 API 模块；不要在业务模块里各自 `as` 强转。
- 已知契约冲突以 `docs/6` 为准；未决项标记 `TODO(contract)`，不要猜。
