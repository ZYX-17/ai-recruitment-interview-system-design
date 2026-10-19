-- ============================================================================
-- 招聘面试系统 · 建库脚本（v3.1 · 2026-10-19）
-- 7 库逻辑隔离（database per service）；gateway-service 无库
-- Owner 对照：recruit_auth/recruit_interview/recruit_notify = A
--             recruit_resume/recruit_file/recruit_ai       = B
--             recruit_room                                 = C
-- 执行顺序：00_create_databases.sql → 01~07 各库 DDL
-- 时区约定：连接统一 UTC（JDBC serverTimezone=UTC），应用层 ISO 8601 输出
-- ============================================================================

-- A：auth-service（身份与权限中心 IAM）
CREATE DATABASE IF NOT EXISTS recruit_auth
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- A：interview-service（面试管理子系统）
CREATE DATABASE IF NOT EXISTS recruit_interview
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- C：room-service（在线面试子系统）
CREATE DATABASE IF NOT EXISTS recruit_room
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- B：resume-service（智能简历与人才管理子系统）
CREATE DATABASE IF NOT EXISTS recruit_resume
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- A：notify-service（邮件/站内信）
CREATE DATABASE IF NOT EXISTS recruit_notify
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- B：file-service（对象存储适配）
CREATE DATABASE IF NOT EXISTS recruit_file
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- B：ai-service（AI 能力编排，Python/FastAPI）
CREATE DATABASE IF NOT EXISTS recruit_ai
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
