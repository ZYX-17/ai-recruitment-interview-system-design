-- ============================================================================
-- 招聘面试系统 · recruit_ai 库 DDL（v3.1）
-- 服务：ai-service（Python/FastAPI）｜ Owner：B（AI 底座）
-- 表数：6 张核心表 + 2 张可选增强表 + 2 张公共表 = 10 张
-- 说明：模型密钥不入库（Nacos 加密配置/环境变量）；本库由 Python 侧
--       经 SQLAlchemy/PyMySQL 访问，字段类型与全系统约定一致
-- ============================================================================

USE recruit_ai;

-- ----------------------------------------------------------------------------
-- 1. AI 任务（业务方经 OpenAPI 契约提交；前端轮询状态）
-- ----------------------------------------------------------------------------
CREATE TABLE ai_task (
  id            BIGINT UNSIGNED NOT NULL,
  task_no       VARCHAR(64)  NOT NULL,
  biz_type      VARCHAR(32)  NOT NULL,               -- RESUME_EXTRACT/MATCH/MINUTES/ANSWER_SCORE/PROFILE/TAG
  biz_id        VARCHAR(64)  NOT NULL,                -- 业务单据 ID
  caller        VARCHAR(32)  NOT NULL,                -- resume-service / interview-service / room-service
  status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING', -- PENDING/RUNNING/SUCCESS/FAILED/DEAD
  progress      TINYINT      NOT NULL DEFAULT 0,
  input_json    JSON         NOT NULL,
  output_json   JSON         NULL,                   -- 结果草稿（经 human_review 确认）
  model         VARCHAR(64)  NULL,                   -- 实际命中档位（vllm-qwen2.5-7b-awq/deepseek-chat/...）
  prompt_code   VARCHAR(64)  NULL,
  prompt_version INT          NULL,
  retry_count   INT           NOT NULL DEFAULT 0,
  error_msg     VARCHAR(512) NULL,
  started_at    DATETIME(3)  NULL,
  finished_at   DATETIME(3)  NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version       INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_task_no (task_no),
  KEY idx_biz (biz_type, biz_id),
  KEY idx_status (status),
  KEY idx_created (created_at)
) COMMENT='AI 任务表（能力统一收口，配额/超时/降级在此调度）';

-- ----------------------------------------------------------------------------
-- 2. Prompt 版本（变更走 PR + 固定评测集回归）
-- ----------------------------------------------------------------------------
CREATE TABLE prompt_version (
  id              BIGINT UNSIGNED NOT NULL,
  prompt_code     VARCHAR(64)  NOT NULL,             -- resume_extract / match_precision / minutes_gen / answer_score
  version         INT           NOT NULL,
  content         MEDIUMTEXT    NOT NULL,
  response_schema JSON          NULL,                 -- guided JSON Schema（vLLM guided decoding）
  model_hint      VARCHAR(64)   NULL,                 -- 建议模型档位
  status          VARCHAR(16)   NOT NULL DEFAULT 'DRAFT', -- DRAFT/ACTIVE/RETIRED
  author_id       BIGINT UNSIGNED NULL,
  created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_code_ver (prompt_code, version),
  KEY idx_status (status)
) COMMENT='Prompt 版本表（evaluation/audit 均记录 prompt_version）';

-- ----------------------------------------------------------------------------
-- 3. 模型档位配置（四级降级链：vLLM→云端→Ollama→模板；密钥不入库）
-- ----------------------------------------------------------------------------
CREATE TABLE model_config (
  id            BIGINT UNSIGNED NOT NULL,
  provider_name VARCHAR(32)  NOT NULL,               -- VLLM / CLOUD_DEEPSEEK / CLOUD_QWEN / OLLAMA / MOCK
  display_name  VARCHAR(64)  NOT NULL,
  base_url      VARCHAR(255) NOT NULL,               -- OpenAI 兼容端点
  model         VARCHAR(64)  NOT NULL,
  params_json   JSON         NULL,                   -- temperature/max_tokens/timeout...
  priority      INT          NOT NULL DEFAULT 100,    -- 数值越小越优先（100=主档）
  daily_quota   INT          NULL,                   -- 日调用配额（预算 80% 熔断）
  enabled       TINYINT(1)   NOT NULL DEFAULT 1,
  remark        VARCHAR(255) NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version       INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_enabled_priority (enabled, priority),
  KEY idx_provider (provider_name)
) COMMENT='模型配置表（密钥经环境变量/Nacos 加密配置注入，严禁写入本表）';

