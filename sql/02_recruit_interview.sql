-- ============================================================================
-- 招聘面试系统 · recruit_interview 库 DDL（v3.1）
-- 服务：interview-service ｜ Owner：A（面试管理子系统）
-- 表数：14 张业务表 + 2 张公共表 = 16 张
-- 说明：job/application 归本库；resume 侧经 job.published/job.updated
--       事件维护 job_snapshot 做匹配（不跨库 JOIN）；
--       person 归 resume 库，本库只存 person_id + 姓名/岗位快照
-- ============================================================================

USE recruit_interview;

-- ----------------------------------------------------------------------------
-- 1. 岗位
-- ----------------------------------------------------------------------------
CREATE TABLE job (
  id              BIGINT UNSIGNED NOT NULL,
  job_code        VARCHAR(64)  NOT NULL,
  title           VARCHAR(128) NOT NULL,
  dept_id         BIGINT UNSIGNED NULL,
  dept_name       VARCHAR(64)  NULL,               -- 部门快照（展示用）
  jd_text         MEDIUMTEXT    NULL,
  requirements_json JSON        NULL,              -- 硬性要求（学历/年限/技能）
  headcount       INT           NOT NULL DEFAULT 1,
  salary_range    VARCHAR(64)   NULL,
  status          VARCHAR(16)   NOT NULL DEFAULT 'DRAFT', -- DRAFT/PUBLISHED/PAUSED/CLOSED
  owner_hr_id     BIGINT UNSIGNED NOT NULL,
  published_at    DATETIME(3)   NULL,
  created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version         INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_job_code (job_code),
  KEY idx_status (status),
  KEY idx_dept (dept_id),
  KEY idx_owner (owner_hr_id)
) COMMENT='岗位表（job.published/updated 事件的聚合根）';

-- ----------------------------------------------------------------------------
-- 2. 岗位阶段与评分模板（模板版本化：新岗位复制生效版本修改，旧版本只读）
-- ----------------------------------------------------------------------------
CREATE TABLE job_stage_template (
  id           BIGINT UNSIGNED NOT NULL,
  job_id       BIGINT UNSIGNED NOT NULL,
  template_ver INT           NOT NULL DEFAULT 1,
  stage_flow   JSON          NOT NULL,             -- [{stageNo,stageName,mode,rounds...}]
  score_dims   JSON          NOT NULL,             -- 评分维度与权重
  status       VARCHAR(16)   NOT NULL DEFAULT 'DRAFT', -- DRAFT / ACTIVE
  created_by   BIGINT UNSIGNED NOT NULL,
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version      INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_job_ver (job_id, template_ver),
  KEY idx_job_status (job_id, status)
) COMMENT='岗位流程阶段模板（版本化，evaluation.template_version 追溯）';

-- ----------------------------------------------------------------------------
-- 3. 投递申请（候选人进入面试流程的聚合根；显式状态机校验流转）
-- ----------------------------------------------------------------------------
CREATE TABLE application (
  id            BIGINT UNSIGNED NOT NULL,
  job_id        BIGINT UNSIGNED NOT NULL,
  job_title     VARCHAR(128) NOT NULL,             -- 岗位快照
  person_id     BIGINT UNSIGNED NOT NULL,          -- resume 库 person
  person_name   VARCHAR(64)  NOT NULL,              -- 姓名快照
  current_stage VARCHAR(32)  NOT NULL,              -- 流程阶段编码
  round_no      INT           NOT NULL DEFAULT 0,   -- 当前轮次
  stage_flow    JSON         NOT NULL,              -- 按岗位模板复制的流转配置
  match_score   DECIMAL(5,2) NULL,                 -- AI 匹配分快照（0~100）
  status        VARCHAR(16)  NOT NULL DEFAULT 'IN_PROGRESS', -- IN_PROGRESS/PASSED/REJECTED/WITHDRAWN/OFFERED/HIRED
  reject_reason VARCHAR(255) NULL,
  source        VARCHAR(32)  NOT NULL DEFAULT 'PORTAL', -- PORTAL / IMPORT
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version       INT           NOT NULL DEFAULT 0,  -- 乐观锁（流转必须 version 匹配）
  PRIMARY KEY (id),
  UNIQUE KEY uk_job_person (job_id, person_id),
  KEY idx_person (person_id),
  KEY idx_status (status),
  KEY idx_stage (current_stage)
) COMMENT='投递申请表（状态机集中流转）';

