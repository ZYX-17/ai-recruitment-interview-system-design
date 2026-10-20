-- ============================================================================
-- 招聘面试系统 · recruit_file 库 DDL（v3.1）
-- 服务：file-service ｜ Owner：B（智能简历与人才管理子系统）
-- 表数：1 张业务表 + 2 张公共表 = 3 张
-- 说明：对象实体在 MinIO；本库存元数据与校验结果；
--       下载走预签名 URL（10min），未授权访问直接被拒
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 1. 文件元数据（上传/下载/预签名/病毒与 MIME 校验登记）
-- ----------------------------------------------------------------------------
CREATE TABLE file_meta (
  id            BIGINT UNSIGNED NOT NULL,
  file_key      VARCHAR(64)  NOT NULL,               -- 业务文件键（预签名 URL 携带）
  bucket        VARCHAR(64)  NOT NULL,
  object_key    VARCHAR(255) NOT NULL,                -- MinIO 对象路径
  sha256        CHAR(64)     NOT NULL,                -- 内容指纹（秒传/查重）
  mime          VARCHAR(128) NOT NULL,
  size          BIGINT        NOT NULL,               -- 字节；简历 ≤20MB、录像 ≤500MB（按 biz_type）
  biz_type      VARCHAR(32)  NOT NULL,                 -- RESUME/RECORDING/ANSWER_VIDEO/EVIDENCE/AVATAR...
  biz_id        VARCHAR(64)  NULL,
  file_name     VARCHAR(255) NOT NULL,
  uploader_id   BIGINT UNSIGNED NOT NULL,
  status        VARCHAR(16)  NOT NULL DEFAULT 'UPLOADING', -- UPLOADING/OK/REJECTED/DELETED
  reject_reason VARCHAR(255) NULL,                    -- MIME 不符/超大/内容校验失败
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version       INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_file_key (file_key),
  UNIQUE KEY uk_object_key (object_key),
  KEY idx_biz (biz_type, biz_id),
  KEY idx_sha (sha256),
  KEY idx_uploader (uploader_id)
) COMMENT='文件元数据表（预签名 URL 10 分钟有效）';

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
