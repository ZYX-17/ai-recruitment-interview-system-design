-- P10 房间回执与纪要：房间失败原因、AI 任务幂等、复核意见、录像登记。
ALTER TABLE interview
  ADD COLUMN room_fail_reason VARCHAR(255) NULL COMMENT 'room.create.failed 原因（人工重试排查）' AFTER note;

ALTER TABLE interview_minutes
  ADD COLUMN ai_task_id VARCHAR(64) NULL COMMENT 'ai-service 任务 ID（回调幂等）' AFTER interview_id,
  ADD COLUMN review_comment VARCHAR(1024) NULL COMMENT '确认/驳回意见' AFTER review_status,
  ADD UNIQUE KEY uk_ai_task (ai_task_id);

CREATE TABLE interview_recording (
  id           BIGINT UNSIGNED NOT NULL,
  interview_id BIGINT UNSIGNED NOT NULL,
  room_id      BIGINT UNSIGNED NULL,
  record_id    VARCHAR(64)  NOT NULL,
  file_key     VARCHAR(255) NOT NULL,
  duration_sec INT           NULL,
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_record (record_id),
  KEY idx_interview (interview_id)
) COMMENT='面试录像登记表（recording.completed 消费）';
