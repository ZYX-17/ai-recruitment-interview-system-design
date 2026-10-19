-- ============================================================================
-- 招聘面试系统 · recruit_notify 库 DDL（v3.1）
-- 服务：notify-service ｜ Owner：A（面试管理子系统闭环出口：邀约/提醒/Offer 通知）
-- 表数：3 张业务表 + 2 张公共表 = 5 张
-- 说明：渠道=邮件（SMTP）+站内信；短信 Mock 不落正式渠道
-- ============================================================================

USE recruit_notify;

-- ----------------------------------------------------------------------------
-- 1. 通知（notification.requested 事件消费后建件）
-- ----------------------------------------------------------------------------
CREATE TABLE notification (
  id             BIGINT UNSIGNED NOT NULL,
  msg_no         VARCHAR(64)  NOT NULL,
  receiver_id    BIGINT UNSIGNED NOT NULL,
  receiver_email VARCHAR(128) NULL,                 -- 发件快照
  channel        VARCHAR(16)  NOT NULL,             -- EMAIL / INAPP
  biz_type       VARCHAR(32)  NOT NULL,             -- INTERVIEW_INVITE/OFFER_SEND/STAGE_CHANGE/REMINDER...
  biz_id        VARCHAR(64)   NOT NULL,             -- 业务单据 ID
  title          VARCHAR(255) NOT NULL,
  content        TEXT         NOT NULL,
  status         VARCHAR(16)  NOT NULL DEFAULT 'QUEUED', -- QUEUED/SENT/READ/FAILED
  error_msg      VARCHAR(512) NULL,
  sent_at        DATETIME(3)  NULL,
  read_at        DATETIME(3)  NULL,                 -- 站内信已读时间
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version        INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_msg_no (msg_no),
  KEY idx_receiver_status (receiver_id, status),
  KEY idx_biz (biz_type, biz_id),
  KEY idx_created (created_at)
) COMMENT='通知表（验收口径：通知 60s 内达）';

-- ----------------------------------------------------------------------------
-- 2. 通知模板（按 code+channel+version 唯一；变量占位 {{name}} 等）
-- ----------------------------------------------------------------------------
CREATE TABLE notification_template (
  id             BIGINT UNSIGNED NOT NULL,
  template_code  VARCHAR(64)  NOT NULL,              -- interview_invite / offer_send ...
  channel        VARCHAR(16)  NOT NULL,               -- EMAIL / INAPP
  version        INT           NOT NULL DEFAULT 1,
  title_tpl      VARCHAR(255) NOT NULL,
  content_tpl    TEXT         NOT NULL,
  variables_json JSON         NULL,                 -- 变量清单与示例
  status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_code_channel_ver (template_code, channel, version)
) COMMENT='通知模板表（模板更新走新增 version 行，旧版本保留）';

-- ----------------------------------------------------------------------------
-- 3. 发送日志（失败重试依据；不记敏感内容）
-- ----------------------------------------------------------------------------
CREATE TABLE notification_send_log (
  id              BIGINT UNSIGNED NOT NULL,
  notification_id BIGINT UNSIGNED NOT NULL,
  provider        VARCHAR(32)  NOT NULL,             -- SMTP / INAPP / SMS_MOCK
  request_id      VARCHAR(64)  NULL,
  result          VARCHAR(16)  NOT NULL,             -- OK / FAIL
  error_msg       VARCHAR(512) NULL,
  cost_ms         INT           NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_notification (notification_id),
  KEY idx_result_created (result, created_at)
) COMMENT='通知发送日志表';

-- ============================================================================
-- 公共表（与其他库同构）
-- ============================================================================
CREATE TABLE local_message (
  id            BIGINT UNSIGNED NOT NULL,
  message_id    VARCHAR(64)  NOT NULL,
  event_type    VARCHAR(64)  NOT NULL,
  event_version INT           NOT NULL DEFAULT 1,
  aggregate_id  VARCHAR(64)  NOT NULL,
  trace_id      VARCHAR(64)  NULL,
  exchange      VARCHAR(64)  NOT NULL,
  routing_key   VARCHAR(128) NOT NULL,
  payload       JSON         NOT NULL,
  status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
  retry_count   INT           NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3)  NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version       INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_message_id (message_id),
  KEY idx_status_retry (status, next_retry_at)
) COMMENT='本地消息表（Outbox）';

CREATE TABLE idempotent_consumption (
  id             BIGINT UNSIGNED NOT NULL,
  consumer_group VARCHAR(64) NOT NULL,
  event_id       VARCHAR(64) NOT NULL,
  consumed_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_group_event (consumer_group, event_id)
) COMMENT='幂等消费表';
