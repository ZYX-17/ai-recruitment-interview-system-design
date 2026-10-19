-- ============================================================================
-- 招聘面试系统 · recruit_auth 库 DDL（v3.1）
-- 服务：auth-service (IAM) ｜ Owner：A（面试管理子系统，兼平台底座）
-- 表数：9 张 IAM 业务表 + 2 张公共表 = 11 张
-- 通用约定：雪花 ID 应用生成（BIGINT UNSIGNED，无 AUTO_INCREMENT）；
--   枚举一律 VARCHAR；时间 DATETIME(3)（UTC）；关键状态表 version 乐观锁；
--   本文件与 auth-service Flyway V1_0__init.sql 同源（W1 落地）
-- ============================================================================

USE recruit_auth;

-- ----------------------------------------------------------------------------
-- 1. 用户（与文档 2.3 示例一致）
-- ----------------------------------------------------------------------------
CREATE TABLE iam_user (
  id            BIGINT UNSIGNED NOT NULL,
  username      VARCHAR(64)  NOT NULL,
  password_hash VARCHAR(100) NULL,                -- CANDIDATE 首次可由邀约创建后补
  user_type     VARCHAR(16)  NOT NULL,            -- EMPLOYEE / CANDIDATE
  display_name  VARCHAR(64)  NOT NULL,
  status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE / LOCKED / DISABLED
  token_version INT          NOT NULL DEFAULT 0,  -- 登出全部/踢线：Redis auth:tv:{userId}
  phone_enc     VARCHAR(255) NULL,                -- 手机号密文
  phone_hash    CHAR(64)     NULL,                -- 手机号哈希（查重/查找）
  dept_id       BIGINT UNSIGNED NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version       INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username),
  UNIQUE KEY uk_phone_hash (phone_hash),
  KEY idx_dept (dept_id)
) COMMENT='用户表';

-- ----------------------------------------------------------------------------
-- 2. 角色
-- ----------------------------------------------------------------------------
CREATE TABLE iam_role (
  id             BIGINT UNSIGNED NOT NULL,
  role_code      VARCHAR(64) NOT NULL,            -- SUPER_ADMIN/HR_ADMIN/RECRUITER/INTERVIEWER/CANDIDATE
  role_name      VARCHAR(64) NOT NULL,
  is_system_role TINYINT(1)  NOT NULL DEFAULT 0,  -- 系统内置角色不可删
  data_scope     VARCHAR(16) NOT NULL DEFAULT 'SELF', -- ALL/DEPARTMENT/ASSIGNED/SELF（默认口径）
  status         VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  remark         VARCHAR(255) NULL,
  created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version        INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_code (role_code)
) COMMENT='角色表（五角色由 seed 写入）';