-- ----------------------------------------------------------------------------
-- 4. 阶段流转历史（只增不改）
-- ----------------------------------------------------------------------------
CREATE TABLE application_stage_history (
  id             BIGINT UNSIGNED NOT NULL,
  application_id BIGINT UNSIGNED NOT NULL,
  stage_no       VARCHAR(32)  NOT NULL,
  round_no       INT           NOT NULL DEFAULT 0,
  status         VARCHAR(16)  NOT NULL,
  result         VARCHAR(16)  NULL,                -- PASS / FAIL / PENDING
  operator_id    BIGINT UNSIGNED NOT NULL,
  comment        VARCHAR(512) NULL,
  operated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_application (application_id),
  KEY idx_operated (operated_at)
) COMMENT='阶段流转历史表';

-- ----------------------------------------------------------------------------
-- 5. 面试安排（一轮面试；room 由 room-service 监听 interview.scheduled 建房）
-- ----------------------------------------------------------------------------
CREATE TABLE interview (
  id              BIGINT UNSIGNED NOT NULL,
  application_id   BIGINT UNSIGNED NOT NULL,
  round_no        INT           NOT NULL DEFAULT 1,
  interviewer_ids JSON          NOT NULL,          -- 面试官用户 ID 数组
  start_time      DATETIME(3)   NOT NULL,          -- 30 分钟对齐契约
  end_time        DATETIME(3)   NOT NULL,
  mode            VARCHAR(16)   NOT NULL,          -- ONLINE / ASYNC
  room_type       VARCHAR(24)   NOT NULL DEFAULT 'ONE_TO_ONE', -- ONE_TO_ONE / ONE_TO_MANY
  room_id         BIGINT UNSIGNED NULL,           -- room-service 回填
  status          VARCHAR(16)   NOT NULL DEFAULT 'SCHEDULED', -- SCHEDULED/IN_PROGRESS/COMPLETED/CANCELLED
  cancel_reason   VARCHAR(255)  NULL,
  created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version         INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_round (application_id, round_no),
  KEY idx_room (room_id),
  KEY idx_start (start_time),
  KEY idx_status (status)
) COMMENT='面试安排表';

-- ----------------------------------------------------------------------------
-- 6. 排期格子（三方冲突兜底：唯一索引拦截，取消物理删行）
-- ----------------------------------------------------------------------------
CREATE TABLE schedule_slot (
  id               BIGINT UNSIGNED NOT NULL,
  participant_type VARCHAR(16) NOT NULL,            -- INTERVIEWER / CANDIDATE / RESOURCE
  participant_id   BIGINT UNSIGNED NOT NULL,
  slot_date        DATE        NOT NULL,
  slot_no          TINYINT     NOT NULL,            -- 0~47，30 分钟/格；先删后插由取消删行保证
  interview_id     BIGINT UNSIGNED NOT NULL,
  created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_slot (participant_type, participant_id, slot_date, slot_no),
  KEY idx_interview (interview_id),
  KEY idx_date (slot_date)
) COMMENT='排期格子表（唯一索引兜底：同事务 INSERT 捕获 DuplicateKeyException 返回 ITV3101）';

