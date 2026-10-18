# 08 · 需求追踪矩阵

> 用途：把三份题目需求逐条映射到「模块 → 数据表 → 接口 → 测试」，防止遗漏，也方便论文第 3 章写需求分析。  
> 图例：MVP = 必须；E = 增强/可选；O = 本期不做。

---

## 1. 面试管理子系统

| 原始需求 | 优先级 | 模块 | 主要表 | 主要接口 | 验收/测试 |
|---|---|---|---|---|---|
| 面试流程配置（初筛→笔试→一面→二面→HR 面→Offer） | MVP | 流程模板 | `itv_process_template`、`itv_process_stage` | `/api/interview/process-templates` | 新建模板、阶段顺序、默认时长、面试类型 |
| 面试安排（时间、面试官、会议室/线上链接） | MVP | 排期 | `itv_schedule`、`itv_schedule_interviewer` | `/api/interview/schedules` | 线上/线下、多个面试官、地点/房间 |
| 面试通知（短信/邮件自动推送） | MVP | 通知 | `msg_template`、`msg_message` | `notification.requested` 事件 | Mock 短信/邮件、模板变量、失败重试 |
| 面试官协同（评价表、打分、对比视图） | MVP | 评价 | `itv_evaluation_template`、`itv_evaluation` | `/evaluation-templates`、`/schedules/{id}/evaluations` | 多面试官评分、加权、对比、必填校验 |
| 面试状态流转（通过/待定/淘汰/转岗） | MVP | 状态机 | `itv_application`、`itv_application_stage`、`itv_application_log` | `POST /applications/{id}/actions` | PASS/HOLD/REJECT/TRANSFER、非法流转拒绝 |
| 候选人状态流水线 | MVP | 投递/流程 | `itv_application`、`itv_application_stage` | `/applications`、`/portal/applications` | 状态枚举、时间线、候选人视图 |
| 面试邀约（短信/邮件/站内信/微信） | MVP（微信 E） | 通知 | `msg_message` | 事件 + `/notify/messages` | 四通道适配器；微信可 Mock |
| 面试排期日历、冲突校验、批量安排 | MVP | 排期 | `itv_schedule`、`itv_schedule_interviewer` | `/calendar`、`/conflict-check`、`/batch` | 面试官/候选人/房间冲突；部分成功 |
| 面试评价表自定义维度 | MVP | 评价模板 | `itv_evaluation_template.dimensions_json` | `/evaluation-templates` | 权重、满分、否决项、模板快照 |
| 面试结果自动流转（一面→二面） | MVP | 状态机 | `itv_application_stage` | `/applications/{id}/actions` | 评价满足条件后自动建下一阶段 |
| 淘汰原因记录 | MVP | 状态机 | `itv_application.reject_reason*`、`itv_application_log` | `/actions` | 原因必填、可查询 |
| Offer 发放/审批/候选人确认 | MVP | Offer | `itv_offer`、`itv_approval_flow`、`itv_approval_task` | `/offers`、`/portal/offers/{id}/respond` | 审批链、过期、接受/拒绝 |
| 入职资料收集 | MVP（最小闭环） | Offer | `itv_offer.onboarding_json`、`file_asset` | `/portal/offers/{id}/onboarding` | 文件类型/归属校验、HR 审核 |
| AI 面试纪要（转写+摘要） | MVP | AI/房间 | `room_transcript_segment`、`room_note` | ASR WS、`/interview/summary` | 转写分段、摘要、人工可编辑 |
| 招聘统计（漏斗/工作量/周期） | E | 统计 | 聚合查询 | `/interview/stats/*` | 漏斗、面试官负载、time-to-hire |

---

## 2. 智能简历与人才管理子系统