-- ----------------------------------------------------------------------------
-- 3. 权限（菜单/按钮/API 三态）
-- ----------------------------------------------------------------------------
CREATE TABLE iam_permission (
  id              BIGINT UNSIGNED NOT NULL,
  permission_code VARCHAR(128) NOT NULL,           -- 如 interview:application:transfer
  type            VARCHAR(16)  NOT NULL,            -- MENU / BUTTON / API
  parent_id       BIGINT UNSIGNED NULL,
  name            VARCHAR(64)  NOT NULL,
  route_path      VARCHAR(255) NULL,               -- MENU：前端路由
  component       VARCHAR(255) NULL,               -- MENU：页面组件路径
  api_pattern     VARCHAR(255) NULL,               -- API：如 /api/interview/applications/**
  sort            INT           NOT NULL DEFAULT 0,
  status          VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
  created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_perm_code (permission_code),
  KEY idx_parent (parent_id),
  KEY idx_type (type)
) COMMENT='权限表（权限码全集由 auth-service 统一 seed）';

-- ----------------------------------------------------------------------------
-- 4. 用户-角色
-- ----------------------------------------------------------------------------
CREATE TABLE iam_user_role (
  id         BIGINT UNSIGNED NOT NULL,
  user_id    BIGINT UNSIGNED NOT NULL,
  role_id    BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_role (user_id, role_id),
  KEY idx_role (role_id)
) COMMENT='用户角色关联表';

-- ----------------------------------------------------------------------------
-- 5. 角色-权限（管理员勾选角色菜单的落点，勾选后即时生效）
-- ----------------------------------------------------------------------------
CREATE TABLE iam_role_permission (
  id            BIGINT UNSIGNED NOT NULL,
  role_id       BIGINT UNSIGNED NOT NULL,
  permission_id BIGINT UNSIGNED NOT NULL,
  created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_perm (role_id, permission_id),
  KEY idx_permission (permission_id)
) COMMENT='角色权限关联表（勾选落点，变更后提升用户 token_version 或刷新 auth:perm 缓存）';

-- ----------------------------------------------------------------------------
-- 6. 数据范围（数据权限细化，覆盖角色默认口径）
-- ----------------------------------------------------------------------------
CREATE TABLE iam_data_scope (
  id         BIGINT UNSIGNED NOT NULL,
  role_id    BIGINT UNSIGNED NULL,                -- 二选一：按角色
  user_id    BIGINT UNSIGNED NULL,                -- 二选一：按用户（优先于角色）
  scope_type VARCHAR(16) NOT NULL,                 -- ALL / DEPARTMENT / ASSIGNED / SELF
  biz_rule   JSON        NULL,                    -- 领域定制规则（如仅本人负责岗位）
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_user (role_id, user_id),
  KEY idx_user (user_id)
) COMMENT='数据范围表（@DataScope MyBatis 拦截器消费）';

-- ----------------------------------------------------------------------------
-- 7. 部门
-- ----------------------------------------------------------------------------
CREATE TABLE iam_department (
  id         BIGINT UNSIGNED NOT NULL,
  parent_id  BIGINT UNSIGNED NULL,
  ancestors  VARCHAR(255) NOT NULL DEFAULT '',     -- 祖级路径，如 0,100,101
  dept_name  VARCHAR(64)  NOT NULL,
  sort       INT           NOT NULL DEFAULT 0,
  status     VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version    INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_parent (parent_id)
) COMMENT='部门表';

-- ----------------------------------------------------------------------------
-- 8. 刷新令牌会话（family 重放检测；主存储在 Redis，本表为会话登记/审计）
-- ----------------------------------------------------------------------------
CREATE TABLE iam_refresh_session (
  id          BIGINT UNSIGNED NOT NULL,
  user_id     BIGINT UNSIGNED NOT NULL,
  family_id   VARCHAR(64)  NOT NULL,              -- 令牌族：重放检测判定整族失效
  token_hash  CHAR(64)     NOT NULL,               -- sha256(Refresh Token)
  device      VARCHAR(255) NULL,
  ip          VARCHAR(64)  NULL,
  expires_at  DATETIME(3)  NOT NULL,               -- 7d
  revoked_at  DATETIME(3)  NULL,
  status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / REVOKED / REPLAYED
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_token_hash (token_hash),
  KEY idx_user (user_id),
  KEY idx_family (family_id),
  KEY idx_expires (expires_at)
) COMMENT='刷新会话表（Redis Key：auth:refresh:{hash} / auth:refresh:family:{familyId}）';

-- ----------------------------------------------------------------------------
-- 9. 操作审计日志（只增不改；不记密码/令牌/简历全文）
-- ----------------------------------------------------------------------------
CREATE TABLE iam_audit_log (
  id            BIGINT UNSIGNED NOT NULL,
  operator_id   BIGINT UNSIGNED NULL,              -- NULL=匿名（登录失败等）
  action        VARCHAR(64)  NOT NULL,             -- LOGIN / GRANT / UPDATE_MENU / ...
  resource_type VARCHAR(64)  NOT NULL,
  resource_id   VARCHAR(64)  NULL,
  result        VARCHAR(16)  NOT NULL,             -- OK / FAIL
  ip            VARCHAR(64)  NULL,
  trace_id      VARCHAR(64)  NULL,
  detail        JSON         NULL,                -- 变更摘要（脱敏）
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_operator (operator_id),
  KEY idx_resource (resource_type, resource_id),
  KEY idx_trace (trace_id),
  KEY idx_created (created_at)
) COMMENT='操作审计日志表';

-- ============================================================================
-- 公共表（每个 schema 必备，与各库保持同构）
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 10. 本地消息表（Outbox 发件箱：与业务同事务写入，publisher confirms 后标记）
-- ----------------------------------------------------------------------------
CREATE TABLE local_message (
  id            BIGINT UNSIGNED NOT NULL,
  message_id    VARCHAR(64)  NOT NULL,             -- 事件 eventId（全局唯一）
  event_type    VARCHAR(64)  NOT NULL,             -- 如 job.published / interview.scheduled
  event_version INT           NOT NULL DEFAULT 1,
  aggregate_id  VARCHAR(64)  NOT NULL,
  trace_id      VARCHAR(64)  NULL,
  exchange      VARCHAR(64)  NOT NULL,
  routing_key   VARCHAR(128) NOT NULL,
  payload       JSON         NOT NULL,            -- 事件信封（5.1 规范）
  status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING', -- PENDING/SENT/CONFIRMED/FAILED/DEAD
  retry_count   INT           NOT NULL DEFAULT 0,  -- ≤3 次后转死信
  next_retry_at DATETIME(3)  NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version       INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_message_id (message_id),
  KEY idx_status_retry (status, next_retry_at)
) COMMENT='本地消息表（Outbox）';

-- ----------------------------------------------------------------------------
-- 11. 消费幂等表（消费前先占位，冲突即已消费过）
-- ----------------------------------------------------------------------------
CREATE TABLE idempotent_consumption (
  id             BIGINT UNSIGNED NOT NULL,
  consumer_group VARCHAR(64) NOT NULL,              -- 如 resume-service.job-published
  event_id       VARCHAR(64) NOT NULL,
  consumed_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_group_event (consumer_group, event_id)
) COMMENT='幂等消费表';