-- ----------------------------------------------------------------------------
-- 7. 面试评价
-- ----------------------------------------------------------------------------
CREATE TABLE evaluation (
  id               BIGINT UNSIGNED NOT NULL,
  interview_id     BIGINT UNSIGNED NOT NULL,
  round_no         INT           NOT NULL DEFAULT 1,
  interviewer_id   BIGINT UNSIGNED NOT NULL,
  dimension_scores JSON          NOT NULL,         -- {dimCode: score}
  comment          VARCHAR(1024) NULL,
  result           VARCHAR(16)   NOT NULL DEFAULT 'PENDING', -- PASS / FAIL / PENDING
  template_version INT           NOT NULL DEFAULT 1, -- 对应 job_stage_template.template_ver
  submitted_at     DATETIME(3)   NULL,
  created_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version          INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_eval (interview_id, round_no, interviewer_id),
  KEY idx_interviewer (interviewer_id)
) COMMENT='面试评价表（评价对比视图按 application 聚合各轮结果）';

-- ----------------------------------------------------------------------------
-- 8. 评分维度（模板维度明细，供评价表单渲染与校验）
-- ----------------------------------------------------------------------------
CREATE TABLE evaluation_dimension (
  id            BIGINT UNSIGNED NOT NULL,
  template_id   BIGINT UNSIGNED NOT NULL,           -- job_stage_template.id
  dim_code      VARCHAR(32)  NOT NULL,
  dim_name      VARCHAR(64)  NOT NULL,
  weight        DECIMAL(5,2) NOT NULL DEFAULT 1.00, -- 权重合计建议 1.0
  full_score    DECIMAL(5,2) NOT NULL DEFAULT 100.00,
  veto_threshold DECIMAL(5,2) NULL,                 -- 否决阈值：单项低于则整体否决
  sort          INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_template_dim (template_id, dim_code)
) COMMENT='评分维度表';

-- ----------------------------------------------------------------------------
-- 9. AI 会议纪要（room.finished + 转写就绪触发生成；人工确认后才可参与决策）
-- ----------------------------------------------------------------------------
CREATE TABLE interview_minutes (
  id                   BIGINT UNSIGNED NOT NULL,
  interview_id         BIGINT UNSIGNED NOT NULL,
  transcript_object_key VARCHAR(255) NULL,          -- 转写全文对象键（MinIO）
  transcript_segment_count INT      NOT NULL DEFAULT 0, -- 转写片段数，0 则标 INSUFFICIENT_TRANSCRIPT
  minutes_md           MEDIUMTEXT    NULL,          -- 纪要 Markdown 草稿
  ai_evaluation        JSON          NULL,          -- AI 提取的要点/风险/建议
  review_status        VARCHAR(16)   NOT NULL DEFAULT 'PENDING', -- PENDING/CONFIRMED/REJECTED/INSUFFICIENT_TRANSCRIPT
  reviewer_id          BIGINT UNSIGNED NULL,
  reviewed_at          DATETIME(3)   NULL,
  model                VARCHAR(64)   NULL,
  prompt_version       INT           NOT NULL DEFAULT 1,
  created_at           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version              INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_interview (interview_id)
) COMMENT='AI 会议纪要表（AI 只做草稿，经人工复核确认）';

-- ----------------------------------------------------------------------------
-- 10. Offer（确认走无登录 confirm_token，CAS 更新防并发重复确认）
-- ----------------------------------------------------------------------------
CREATE TABLE offer (
  id                 BIGINT UNSIGNED NOT NULL,
  offer_no           VARCHAR(64)  NOT NULL,
  application_id     BIGINT UNSIGNED NOT NULL,
  position_title     VARCHAR(128) NOT NULL,         -- 岗位快照
  salary_json        JSON         NULL,
  entry_date         DATE         NULL,
  expire_at          DATETIME(3)  NOT NULL,          -- 过期自动失效（延迟消息）
  confirm_token_hash CHAR(64)     NOT NULL,          -- sha256(token)，邮件链接不存明文
  status             VARCHAR(16)  NOT NULL DEFAULT 'DRAFT', -- DRAFT/SENT/CONFIRMED/EXPIRED/REJECTED/WITHDRAWN
  onboarding_json    JSON         NULL,              -- 入职引导信息
  operator_id        BIGINT UNSIGNED NOT NULL,
  confirmed_at       DATETIME(3)  NULL,
  created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version            INT           NOT NULL DEFAULT 0, -- CAS：UPDATE ... WHERE status='SENT' AND version=?
  PRIMARY KEY (id),
  UNIQUE KEY uk_application (application_id),
  UNIQUE KEY uk_offer_no (offer_no),
  UNIQUE KEY uk_confirm_token (confirm_token_hash),
  KEY idx_expire (expire_at)
) COMMENT='Offer 表';

