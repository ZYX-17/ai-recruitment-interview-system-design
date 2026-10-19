-- ============================================================================
-- 招聘面试系统 · recruit_room 库 DDL（v3.1）
-- 服务：room-service ｜ Owner：C（在线面试子系统，后端与房间前端一体）
-- 表数：8 张业务表 + 2 张公共表 = 10 张
-- 说明：媒体流不落库（LiveKit SFU/MinIO）；本库存房间业务状态、令牌授权、
--       Webhook 事件、聊天/笔记/转写/防作弊记录
-- ============================================================================

USE recruit_room;

-- ----------------------------------------------------------------------------
-- 1. 房间（与文档 2.3 示例一致，增加录制状态快照列）
-- ----------------------------------------------------------------------------
CREATE TABLE room (
  id                BIGINT UNSIGNED NOT NULL,
  room_no           VARCHAR(32)  NOT NULL,
  interview_id      BIGINT UNSIGNED NOT NULL,        -- interview 库面试安排 ID（事件驱动建房）
  livekit_room_name VARCHAR(128) NOT NULL,           -- LiveKit 服务端房间名
  room_type         VARCHAR(24)  NOT NULL,           -- ONE_TO_ONE / ONE_TO_MANY
  status            VARCHAR(16)  NOT NULL,           -- CREATED/WAITING/LIVE/FINISHED（显式状态机）
  max_participants  TINYINT      NOT NULL DEFAULT 6,  -- ONE_TO_ONE=2 / ONE_TO_MANY=6
  current_count     TINYINT      NOT NULL DEFAULT 0,  -- 行级原子扣减，participant_left 归还（GREATEST 防负）
  recording_status  VARCHAR(16)  NOT NULL DEFAULT 'NONE', -- NONE/RECORDING/COMPLETED/FAILED（列表快照）
  created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version           INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_room_no (room_no),
  UNIQUE KEY uk_livekit_room (livekit_room_name),
  KEY idx_interview (interview_id),
  KEY idx_status (status)
) COMMENT='房间表（interview.scheduled 事件驱动创建）';

-- ----------------------------------------------------------------------------
-- 2. 参会人（进房预检 + 名额校验 + LiveKit identity 绑定）
-- ----------------------------------------------------------------------------
CREATE TABLE room_participant (
  id           BIGINT UNSIGNED NOT NULL,
  room_id      BIGINT UNSIGNED NOT NULL,
  ref_id       BIGINT UNSIGNED NOT NULL,             -- iam_user.id
  ref_name     VARCHAR(64)   NOT NULL,               -- 姓名快照
  role         VARCHAR(16)   NOT NULL,               -- HOST/INTERVIEWER/CANDIDATE/OBSERVER
  status       VARCHAR(16)   NOT NULL DEFAULT 'WAITING', -- WAITING/ADMITTED/JOINED/LEFT/KICKED
  livekit_identity VARCHAR(128) NULL,                -- 令牌绑定的 identity
  joined_at    DATETIME(3)   NULL,
  left_at      DATETIME(3)   NULL,
  created_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version      INT            NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_room_ref (room_id, ref_id),
  KEY idx_ref (ref_id)
) COMMENT='房间参会人表（受邀名单，未邀请者被拒）';

-- ----------------------------------------------------------------------------
-- 3. 房间事件（Webhook 转换落库；幂等经 idempotent_consumption + webhook_event_id）
-- ----------------------------------------------------------------------------
CREATE TABLE room_event (
  id               BIGINT UNSIGNED NOT NULL,
  room_id          BIGINT UNSIGNED NOT NULL,
  webhook_event_id VARCHAR(64)  NULL,                -- LiveKit event id（追溯去重）
  event_type       VARCHAR(64)  NOT NULL,            -- participant_joined/participant_left/room_finished...
  payload          JSON          NOT NULL,
  occurred_at      DATETIME(3)   NOT NULL,
  created_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_webhook_event (webhook_event_id),
  KEY idx_room_time (room_id, occurred_at)
) COMMENT='房间事件表（HMAC 验签后写入）';

-- ----------------------------------------------------------------------------
-- 4. 录制元数据（Egress 编排；Egress 失败不阻塞面试结束）
-- ----------------------------------------------------------------------------
CREATE TABLE record_meta (
  id             BIGINT UNSIGNED NOT NULL,
  room_id        BIGINT UNSIGNED NOT NULL,
  interview_id   BIGINT UNSIGNED NOT NULL,           -- 快照引用
  egress_id      VARCHAR(64)  NULL,                  -- LiveKit Egress 任务 ID
  file_id        BIGINT UNSIGNED NULL,               -- file-service 录像文件
  file_key       VARCHAR(255) NULL,                  -- MinIO object key
  duration_sec   INT           NULL,
  status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING', -- PENDING/RECORDING/COMPLETED/FAILED
  error_msg      VARCHAR(512) NULL,
  started_at     DATETIME(3)  NULL,
  finished_at    DATETIME(3)  NULL,
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version        INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_egress (egress_id),
  KEY idx_room (room_id),
  KEY idx_interview (interview_id)
) COMMENT='录制元数据表（回放页按本表鉴权取流）';

