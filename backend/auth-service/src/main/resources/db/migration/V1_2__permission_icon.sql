-- ----------------------------------------------------------------------------
-- V1_2：权限图标与乐观锁版本
--
-- A-06 动态菜单与权限树需要返回 icon；A-15 权限修改需要 version 乐观锁。
-- V1_0 未建这两列，这里补齐；seed 保持不变（图标由管理端按需维护）。
-- ----------------------------------------------------------------------------
ALTER TABLE iam_permission
  ADD COLUMN icon VARCHAR(64) NULL AFTER component,
  ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER status;
