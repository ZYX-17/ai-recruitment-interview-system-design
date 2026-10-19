-- ============================================================================
-- 招聘面试系统 · recruit_resume 库 DDL（v3.1）
-- 服务：resume-service ｜ Owner：B（智能简历与人才管理子系统）
-- 表数：14 张业务表 + 2 张公共表 = 16 张
-- 说明：向量检索在 ES（resume_vec：dense_vector 512 维 bge-small-zh-v1.5
--       + ik_max_word/ik_smart + BM25+kNN RRF），本库存结构化与任务状态；
--       person 归本库，interview/room 只存 person_id+快照
-- ============================================================================

USE recruit_resume;

-- ----------------------------------------------------------------------------
-- 1. 候选人（人才档案主档；MERGED 后指向 target person）
-- ----------------------------------------------------------------------------
CREATE TABLE person (
  id          BIGINT UNSIGNED NOT NULL,
  user_id     BIGINT UNSIGNED NULL,                -- 关联 iam_user（候选人注册后绑定）
  name        VARCHAR(64)  NOT NULL,
  phone_enc   VARCHAR(255) NULL,
  phone_hash  CHAR(64)     NULL,                    -- 手机号查重/匹配
  email       VARCHAR(128) NULL,
  city        VARCHAR(64)  NULL,
  education   VARCHAR(32)  NULL,                    -- 博士/硕士/本科/大专/其他
  work_years  INT           NULL,
  status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / MERGED / BLACKLIST
  merged_to   BIGINT UNSIGNED NULL,                -- 合并后指向目标 person
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version     INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user (user_id),
  KEY idx_phone_hash (phone_hash),
  KEY idx_name (name),
  KEY idx_status (status)
) COMMENT='候选人/人才主档表';

-- ----------------------------------------------------------------------------
-- 2. 简历（sha256 判重：同人重传相同文件直接命中）
-- ----------------------------------------------------------------------------
CREATE TABLE resume (
  id                BIGINT UNSIGNED NOT NULL,
  resume_no         VARCHAR(64)  NOT NULL,
  person_id         BIGINT UNSIGNED NOT NULL,
  file_id           BIGINT UNSIGNED NOT NULL,       -- file-service 原始文件
  file_key          VARCHAR(255) NOT NULL,
  file_name         VARCHAR(255) NOT NULL,
  sha256            CHAR(64)     NOT NULL,
  channel           VARCHAR(32)  NOT NULL DEFAULT 'PORTAL', -- PORTAL/EMAIL/HR_IMPORT
  parse_status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING', -- PENDING/PARSING/STRUCTURING/INDEXING/SUCCESS/FAILED
  structured_json   JSON         NULL,             -- 最新结构化结果
  markdown_file_id  BIGINT UNSIGNED NULL,           -- MinerU 结构化 Markdown 文件
  current_version_no INT          NOT NULL DEFAULT 0,
  created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version           INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_resume_no (resume_no),
  UNIQUE KEY uk_sha256 (sha256),
  KEY idx_person (person_id),
  KEY idx_parse_status (parse_status)
) COMMENT='简历表';

-- ----------------------------------------------------------------------------
-- 3. 简历解析版本（不覆盖旧版本；parser/model 版本留痕）
-- ----------------------------------------------------------------------------
CREATE TABLE resume_version (
  id                BIGINT UNSIGNED NOT NULL,
  resume_id         BIGINT UNSIGNED NOT NULL,
  version_no        INT           NOT NULL,
  parser_version    VARCHAR(32)  NOT NULL,          -- 如 mineru-1.x
  extract_model     VARCHAR(64)   NULL,             -- LLM 抽取模型
  prompt_version    INT           NULL,
  structured_json   JSON          NOT NULL,
  markdown_file_id  BIGINT UNSIGNED NULL,
  created_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_resume_ver (resume_id, version_no)
) COMMENT='简历解析版本表';

-- ----------------------------------------------------------------------------
-- 4. 解析任务（前端轮询状态；retry ≤3 次转死信走补偿页）
-- ----------------------------------------------------------------------------
CREATE TABLE resume_parse_task (
  id           BIGINT UNSIGNED NOT NULL,
  task_no      VARCHAR(64)  NOT NULL,
  resume_id    BIGINT UNSIGNED NOT NULL,
  ai_task_no   VARCHAR(64)  NULL,                   -- recruit_ai.ai_task.task_no
  status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING', -- PENDING/RUNNING/SUCCESS/FAILED/DEAD
  stage        VARCHAR(32)  NOT NULL DEFAULT 'PARSE', -- PARSE/CHUNK/EMBED/INDEX
  progress     TINYINT      NOT NULL DEFAULT 0,     -- 0~100
  error_msg    VARCHAR(512) NULL,
  retry_count  INT           NOT NULL DEFAULT 0,
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version      INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_task_no (task_no),
  KEY idx_resume (resume_id),
  KEY idx_status (status)
) COMMENT='简历解析任务表';

