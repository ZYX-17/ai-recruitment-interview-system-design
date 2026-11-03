-- 演示数据（仅 demo profile；application-demo.yml 追加 db/demo 迁移目录）
-- 3 岗位 / 3 流程模板 / 12 投递 / 6 场面试 / 2 份 Offer；固定 ID + INSERT IGNORE 便于重复执行。

INSERT IGNORE INTO job
  (id, job_code, title, dept_id, dept_name, jd_text, requirements_json, headcount,
   salary_range, status, owner_hr_id, published_at, version)
VALUES
  (50001, 'JOB-DEMO-JAVA', 'Java 开发工程师', 20001, '研发中心',
   '负责招聘面试系统后端服务开发。', '{"education":"本科","years":3,"skills":["Java","Spring Boot"]}',
   2, '20k-35k', 'PUBLISHED', 10001, '2026-10-01 01:00:00.000', 0),
  (50002, 'JOB-DEMO-FE', '前端开发工程师', 20001, '研发中心',
   '负责面试系统 Web 端开发。', '{"education":"本科","years":2,"skills":["Vue 3","TypeScript"]}',
   1, '18k-30k', 'PUBLISHED', 10001, '2026-10-02 01:00:00.000', 0),
  (50003, 'JOB-DEMO-HRBP', 'HRBP', 20002, '人力资源部',
   '负责业务线招聘与人才运营。', '{"education":"本科","years":3,"skills":["招聘","人才运营"]}',
   1, '15k-25k', 'DRAFT', 10002, NULL, 0);

INSERT IGNORE INTO job_stage_template
  (id, job_id, template_ver, stage_flow, score_dims, status, created_by, version)
VALUES
  (55001, 50001, 1,
   '[{"stageNo":"SCREEN","stageName":"初筛","mode":"INTERNAL","rounds":0},{"stageNo":"FIRST","stageName":"一面","mode":"ONLINE","rounds":1},{"stageNo":"OFFER","stageName":"Offer","mode":"OFFER","rounds":0},{"stageNo":"HIRED","stageName":"入职","mode":"OFFER","rounds":0}]',
   '[{"dimCode":"TECH","dimName":"技术深度","weight":0.5,"fullScore":10},{"dimCode":"COMM","dimName":"沟通表达","weight":0.3,"fullScore":10},{"dimCode":"MATCH","dimName":"岗位匹配","weight":0.2,"fullScore":10}]',
   'ACTIVE', 10001, 0),
  (55002, 50002, 1,
   '[{"stageNo":"SCREEN","stageName":"初筛","mode":"INTERNAL","rounds":0},{"stageNo":"FIRST","stageName":"一面","mode":"ONLINE","rounds":1},{"stageNo":"OFFER","stageName":"Offer","mode":"OFFER","rounds":0},{"stageNo":"HIRED","stageName":"入职","mode":"OFFER","rounds":0}]',
   '[{"dimCode":"FE","dimName":"前端基础","weight":0.5,"fullScore":10},{"dimCode":"COMM","dimName":"沟通表达","weight":0.3,"fullScore":10},{"dimCode":"MATCH","dimName":"岗位匹配","weight":0.2,"fullScore":10}]',
   'ACTIVE', 10001, 0),
  (55003, 50003, 1,
   '[{"stageNo":"SCREEN","stageName":"初筛","mode":"INTERNAL","rounds":0},{"stageNo":"FIRST","stageName":"一面","mode":"ONLINE","rounds":1},{"stageNo":"OFFER","stageName":"Offer","mode":"OFFER","rounds":0},{"stageNo":"HIRED","stageName":"入职","mode":"OFFER","rounds":0}]',
   '[{"dimCode":"HR","dimName":"招聘专业度","weight":0.6,"fullScore":10},{"dimCode":"COMM","dimName":"沟通表达","weight":0.4,"fullScore":10}]',
   'ACTIVE', 10002, 0);

INSERT IGNORE INTO application
  (id, job_id, job_title, person_id, person_name, current_stage, round_no, stage_flow,
   match_score, status, source, version)