| 原始需求 | 优先级 | 模块 | 主要表/索引 | 主要接口 | 验收/测试 |
|---|---|---|---|---|---|
| 多渠道简历聚合（招聘网站/邮箱/内推/猎头） | MVP（真实渠道 Mock） | 渠道适配 | `cand_ingest_cursor`、`cand_resume.source_channel` | 上传、邮箱轮询、适配器 | 上传/邮件/Mock 渠道；去重；来源可追踪 |
| 简历解析引擎：PDF/Word/图片 → 结构化 | MVP | 解析流水线 | `cand_parse_task`、`cand_resume` | `/resume/resumes`、`/parse-status` | 三种格式各 10 份；失败可重试 |
| 表格识别（Markdown 表格、保留行列） | MVP | MinerU/分块 | `cand_resume.markdown_file_id`、ES `resume_chunk` | 解析任务 | 表格块含表头/行列；检索可命中 |
| 公式、图片标题 | E | MinerU/分块 | Markdown 产物、ES 块 | 解析任务 | 公式 LaTeX、图注保留 |
| 推荐流水线 MinerU → Markdown → 分块 → BGE → ES8 | MVP | AI 流水线 | `cand_parse_task`、ES 别名 | 内部 AI API | 产物可复现；索引可重建 |
| 简历去重（同人多份合并） | MVP | 去重 | `cand_merge_record`、`cand_candidate.canonical_id` | `/candidates/{id}/merge` | 精确+相似；人工确认；活跃引用更新 |
| 简历查重（防刷单/猎头重复投递） | MVP | 去重 | `cand_merge_record`、ES `candidate_profile` | `/candidates/duplicates` | 重复投递告警；不误伤 |
| AI 实体抽取（学校/公司/技能/年限） | MVP | 抽取 | `cand_resume_education/experience/skill` | 解析任务 | 字段 F1 ≥ 0.85；人工校正 |
| 人岗匹配评分（简历 vs JD） | MVP | 匹配 | `cand_match_record` | `/resume/matches` | 维度分、证据、缺口、缓存 |
| AI 简历筛选（自动匹配、打分排序） | MVP | 筛选/匹配 | `cand_screening_result` | `/screening-rules/{id}/run` | 规则过滤 + AI 精排 + 排名 |
| 自动分类/标签（职能/级别/技能/来源） | MVP | 标签 | `cand_tag`、`cand_candidate_tag` | `/resume/tags` | 受控词表；置信度；人工覆盖 |
| 筛选规则引擎（学历/年限/地点/年龄） | MVP | 规则 | `cand_screening_rule` | `/screening-rules` | 白名单字段/操作符；解释命中 |
| 人才库沉淀（未入职/淘汰优质/被动） | MVP | 人才库 | `cand_talent_pool` | `/resume/talents` | 入库规则；状态；负责人 |
| 人才标签、检索、二次挖掘 | MVP | 人才库/ES | `cand_talent_pool`、ES | `/resume/search/talent`、`/talents/reactivate` | 按新岗位推荐；标签筛选 |
| 人才跟进记录、定时回访提醒 | MVP | 跟进 | `cand_talent_follow`、`next_follow_at` | `/talents/{id}/follow`、`/reminders` | XXL-Job 提醒；通知 |
| AI 候选人画像 | MVP | AI/人才 | `cand_talent_pool.ai_portrait` | `/candidates/{id}/portrait` | 简历+面试聚合；证据；人工可编辑 |
| 人才地图 | E | 统计 | 聚合查询/ES | `/resume/stats/talent-map`（可选） | 技能/城市/年限分布 |
| 多渠道真实对接 BOSS/智联/猎聘 | O/Mock | 渠道适配 | `cand_ingest_cursor` | 适配器 | 无公开 API，合规风险；演示 Mock |

---

## 3. 在线面试子系统