-- ----------------------------------------------------------------------------
-- 5. 分块（表格独立成块；es_doc_id 关联 ES 文档）
-- ----------------------------------------------------------------------------
CREATE TABLE resume_chunk (
  id              BIGINT UNSIGNED NOT NULL,
  resume_id       BIGINT UNSIGNED NOT NULL,
  chunk_no        INT           NOT NULL,
  chunk_type      VARCHAR(16)   NOT NULL DEFAULT 'TEXT', -- TEXT / TABLE（表头重复+标题路径前缀）
  section_path    VARCHAR(255)  NULL,               -- 标题路径，如 教育/硕士/XX大学
  content         MEDIUMTEXT     NOT NULL,
  es_doc_id       VARCHAR(64)   NOT NULL,           -- ES resume_vec 文档 ID
  embedding_model VARCHAR(64)   NULL,               -- bge-small-zh-v1.5
  created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_resume_chunk (resume_id, chunk_no),
  KEY idx_es_doc (es_doc_id)
) COMMENT='简历分块表（与 ES 文档一一对应，ES 重建以本表为准）';

-- ----------------------------------------------------------------------------
-- 6. 技能（AI 抽取 + 人工补录）
-- ----------------------------------------------------------------------------
CREATE TABLE candidate_skill (
  id          BIGINT UNSIGNED NOT NULL,
  person_id   BIGINT UNSIGNED NOT NULL,
  skill_name  VARCHAR(64)  NOT NULL,
  level       VARCHAR(16)  NULL,                    -- 了解/熟练/精通
  years       DECIMAL(3,1) NULL,
  source      VARCHAR(16)  NOT NULL DEFAULT 'EXTRACTED', -- EXTRACTED / MANUAL
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_person_skill (person_id, skill_name)
) COMMENT='候选人技能表';

-- ----------------------------------------------------------------------------
-- 7~8. 标签体系
-- ----------------------------------------------------------------------------
CREATE TABLE tag (
  id         BIGINT UNSIGNED NOT NULL,
  tag_code   VARCHAR(64)  NOT NULL,
  tag_name   VARCHAR(64)  NOT NULL,
  dimension  VARCHAR(32)  NOT NULL DEFAULT 'GENERAL', -- GENERAL/技能/风格/级别
  status     VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tag_code (tag_code)
) COMMENT='标签字典表';

CREATE TABLE candidate_tag (
  id           BIGINT UNSIGNED NOT NULL,
  person_id    BIGINT UNSIGNED NOT NULL,
  tag_id       BIGINT UNSIGNED NOT NULL,
  source       VARCHAR(8)   NOT NULL DEFAULT 'AI',   -- HR / AI
  operator_id  BIGINT UNSIGNED NULL,
  confidence   DECIMAL(4,3) NULL,                   -- AI 打标置信度
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_person_tag (person_id, tag_id),
  KEY idx_tag (tag_id)
) COMMENT='候选人标签关联表';

-- ----------------------------------------------------------------------------
-- 9~10. 人才库（归档等级/跟进提醒）
-- ----------------------------------------------------------------------------
CREATE TABLE talent_profile (
  id                BIGINT UNSIGNED NOT NULL,
  person_id         BIGINT UNSIGNED NOT NULL,
  archive_level     VARCHAR(16)  NOT NULL DEFAULT 'SILVER', -- SILVER / GOLD / PLATINUM
  summary           VARCHAR(1024) NULL,             -- AI 画像摘要（草稿+人工确认）
  interview_history JSON          NULL,             -- [{round,stage,result,score}]
  owner_hr_id       BIGINT UNSIGNED NULL,
  next_followup_at  DATETIME(3)   NULL,             -- 延迟消息提醒
  created_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version           INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_person (person_id),
  KEY idx_followup (next_followup_at),
  KEY idx_owner (owner_hr_id)
) COMMENT='人才库档案表';

CREATE TABLE talent_followup (
  id               BIGINT UNSIGNED NOT NULL,
  talent_profile_id BIGINT UNSIGNED NOT NULL,
  hr_id            BIGINT UNSIGNED NOT NULL,
  content          VARCHAR(1024) NOT NULL,
  next_followup_at DATETIME(3)   NULL,
  created_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_talent (talent_profile_id),
  KEY idx_hr (hr_id)
) COMMENT='人才跟进记录表';