VALUES
  (51001, 50001, 'Java 开发工程师', 10006, '候选人-孙', 'FIRST', 1,
   '[{"stageNo":"SCREEN","stageName":"初筛","mode":"INTERNAL","rounds":0},{"stageNo":"FIRST","stageName":"一面","mode":"ONLINE","rounds":1},{"stageNo":"OFFER","stageName":"Offer","mode":"OFFER","rounds":0},{"stageNo":"HIRED","stageName":"入职","mode":"OFFER","rounds":0}]',
   88.50, 'IN_PROGRESS', 'PORTAL', 0),
  (51002, 50001, 'Java 开发工程师', 60002, '李四', 'SCREEN', 0,
   '[{"stageNo":"SCREEN","stageName":"初筛","mode":"INTERNAL","rounds":0},{"stageNo":"FIRST","stageName":"一面","mode":"ONLINE","rounds":1},{"stageNo":"OFFER","stageName":"Offer","mode":"OFFER","rounds":0},{"stageNo":"HIRED","stageName":"入职","mode":"OFFER","rounds":0}]',
   76.00, 'IN_PROGRESS', 'PORTAL', 0),
  (51003, 50001, 'Java 开发工程师', 60003, '王五', 'OFFER', 1,
   '[{"stageNo":"SCREEN","stageName":"初筛","mode":"INTERNAL","rounds":0},{"stageNo":"FIRST","stageName":"一面","mode":"ONLINE","rounds":1},{"stageNo":"OFFER","stageName":"Offer","mode":"OFFER","rounds":0},{"stageNo":"HIRED","stageName":"入职","mode":"OFFER","rounds":0}]',
   92.00, 'OFFERED', 'PORTAL', 0),
  (51004, 50001, 'Java 开发工程师', 60004, '赵六', 'HIRED', 1,
   '[{"stageNo":"SCREEN","stageName":"初筛","mode":"INTERNAL","rounds":0},{"stageNo":"FIRST","stageName":"一面","mode":"ONLINE","rounds":1},{"stageNo":"OFFER","stageName":"Offer","mode":"OFFER","rounds":0},{"stageNo":"HIRED","stageName":"入职","mode":"OFFER","rounds":0}]',
   95.00, 'HIRED', 'PORTAL', 0),
  (51005, 50002, '前端开发工程师', 60005, '钱七', 'FIRST', 1,
   '[{"stageNo":"SCREEN","stageName":"初筛","mode":"INTERNAL","rounds":0},{"stageNo":"FIRST","stageName":"一面","mode":"ONLINE","rounds":1},{"stageNo":"OFFER","stageName":"Offer","mode":"OFFER","rounds":0},{"stageNo":"HIRED","stageName":"入职","mode":"OFFER","rounds":0}]',
   81.00, 'IN_PROGRESS', 'PORTAL', 0),
  (51006, 50002, '前端开发工程师', 10006, '候选人-孙', 'SCREEN', 0,
   '[{"stageNo":"SCREEN","stageName":"初筛","mode":"INTERNAL","rounds":0},{"stageNo":"FIRST","stageName":"一面","mode":"ONLINE","rounds":1},{"stageNo":"OFFER","stageName":"Offer","mode":"OFFER","rounds":0},{"stageNo":"HIRED","stageName":"入职","mode":"OFFER","rounds":0}]',
   70.00, 'IN_PROGRESS', 'IMPORT', 0),
  (51007, 50002, '前端开发工程师', 60007, '周九', 'OFFER', 1,
   '[{"stageNo":"SCREEN","stageName":"初筛","mode":"INTERNAL","rounds":0},{"stageNo":"FIRST","stageName":"一面","mode":"ONLINE","rounds":1},{"stageNo":"OFFER","stageName":"Offer","mode":"OFFER","rounds":0},{"stageNo":"HIRED","stageName":"入职","mode":"OFFER","rounds":0}]',
   89.00, 'OFFERED', 'PORTAL', 0),
  (51008, 50002, '前端开发工程师', 60008, '吴十', 'SCREEN', 0,
   '[{"stageNo":"SCREEN","stageName":"初筛","mode":"INTERNAL","rounds":0},{"stageNo":"FIRST","stageName":"一面","mode":"ONLINE","rounds":1},{"stageNo":"OFFER","stageName":"Offer","mode":"OFFER","rounds":0},{"stageNo":"HIRED","stageName":"入职","mode":"OFFER","rounds":0}]',
   62.50, 'REJECTED', 'PORTAL', 0),
  (51009, 50001, 'Java 开发工程师', 10007, '候选人-周', 'SCREEN', 0,
   '[{"stageNo":"SCREEN","stageName":"初筛","mode":"INTERNAL","rounds":0},{"stageNo":"FIRST","stageName":"一面","mode":"ONLINE","rounds":1},{"stageNo":"OFFER","stageName":"Offer","mode":"OFFER","rounds":0},{"stageNo":"HIRED","stageName":"入职","mode":"OFFER","rounds":0}]',
   68.00, 'IN_PROGRESS', 'PORTAL', 0),
  (51010, 50001, 'Java 开发工程师', 60010, '王十二', 'SCREEN', 0,
   '[{"stageNo":"SCREEN","stageName":"初筛","mode":"INTERNAL","rounds":0},{"stageNo":"FIRST","stageName":"一面","mode":"ONLINE","rounds":1},{"stageNo":"OFFER","stageName":"Offer","mode":"OFFER","rounds":0},{"stageNo":"HIRED","stageName":"入职","mode":"OFFER","rounds":0}]',
   73.00, 'WITHDRAWN', 'PORTAL', 0),
  (51011, 50002, '前端开发工程师', 60011, '冯十三', 'FIRST', 1,
   '[{"stageNo":"SCREEN","stageName":"初筛","mode":"INTERNAL","rounds":0},{"stageNo":"FIRST","stageName":"一面","mode":"ONLINE","rounds":1},{"stageNo":"OFFER","stageName":"Offer","mode":"OFFER","rounds":0},{"stageNo":"HIRED","stageName":"入职","mode":"OFFER","rounds":0}]',
   84.00, 'IN_PROGRESS', 'PORTAL', 0),
  (51012, 50003, 'HRBP', 60012, '陈十四', 'SCREEN', 0,
   '[{"stageNo":"SCREEN","stageName":"初筛","mode":"INTERNAL","rounds":0},{"stageNo":"FIRST","stageName":"一面","mode":"ONLINE","rounds":1},{"stageNo":"OFFER","stageName":"Offer","mode":"OFFER","rounds":0},{"stageNo":"HIRED","stageName":"入职","mode":"OFFER","rounds":0}]',
   78.00, 'IN_PROGRESS', 'IMPORT', 0);