-- ----------------------------------------------------------------------------
-- 11~14. 异步录播面试（Should-High，Must 全绿后启动）
-- ----------------------------------------------------------------------------
CREATE TABLE async_paper (
  id               BIGINT UNSIGNED NOT NULL,
  paper_no         VARCHAR(64)  NOT NULL,
  application_id   BIGINT UNSIGNED NOT NULL,
  status           VARCHAR(16)  NOT NULL DEFAULT 'DRAFT', -- DRAFT/RELEASED/IN_PROGRESS/SUBMITTED/SCORED
  question_count   INT           NOT NULL DEFAULT 0,
  duration_limit_sec INT         NOT NULL DEFAULT 3600,
  settings_json    JSON          NULL,              -- 拍摄要求/防作弊开关
  created_by       BIGINT UNSIGNED NOT NULL,
  created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version          INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_paper_no (paper_no),
  UNIQUE KEY uk_application (application_id)
) COMMENT='异步面试试卷表（一份投递一份卷）';

CREATE TABLE async_question (
  id           BIGINT UNSIGNED NOT NULL,
  paper_id     BIGINT UNSIGNED NOT NULL,
  seq_no       INT           NOT NULL,
  question_text TEXT          NOT NULL,
  prepare_sec  INT           NOT NULL DEFAULT 60,   -- 读题准备时长
  answer_sec   INT           NOT NULL DEFAULT 180,  -- 作答时长上限
  weight       DECIMAL(5,2)  NOT NULL DEFAULT 1.00,
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_paper_seq (paper_id, seq_no)
) COMMENT='异步面试题目表';

CREATE TABLE async_answer (
  id               BIGINT UNSIGNED NOT NULL,
  question_id      BIGINT UNSIGNED NOT NULL,
  candidate_id     BIGINT UNSIGNED NOT NULL,        -- person/user id
  video_file_id    BIGINT UNSIGNED NULL,            -- file-service 文件 ID
  audio_file_id    BIGINT UNSIGNED NULL,
  answer_sec       INT           NULL,              -- 实际作答时长
  transcript_text  MEDIUMTEXT    NULL,              -- 逐题 ASR 转写
  ai_score         DECIMAL(5,2)  NULL,              -- AI 评分草稿
  manual_score     DECIMAL(5,2)  NULL,              -- 人工复核分
  status           VARCHAR(16)   NOT NULL DEFAULT 'PENDING', -- PENDING/SUBMITTED/TRANSCRIBED/AI_SCORED/REVIEWED
  submitted_at     DATETIME(3)   NULL,
  created_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version          INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_question (question_id),
  KEY idx_candidate (candidate_id)
) COMMENT='异步面试作答表';

CREATE TABLE async_answer_score (
  id               BIGINT UNSIGNED NOT NULL,
  answer_id        BIGINT UNSIGNED NOT NULL,
  dimension_scores JSON          NOT NULL,
  comment          VARCHAR(1024) NULL,
  model            VARCHAR(64)   NOT NULL,
  prompt_version   INT           NOT NULL DEFAULT 1,
  created_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_answer (answer_id)
) COMMENT='异步作答 AI 评分表（留痕模型与 Prompt 版本）';

-- ============================================================================
-- 公共表（与其他库同构）
-- ============================================================================
CREATE TABLE local_message (
  id            BIGINT UNSIGNED NOT NULL,
  message_id    VARCHAR(64)  NOT NULL,
  event_type    VARCHAR(64)  NOT NULL,             -- job.published / job.updated / interview.scheduled / offer.sent ...
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
