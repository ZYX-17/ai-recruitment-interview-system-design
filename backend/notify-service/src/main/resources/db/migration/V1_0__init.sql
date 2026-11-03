-- Flyway V1_0：recruit_notify 初始化（与 sql/05_recruit_notify.sql 同源）
-- 规则：脚本内不使用 USE/CREATE DATABASE，由连接串指定 schema。

CREATE TABLE notification (
  id             BIGINT UNSIGNED NOT NULL,
  msg_no         VARCHAR(64)  NOT NULL,
  receiver_id    BIGINT UNSIGNED NOT NULL,
  receiver_email VARCHAR(128) NULL,
  channel        VARCHAR(16)  NOT NULL,
  biz_type       VARCHAR(32)  NOT NULL,
  biz_id         VARCHAR(64)  NOT NULL,
  title          VARCHAR(255) NOT NULL,
  content        TEXT         NOT NULL,
  status         VARCHAR(16)  NOT NULL DEFAULT 'QUEUED',
  error_msg      VARCHAR(512) NULL,
  sent_at        DATETIME(3)  NULL,
  read_at        DATETIME(3)  NULL,
  retry_count    INT          NOT NULL DEFAULT 0,
  next_retry_at  DATETIME(3)  NULL,
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version        INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_msg_no (msg_no),
  KEY idx_receiver_status (receiver_id, status),
  KEY idx_biz (biz_type, biz_id),
  KEY idx_created (created_at),
  KEY idx_retry (status, next_retry_at)
) COMMENT='通知表（验收口径：通知 60s 内达）';

CREATE TABLE notification_template (
  id             BIGINT UNSIGNED NOT NULL,
  template_code  VARCHAR(64)  NOT NULL,
  channel        VARCHAR(16)  NOT NULL,
  version        INT          NOT NULL DEFAULT 1,
  title_tpl      VARCHAR(255) NOT NULL,
  content_tpl    TEXT         NOT NULL,
  variables_json JSON         NULL,
  status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_code_channel_ver (template_code, channel, version)
) COMMENT='通知模板表（新版本插入新行，旧版本置 ARCHIVED 保留）';

CREATE TABLE notification_send_log (
  id              BIGINT UNSIGNED NOT NULL,
  notification_id BIGINT UNSIGNED NOT NULL,
  provider        VARCHAR(32)  NOT NULL,
  request_id      VARCHAR(64)  NULL,
  result          VARCHAR(16)  NOT NULL,
  error_msg       VARCHAR(512) NULL,
  cost_ms         INT          NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_notification (notification_id),
  KEY idx_result_created (result, created_at)
) COMMENT='通知发送日志表';

CREATE TABLE local_message (
  id            BIGINT UNSIGNED NOT NULL,
  message_id    VARCHAR(64)  NOT NULL,
  event_type    VARCHAR(64)  NOT NULL,
  event_version INT          NOT NULL DEFAULT 1,
  aggregate_id  VARCHAR(64)  NOT NULL,
  trace_id      VARCHAR(64)  NULL,
  exchange      VARCHAR(64)  NOT NULL,
  routing_key   VARCHAR(128) NOT NULL,
  payload       JSON         NOT NULL,
  status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
  retry_count   INT          NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3)  NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version       INT          NOT NULL DEFAULT 0,
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