-- ----------------------------------------------------------------------------
-- 4. AI 结果（任务产物的规范化视图；evidence 供答辩演示）
-- ----------------------------------------------------------------------------
CREATE TABLE ai_result (
  id           BIGINT UNSIGNED NOT NULL,
  task_id      BIGINT UNSIGNED NOT NULL,
  result_json  JSON         NOT NULL,
  confidence   DECIMAL(4,3) NULL,
  evidence     JSON         NULL,                    -- 证据片段/来源定位
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_task (task_id)
) COMMENT='AI 结果表';

-- ----------------------------------------------------------------------------
-- 5. 调用日志（每次 LLM/ASR/Embedding 调用一条；预算与压测数据源）
-- ----------------------------------------------------------------------------
CREATE TABLE ai_call_log (
  id              BIGINT UNSIGNED NOT NULL,
  trace_id        VARCHAR(64)  NOT NULL,
  task_no         VARCHAR(64)  NULL,
  capability      VARCHAR(16)  NOT NULL,             -- LLM / ASR / EMBED
  provider_name   VARCHAR(32)  NOT NULL,
  model           VARCHAR(64)  NOT NULL,
  prompt_code     VARCHAR(64)  NULL,
  prompt_version  INT           NULL,
  input_tokens    INT           NULL,
  output_tokens   INT           NULL,
  latency_ms      INT           NOT NULL,
  status          VARCHAR(16)   NOT NULL,             -- OK / TIMEOUT / ERROR / DEGRADED / RATE_LIMIT
  degraded_to     VARCHAR(64)  NULL,                 -- 降级目标档位
  error_msg       VARCHAR(512) NULL,
  created_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_trace (trace_id),
  KEY idx_task (task_no),
  KEY idx_created (created_at),
  KEY idx_status (status)
) COMMENT='AI 调用日志表（W13/W14 评测与容量报告数据源）';

-- ----------------------------------------------------------------------------
-- 6. 人工复核（AI 输出一律草稿+人工确认）
-- ----------------------------------------------------------------------------
CREATE TABLE human_review (
  id           BIGINT UNSIGNED NOT NULL,
  biz_type     VARCHAR(32)  NOT NULL,                -- MATCH / MINUTES / ANSWER_SCORE / TAG / MERGE
  biz_id       VARCHAR(64)  NOT NULL,
  task_no      VARCHAR(64)  NULL,
  reviewer_id  BIGINT UNSIGNED NOT NULL,
  status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING', -- PENDING/CONFIRMED/REJECTED
  comment      VARCHAR(1024) NULL,
  reviewed_at  DATETIME(3)  NULL,
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_biz (biz_type, biz_id),
  KEY idx_status (status)
) COMMENT='人工复核表（确认后才写入业务正式状态）';

-- ----------------------------------------------------------------------------
-- 7~8. Agent 调用轨迹与工具审计（可选增强：答辩加分项，优先级最低）
-- ----------------------------------------------------------------------------
CREATE TABLE agent_trace (
  id           BIGINT UNSIGNED NOT NULL,
  task_no      VARCHAR(64)  NOT NULL,
  step_no      INT           NOT NULL,
  step_type    VARCHAR(32)  NOT NULL,               -- LLM_CALL / TOOL_CALL
  name         VARCHAR(64)  NOT NULL,                -- 模型名或工具名
  input_json   JSON         NULL,
  output_json  JSON         NULL,
  duration_ms  INT           NULL,
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_task_step (task_no, step_no)
) COMMENT='Agent 调用轨迹表（可选增强）';

CREATE TABLE ai_tool_audit (
  id           BIGINT UNSIGNED NOT NULL,
  task_no      VARCHAR(64)  NOT NULL,
  tool_name    VARCHAR(64)  NOT NULL,
  allowed      TINYINT(1)   NOT NULL,               -- 白名单判定结果
  args_hash    CHAR(64)     NULL,                   -- 参数摘要（不存原文）
  result       VARCHAR(16)  NOT NULL,               -- OK / BLOCKED / FAIL
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_task (task_no)
) COMMENT='工具白名单审计表（可选增强）';

-- ============================================================================
-- 公共表（与其他库同构；ai-service 侧 Outbox 为 Python 实现，结构一致）
-- ============================================================================
CREATE TABLE local_message (
  id            BIGINT UNSIGNED NOT NULL,
  message_id    VARCHAR(64)  NOT NULL,
  event_type    VARCHAR(64)  NOT NULL,             -- ai_task.completed ...
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