| 原始需求 | 优先级 | 模块 | 主要表/组件 | 主要接口 | 验收/测试 |
|---|---|---|---|---|---|
| 实时音视频通话（WebRTC） | MVP | LiveKit SFU | `room_room`、`room_participant` | `/rooms/{no}/token` | 1v1 入会、设备切换、重连 |
| 面试录制、回放、存档 | MVP | Egress/MinIO | `room_recording`、`file_asset` | `/recordings/start|stop`、`/play` | 录制成功 ≥95%；2 分钟内可回放 |
| 屏幕共享 | MVP | LiveKit | 房间配置/令牌 | 客户端 SDK | 面试官共享；候选人按配置 |
| 虚拟背景/美颜/降噪 | E | 客户端处理 | — | 客户端 | 可开关；不影响人脸核验原始轨 |
| 面试笔记（实时记录、AI 纪要） | MVP | 房间/AI | `room_note`、`room_transcript_segment` | `/notes`、ASR WS、`/summary` | 笔记保存；纪要生成；人工修改 |
| 防作弊（人脸核验、环境检测、录屏） | MVP（活体 E） | 防作弊 | `room_face_verify`、`room_cheat_event`、`room_cheat_report` | `/face-verify`、`/cheat-events`、`/cheat-report` | 入场核验；切屏/多人脸/缺失；风险分 |
| AI 面试助手（实时转写、关键问题提示） | MVP | AI/房间 | `room_transcript_segment`、`room_note` | ASR WS、`/interview/suggest` | partial ≤1.5s；建议仅面试官可见 |
| AI 面试评价（自动评价摘要） | MVP | AI/评价 | `itv_evaluation.ai_draft_json` | `/interview/summary`、`/ai-draft` | 证据引用；人工确认；不自动决策 |
| 实时视频 + 异步 AI 面试 | MVP | 房间/异步 | `async_interview*` | `/async-interviews` | 录播作答、上传、ASR、评分、复核 |
| 白板 | E | Yjs/Excalidraw | `room_whiteboard_snapshot` | 独立 WS | 协同；结束快照；可选 |
| 聊天消息 | MVP | 房间 WS | `room_chat_message` | `/messages`、`/ws/room` | 持久化、禁言、审计 |
| 面试房间管理（临时会议室/群面） | MVP | 房间 | `room_room`、`room_participant` | `/rooms/*` | 候场、准入、踢人、结束 |
| 1v1 / 1v5 并发 | MVP | SFU/容量 | — | — | 50 房间/200 人稳定；100 房间压测报告 |
| 面试回放权限与审计 | MVP | 文件/房间 | `room_recording`、日志 | `/recordings/{id}/play` | 仅授权人；短签名；下载审计 |

---

## 4. 跨子系统与架构要求

| 要求 | 落地 | 验收 |
|---|---|---|
| 后端微服务 | 8 个 Java 服务 + FastAPI AI | 每服务独立启动、独立 schema、网关路由 |
| 后端调用模型用 vLLM | Java → FastAPI → vLLM；Java 不直连模型 | 模型 Key 不落到业务服务；换模型不影响业务 |
| 统一登录鉴权 | auth + gateway + system RBAC | 同一账号登录、令牌统一、候选人/员工隔离 |
| 区分用户角色 | 6 个默认角色 + 自定义角色 | 角色矩阵、数据范围 |
| 管理员分配权限 | 用户分配角色；角色勾选菜单/按钮 | 取消菜单后前端路由与后端接口均拒绝 |
| 勾选角色菜单 | `sys_role_menu` + 树形 UI | 父目录自动补齐、版本递增、缓存生效 |
| MySQL | 8 个 schema、Flyway | 迁移可重复执行、无跨库 JOIN |
| ES 向量库 | 混合检索 + RRF + 别名 | Recall@10、重建索引、权限回查 |
| 数据表协调一致 | 数据所有权、雪花 ID、快照、枚举字典 | 每周集成；无跨服务直连数据库 |
| 1v1 / 1v5 性能 | Simulcast/Dynacast/AdaptiveStream + 压测 | 带宽/CPU/延迟/入会成功率报告 |
| 安全与隐私 | 权限码、对象校验、HMAC、短令牌、知情同意 | 越权/令牌/上传/注入测试通过 |

---

## 5. 测试用例归属（摘要）

| 测试域 | 负责人 | 关键用例 |
|---|---|---|
| 鉴权/RBAC/菜单 | A | 登录、刷新、禁用、角色勾选、候选人隔离、越权 |
| 面试流程/排期/评价/Offer | A | 冲突、状态机、评价聚合、审批、过期 |
| 简历解析/去重/检索/匹配/人才库 | B | 三格式解析、表格块、重复合并、规则、匹配排序 |
| 在线面试/录制/防作弊/异步 | C | 1v1、1v5、弱网、录制回放、人脸/切屏、异步评分 |
| AI 质量/性能/安全 | B + C | 抽取 F1、检索 NDCG、ASR CER、注入、降级 |
| 端到端 | 全员 | 投递→筛选→排期→面试→评价→Offer→人才库 |

> 论文中可用本矩阵做「需求覆盖表」：每行对应一个需求、一个实现位置、一个测试结果，答辩时非常直观。
