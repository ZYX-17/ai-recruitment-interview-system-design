-- 评价重开留痕（P6-7）：提交后不可改，HR_ADMIN 重开记录操作人/时间/原因。
ALTER TABLE evaluation
  ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED'
    COMMENT 'SUBMITTED/REOPENED' AFTER result,
  ADD COLUMN reopened_by BIGINT UNSIGNED NULL COMMENT '重开人（HR_ADMIN）' AFTER submitted_at,
  ADD COLUMN reopened_at DATETIME(3) NULL AFTER reopened_by,
  ADD COLUMN reopen_reason VARCHAR(255) NULL AFTER reopened_at;