INSERT IGNORE INTO interview
  (id, application_id, round_no, interviewer_ids, start_time, end_time, mode, room_type,
   room_id, status, note, version)
VALUES
  (90001, 51001, 1, '["10003","10004"]', DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 2 HOUR), DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 3 HOUR),
   'ONLINE', 'ONE_TO_ONE', 70001, 'SCHEDULED', '请提前 10 分钟进房', 0),
  (90002, 51003, 1, '["10003"]', '2026-10-21 01:00:00.000', '2026-10-21 02:00:00.000',
   'ONLINE', 'ONE_TO_ONE', 70002, 'COMPLETED', '已确认 Offer', 0),
  (90003, 51004, 1, '["10004","10005"]', '2026-10-22 02:00:00.000', '2026-10-22 03:00:00.000',
   'ONLINE', 'ONE_TO_MANY', 70003, 'COMPLETED', '已入职', 0),
  (90004, 51005, 1, '["10005"]', '2026-10-23 01:00:00.000', '2026-10-23 02:00:00.000',
   'ONLINE', 'ONE_TO_ONE', 70004, 'IN_PROGRESS', NULL, 0),
  (90005, 51007, 1, '["10003","10005"]', '2026-10-24 01:00:00.000', '2026-10-24 02:00:00.000',
   'ONLINE', 'ONE_TO_ONE', 70005, 'NO_SHOW', '候选人未到场', 0),
  (90006, 51011, 1, '["10004"]', '2026-10-26 01:00:00.000', '2026-10-26 02:00:00.000',
   'ONLINE', 'ONE_TO_ONE', NULL, 'ROOM_PENDING', NULL, 0);

INSERT IGNORE INTO offer
  (id, offer_no, application_id, position_title, salary_json, entry_date, expire_at,
   confirm_token_hash, status, onboarding_json, operator_id, confirmed_at, version)
VALUES
  (92001, 'OFFER-DEMO-20261001', 51003, 'Java 开发工程师',
   '{"base":"25k","bonus":"2个月"}', '2026-11-10', '2026-11-01 16:00:00.000',
   'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
   'SENT', '{"owner":"HR-李"}', 10001, NULL, 0),
  (92002, 'OFFER-DEMO-20261002', 51004, 'Java 开发工程师',
   '{"base":"28k","bonus":"3个月"}', '2026-11-01', '2026-10-25 16:00:00.000',
   'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
   'CONFIRMED', '{"owner":"HR-李"}', 10001, '2026-10-24 03:00:00.000', 1);
