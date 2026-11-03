-- ============================================================================
-- 招聘面试系统 · recruit_auth 字典表（P2-5 / X-11）
-- 约定：
--   1) 一行一个枚举项，唯一键 (dict_type, dict_code)；
--   2) dict_type / dict_code 创建后不可改，管理端只允许维护 label/sort/status/remark；
--   3) 种子与文档 §5.9 枚举字典对齐，INSERT IGNORE 可重复执行。
-- ============================================================================

CREATE TABLE iam_dict (
  id         BIGINT UNSIGNED NOT NULL,
  dict_type  VARCHAR(64)  NOT NULL,                -- 字典类型，如 user_status
  dict_code  VARCHAR(64)  NOT NULL,                -- 枚举码，如 ACTIVE
  dict_label VARCHAR(128) NOT NULL,                -- 展示文案
  sort       INT           NOT NULL DEFAULT 0,
  status     VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
  remark     VARCHAR(255)  NULL,
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version    INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_type_code (dict_type, dict_code),
  KEY idx_type (dict_type)
) COMMENT='字典表（前端下拉与枚举展示）';

INSERT IGNORE INTO iam_dict
  (id, dict_type, dict_code, dict_label, sort, status, remark)
VALUES
  (70001, 'common_status',    'ACTIVE',    '启用', 10, 'ACTIVE', '通用启停状态'),
  (70002, 'common_status',    'DISABLED',  '停用', 20, 'ACTIVE', '通用启停状态'),
  (70003, 'user_status',      'ACTIVE',    '正常', 10, 'ACTIVE', NULL),
  (70004, 'user_status',      'LOCKED',    '锁定', 20, 'ACTIVE', NULL),
  (70005, 'user_status',      'DISABLED',  '禁用', 30, 'ACTIVE', NULL),
  (70006, 'user_type',        'EMPLOYEE',  '员工', 10, 'ACTIVE', NULL),
  (70007, 'user_type',        'CANDIDATE', '候选人', 20, 'ACTIVE', NULL),
  (70008, 'data_scope',       'ALL',        '全部', 10, 'ACTIVE', NULL),
  (70009, 'data_scope',       'DEPARTMENT', '本部门', 20, 'ACTIVE', NULL),
  (70010, 'data_scope',       'ASSIGNED',   '被分配', 30, 'ACTIVE', NULL),
  (70011, 'data_scope',       'SELF',       '仅本人', 40, 'ACTIVE', NULL),
  (70012, 'permission_type',  'MENU',       '菜单', 10, 'ACTIVE', NULL),
  (70013, 'permission_type',  'BUTTON',     '按钮', 20, 'ACTIVE', NULL),
  (70014, 'permission_type',  'API',        '接口', 30, 'ACTIVE', NULL),
  (70015, 'audit_result',     'OK',         '成功', 10, 'ACTIVE', NULL),
  (70016, 'audit_result',     'FAIL',       '失败', 20, 'ACTIVE', NULL),
  (70017, 'refresh_status',   'ACTIVE',     '有效', 10, 'ACTIVE', NULL),
  (70018, 'refresh_status',   'REVOKED',    '已撤销', 20, 'ACTIVE', NULL),
  (70019, 'refresh_status',   'REPLAYED',   '重放失效', 30, 'ACTIVE', NULL);
