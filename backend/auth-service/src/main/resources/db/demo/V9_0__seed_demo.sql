-- 演示数据（仅 demo profile；application-demo.yml 追加 db/demo 迁移目录）
-- 8 个演示账号：admin（seed）+ 7 个业务账号，密码统一由 SEED_ADMIN_PASSWORD 注入。
INSERT IGNORE INTO iam_user
  (id, username, password_hash, user_type, display_name, status, token_version, dept_id, version)
SELECT 10001, 'hr01', '${seedAdminPasswordHash}', 'EMPLOYEE', '招聘管理员-李', 'ACTIVE', 0, 20001, 0
FROM DUAL WHERE '${seedAdminPasswordHash}' <> '';
INSERT IGNORE INTO iam_user
  (id, username, password_hash, user_type, display_name, status, token_version, dept_id, version)
SELECT 10002, 'hr02', '${seedAdminPasswordHash}', 'EMPLOYEE', '招聘专员-王', 'ACTIVE', 0, 20002, 0
FROM DUAL WHERE '${seedAdminPasswordHash}' <> '';
INSERT IGNORE INTO iam_user
  (id, username, password_hash, user_type, display_name, status, token_version, dept_id, version)
SELECT 10003, 'interviewer01', '${seedAdminPasswordHash}', 'EMPLOYEE', '面试官-张', 'ACTIVE', 0, 20001, 0
FROM DUAL WHERE '${seedAdminPasswordHash}' <> '';
INSERT IGNORE INTO iam_user
  (id, username, password_hash, user_type, display_name, status, token_version, dept_id, version)
SELECT 10004, 'interviewer02', '${seedAdminPasswordHash}', 'EMPLOYEE', '面试官-陈', 'ACTIVE', 0, 20001, 0
FROM DUAL WHERE '${seedAdminPasswordHash}' <> '';
INSERT IGNORE INTO iam_user
  (id, username, password_hash, user_type, display_name, status, token_version, dept_id, version)
SELECT 10005, 'interviewer03', '${seedAdminPasswordHash}', 'EMPLOYEE', '面试官-赵', 'ACTIVE', 0, 20002, 0
FROM DUAL WHERE '${seedAdminPasswordHash}' <> '';
INSERT IGNORE INTO iam_user
  (id, username, password_hash, user_type, display_name, status, token_version, dept_id, version)
SELECT 10006, 'candidate01', '${seedAdminPasswordHash}', 'CANDIDATE', '候选人-孙', 'ACTIVE', 0, NULL, 0
FROM DUAL WHERE '${seedAdminPasswordHash}' <> '';
INSERT IGNORE INTO iam_user
  (id, username, password_hash, user_type, display_name, status, token_version, dept_id, version)
SELECT 10007, 'candidate02', '${seedAdminPasswordHash}', 'CANDIDATE', '候选人-周', 'ACTIVE', 0, NULL, 0
FROM DUAL WHERE '${seedAdminPasswordHash}' <> '';

INSERT IGNORE INTO iam_user_role (id, user_id, role_id) VALUES
  (500011, 10001, 40002),
  (500012, 10002, 40003),
  (500013, 10003, 40004),
  (500014, 10004, 40004),
  (500015, 10005, 40004),
  (500016, 10006, 40005),
  (500017, 10007, 40005);