-- ----------------------------------------------------------------------------
-- 11. 硬过滤规则（匹配第一路：规则筛 + ES 召回）
-- ----------------------------------------------------------------------------
CREATE TABLE screening_rule (
  id          BIGINT UNSIGNED NOT NULL,
  job_id      BIGINT UNSIGNED NOT NULL,             -- interview 库岗位（快照引用）
  rules_json  JSON         NOT NULL,                -- [{field,op,value}] 学历/年限/城市/技能硬条件
  match_mode  VARCHAR(16)  NOT NULL DEFAULT 'HYBRID', -- RECALL_ONLY / HYBRID（召回+LLM 精排）
  enabled     TINYINT(1)   NOT NULL DEFAULT 1,
  priority    INT          NOT NULL DEFAULT 0,
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version     INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_job (job_id)
) COMMENT='硬过滤规则表（ES 挂时切 mysql-like 查询兜底）';

-- ----------------------------------------------------------------------------
-- 12. 匹配结果（final = 0.3×召回 + 0.7×LLM；cache_key 防重复计算）
-- ----------------------------------------------------------------------------
CREATE TABLE match_result (
  id             BIGINT UNSIGNED NOT NULL,
  job_id         BIGINT UNSIGNED NOT NULL,
  resume_id      BIGINT UNSIGNED NOT NULL,
  person_id      BIGINT UNSIGNED NOT NULL,
  overall_score  DECIMAL(5,2) NOT NULL,             -- 0~100
  recall_score   DECIMAL(5,2) NULL,
  llm_score      DECIMAL(5,2) NULL,
  score_detail   JSON         NULL,                 -- 分项（技能/经验/学历匹配）
  evidence       JSON         NULL,                 -- 证据片段（答辩演示：评分理由）
  gaps           JSON         NULL,                 -- 缺口
  cache_key      VARCHAR(128) NOT NULL,             -- job+resume(+版本) 派生
  model          VARCHAR(64)  NULL,
  prompt_version INT           NULL,
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_cache (cache_key),
  KEY idx_job_score (job_id, overall_score DESC),
  KEY idx_resume (resume_id),
  KEY idx_person (person_id)
) COMMENT='人岗匹配结果表（Top5 命中率评测数据源）';

-- ----------------------------------------------------------------------------
-- 13. 简历合并记录（查重：sha256 完全相同 / phone_hash 相同 / kNN 相似度 ≥0.92）
-- ----------------------------------------------------------------------------
CREATE TABLE resume_merge_record (
  id                BIGINT UNSIGNED NOT NULL,
  source_person_id  BIGINT UNSIGNED NOT NULL,       -- 被合并方
  target_person_id  BIGINT UNSIGNED NOT NULL,       -- 保留方
  similarity        DECIMAL(4,3) NULL,
  dedup_type        VARCHAR(16)  NOT NULL,           -- SHA256 / PHONE / VECTOR
  status            VARCHAR(16)  NOT NULL DEFAULT 'PENDING', -- PENDING/CONFIRMED/REJECTED
  evidence          JSON         NULL,
  operator_id       BIGINT UNSIGNED NULL,
  created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version           INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_source (source_person_id),
  KEY idx_target (target_person_id)
) COMMENT='简历合并审核表（高相似异人须人工确认不误合并）';

-- ----------------------------------------------------------------------------
-- 14. 岗位快照（由 job.published/job.updated 事件维护，匹配只读本库）
-- ----------------------------------------------------------------------------
CREATE TABLE job_snapshot (
  id                BIGINT UNSIGNED NOT NULL,       -- = interview 库 job.id
  job_version       INT           NOT NULL DEFAULT 0,
  title             VARCHAR(128) NOT NULL,
  dept_name         VARCHAR(64)  NULL,
  requirements_json JSON         NULL,
  status            VARCHAR(16)  NOT NULL,
  source_event_id   VARCHAR(64)  NULL,
  created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_status (status)
) COMMENT='岗位快照表（事件驱动维护，resume 匹配不跨库读 interview）';

-- ============================================================================
-- 公共表（与其他库同构）
-- ============================================================================
CREATE TABLE local_message (
  id            BIGINT UNSIGNED NOT NULL,
  message_id    VARCHAR(64)  NOT NULL,
  event_type    VARCHAR(64)  NOT NULL,             -- resume.parsed / match.completed ...
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
