-- P9 异步面试：门户邀请令牌、开始/提交时间、总分与人工复核留痕。
-- 说明：async_* 四表已由 V1_0 建好；本迁移只补 P9 需要的列与索引。
ALTER TABLE async_paper
  ADD COLUMN invite_token_hash VARCHAR(64) NULL COMMENT 'sha256(邀请 token)，明文只在邀请链接' AFTER settings_json,
  ADD COLUMN invite_expire_at DATETIME(3) NULL COMMENT '邀请链接过期时间' AFTER invite_token_hash,
  ADD COLUMN started_at DATETIME(3) NULL COMMENT '候选人首次开始作答时间' AFTER invite_expire_at,
  ADD COLUMN submitted_at DATETIME(3) NULL COMMENT '候选人提交时间' AFTER started_at,
  ADD COLUMN total_score DECIMAL(5,2) NULL COMMENT '加权总分（人工分优先，缺失回退 AI 分）' AFTER submitted_at,
  ADD COLUMN reviewed_by BIGINT UNSIGNED NULL COMMENT '最后人工复核人' AFTER total_score,
  ADD COLUMN reviewed_at DATETIME(3) NULL COMMENT '最后人工复核时间' AFTER reviewed_by,
  ADD UNIQUE KEY uk_invite_token (invite_token_hash),
  ADD KEY idx_status (status);

ALTER TABLE async_answer
  ADD COLUMN manual_comment VARCHAR(1024) NULL COMMENT '人工复核评语' AFTER manual_score,
  ADD COLUMN manual_scored_by BIGINT UNSIGNED NULL COMMENT '人工复核人' AFTER manual_comment,
  ADD COLUMN manual_scored_at DATETIME(3) NULL COMMENT '人工复核时间' AFTER manual_scored_by;