-- ----------------------------------------------------------------------------
-- 5. 人工笔记（AI 纪要只作补充，不替代人工记录）
-- ----------------------------------------------------------------------------
CREATE TABLE interview_note (
  id         BIGINT UNSIGNED NOT NULL,
  room_id    BIGINT UNSIGNED NOT NULL,
  author_id  BIGINT UNSIGNED NOT NULL,
  author_name VARCHAR(64)   NOT NULL,
  content    TEXT           NOT NULL,
  visibility VARCHAR(16)    NOT NULL DEFAULT 'PRIVATE', -- PRIVATE / SHARED
  created_at DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_room_time (room_id, created_at)
) COMMENT='面试笔记表（面试官实时记录）';

-- ----------------------------------------------------------------------------
-- 6. 房间聊天（落库后 WS 广播；会话完成后可回放展示）
-- ----------------------------------------------------------------------------
CREATE TABLE chat_message (
  id           BIGINT UNSIGNED NOT NULL,
  room_id      BIGINT UNSIGNED NOT NULL,
  msg_no      VARCHAR(64)  NOT NULL,
  sender_id    BIGINT UNSIGNED NOT NULL,
  sender_name  VARCHAR(64)   NOT NULL,
  msg_type     VARCHAR(16)   NOT NULL DEFAULT 'TEXT', -- TEXT / SYSTEM
  content      TEXT          NOT NULL,
  visibility   VARCHAR(16)   NOT NULL DEFAULT 'ALL', -- ALL / HOST_ONLY
  sent_at      DATETIME(3)   NOT NULL,
  created_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_msg_no (msg_no),
  KEY idx_room_time (room_id, sent_at)
) COMMENT='房间聊天消息表';

-- ----------------------------------------------------------------------------
-- 7. 转写片段（实时 ASR final 段 + 会后补转写；纪要/回放字幕按序读取）
-- ----------------------------------------------------------------------------
CREATE TABLE transcript_segment (
  id           BIGINT UNSIGNED NOT NULL,
  room_id      BIGINT UNSIGNED NOT NULL,
  speaker_id   BIGINT UNSIGNED NOT NULL,             -- 参会人 ref_id（按音频来源标注）
  speaker_name VARCHAR(64)   NOT NULL,
  segment_no   INT           NOT NULL,               -- 该说话人房间内序号
  start_ms     BIGINT        NOT NULL,               -- 相对房间开始偏移
  end_ms       BIGINT        NOT NULL,
  text         TEXT          NOT NULL,
  confidence   DECIMAL(4,3) NULL,
  source       VARCHAR(16)   NOT NULL DEFAULT 'ASR_LIVE', -- ASR_LIVE / ASR_OFFLINE（会后补转写）
  created_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_room_speaker_seq (room_id, speaker_id, segment_no),
  KEY idx_room (room_id)
) COMMENT='转写片段表（1v5=6 路；超出 20 路降级会后补转写写 source=ASR_OFFLINE）';

-- ----------------------------------------------------------------------------
-- 8. 防作弊事件（只采集不自动判定，供面试官评价参考）
-- ----------------------------------------------------------------------------
CREATE TABLE cheat_event (
  id               BIGINT UNSIGNED NOT NULL,
  room_id          BIGINT UNSIGNED NOT NULL,
  candidate_id     BIGINT UNSIGNED NOT NULL,
  event_type       VARCHAR(32)  NOT NULL,            -- TAB_SWITCH/FULLSCREEN_EXIT/WINDOW_BLUR/DEVICE_CHANGE...
  severity         VARCHAR(8)   NOT NULL DEFAULT 'INFO', -- INFO/WARN/HIGH
  evidence_file_id BIGINT UNSIGNED NULL,              -- 截图等证据文件
  detail           JSON          NULL,
  occurred_at      DATETIME(3)   NOT NULL,
  created_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_room_time (room_id, occurred_at),
  KEY idx_candidate (candidate_id)
) COMMENT='防作弊事件表（浏览器埋点经 WS 上报）';

-- ============================================================================
-- 公共表（与其他库同构）
-- ============================================================================
CREATE TABLE local_message (
  id            BIGINT UNSIGNED NOT NULL,
  message_id    VARCHAR(64)  NOT NULL,
  event_type    VARCHAR(64)  NOT NULL,             -- room.finished / recording.completed ...
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
