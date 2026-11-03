-- 排期备注（POST/PUT /api/interview/schedules 的 note）
-- 不改 V1_0 已发布的校验和，新增列用独立迁移，sql/02 同步维护。
ALTER TABLE interview
  ADD COLUMN note VARCHAR(500) NULL COMMENT '排期备注（面试须知等）' AFTER cancel_reason;
