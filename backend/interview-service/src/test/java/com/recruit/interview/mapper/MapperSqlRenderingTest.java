package com.recruit.interview.mapper;

import com.recruit.interview.entity.Job;
import com.recruit.interview.mapper.AsyncPaperMapper;
import com.recruit.interview.mapper.InterviewMapper;
import com.recruit.interview.mapper.InterviewMinutesMapper;
import com.recruit.interview.support.ScheduleParticipant;
import com.recruit.interview.support.ScheduleSlotKey;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mapper 注解 SQL 的渲染测试。
 *
 * <p>本机没有可用 MySQL，无法跑真实 SQL；这里用 MyBatis 自带的 {@link XMLLanguageDriver}
 * 解析 {@code <script>} 并代入参数，至少保证动态标签、OGNL 与占位符能正确展开，
 * 避免上线后才发现 SQL 语法/参数名错误。</p>
 */
class MapperSqlRenderingTest {

    private static final Configuration CONFIGURATION = new Configuration();
    private static final XMLLanguageDriver DRIVER = new XMLLanguageDriver();

    @Test
    void jobPageRendersDepartmentSubtreeCondition() throws Exception {
        String sql = render(JobMapper.class, "selectJobPage", Map.class,
                params("keyword", null, "status", "PUBLISHED", "deptId", null,
                        "scope", "DEPARTMENT", "deptIds", List.of(20001L, 20002L),
                        "userId", 10001L, "limit", 20, "offset", 0L));

        assertThat(sql).contains("FROM job j").contains("j.status = ?")
                .contains("j.dept_id IN").doesNotContain("j.owner_hr_id = ?");
    }

    @Test
    void jobPageRendersSelfCondition() throws Exception {
        String sql = render(JobMapper.class, "countJobPage", Map.class,
                params("keyword", "Java", "status", null, "deptId", 20001L,
                        "scope", "SELF", "deptIds", List.of(), "userId", 10001L));

        assertThat(sql).contains("j.owner_hr_id = ?").contains("j.job_code LIKE")
                .contains("j.dept_id = ?");
    }

    @Test
    void applicationPageRendersJoinAndScope() throws Exception {
        String sql = render(ApplicationMapper.class, "selectApplicationPage", Map.class,
                params("keyword", "张", "status", "IN_PROGRESS", "jobId", 50001L, "personId", null,
                        "scope", "DEPARTMENT", "deptIds", List.of(20001L), "userId", 10001L,
                        "limit", 20, "offset", 0L));

        assertThat(sql).contains("JOIN job j ON j.id = a.job_id")
                .contains("a.person_name LIKE").contains("j.dept_id IN");
    }

    @Test
    void templatePageRendersScopeCondition() throws Exception {
        String sql = render(JobStageTemplateMapper.class, "selectTemplatePage", Map.class,
                params("jobId", 50001L, "status", "ACTIVE", "scope", "DEPARTMENT",
                        "deptIds", List.of(20001L), "userId", 10001L,
                        "limit", 20, "offset", 0L));

        assertThat(sql).contains("FROM job_stage_template t JOIN job j")
                .contains("t.status = ?").contains("j.dept_id IN");
    }

    @Test
    void updateStatusOmitsPublishedAtWhenNull() throws Exception {
        String without = render(JobMapper.class, "updateStatus", Map.class,
                params("id", 50001L, "status", "CLOSED", "publishedAt", null, "version", 3));
        assertThat(without).doesNotContain("published_at").contains("version = version + 1");

        String with = render(JobMapper.class, "updateStatus", Map.class,
                params("id", 50001L, "status", "PUBLISHED",
                        "publishedAt", LocalDateTime.now(), "version", 3));
        assertThat(with).contains("published_at = ?");
    }

    @Test
    void updateWithVersionUsesJobObjectParameters() throws Exception {
        Job job = new Job();
        job.setId(50001L);
        job.setTitle("Java");
        job.setVersion(3);

        String sql = render(JobMapper.class, "updateWithVersion", Job.class, job);

        assertThat(sql).contains("title = ?").contains("WHERE id = ? AND version = ?")
                .contains("version = version + 1");
    }

    @Test
    void schedulePageRendersDepartmentScopeAndInterviewerFilter() throws Exception {
        String sql = render(InterviewMapper.class, "selectSchedulePage", Map.class,
                params("status", "ROOM_PENDING", "startFrom", LocalDateTime.now(),
                        "startTo", LocalDateTime.now().plusDays(1), "interviewerId", "10002",
                        "deptId", null, "scope", "DEPARTMENT", "deptIds", List.of(20001L),
                        "userId", 10001L, "userIdText", "10001", "limit", 20, "offset", 0L));

        assertThat(sql).contains("JOIN application a ON a.id = i.application_id")
                .contains("JSON_CONTAINS").contains("j.dept_id IN")
                .doesNotContain("j.owner_hr_id = ?");
    }

    @Test
    void calendarRendersSelfConditionUsingCurrentUser() throws Exception {
        String sql = render(InterviewMapper.class, "selectCalendar", Map.class,
                params("startFrom", LocalDateTime.now(), "startTo", LocalDateTime.now().plusDays(1),
                        "interviewerId", null, "deptId", 20001L,
                        "scope", "SELF", "deptIds", List.of(), "userId", 10001L,
                        "userIdText", "10001"));

        assertThat(sql).contains("i.start_time >=").contains("j.owner_hr_id = ?")
                .contains("JSON_QUOTE(?)").contains("j.dept_id = ?");
    }

    @Test
    void slotConflictRendersRecordPropertiesAndExcludes() throws Exception {
        String sql = render(ScheduleSlotMapper.class, "selectConflicts", Map.class,
                params("keys", List.of(new ScheduleSlotKey(LocalDate.of(2026, 10, 20), 18),
                                new ScheduleSlotKey(LocalDate.of(2026, 10, 20), 19)),
                        "participants", List.of(new ScheduleParticipant("INTERVIEWER", 10002L),
                                new ScheduleParticipant("CANDIDATE", 60001L)),
                        "excludeInterviewIds", List.of(90001L)));

        assertThat(sql).contains("s.slot_date = ? AND s.slot_no = ?")
                .contains("s.participant_type = ? AND s.participant_id = ?")
                .contains("s.interview_id NOT IN").doesNotContain("${");
    }

    @Test
    void myEvaluationTasksRenderAssigneeAndWindow() throws Exception {
        String sql = render(EvaluationMapper.class, "selectMyTasks", Map.class,
                params("interviewerId", 10002L, "userIdText", "10002",
                        "from", LocalDateTime.of(2026, 10, 1, 0, 0),
                        "to", LocalDateTime.of(2026, 11, 1, 0, 0)));

        assertThat(sql).contains("JSON_CONTAINS(i.interviewer_ids, JSON_QUOTE(?))")
                .contains("i.start_time >= ?").contains("i.start_time < ?")
                .contains("LEFT JOIN evaluation e").contains("e.interviewer_id = ?");
    }

    @Test
    void evaluationReopenWritesAuditColumnsWithVersionGuard() throws Exception {
        String sql = render(EvaluationMapper.class, "reopen", Map.class,
                params("id", 91001L, "reopenedBy", 10001L, "reason", "材料有误", "version", 2));

        assertThat(sql).contains("reopened_by = ?").contains("reopen_reason = ?")
                .contains("version = version + 1")
                .contains("WHERE id = ? AND version = ? AND status = 'SUBMITTED'");
    }

    @Test
    void offerPageRendersScopeAndKeyword() throws Exception {
        String sql = render(OfferMapper.class, "selectOfferPage", Map.class,
                params("keyword", "张", "status", "SENT", "jobId", 50001L,
                        "scope", "DEPARTMENT", "deptIds", List.of(20001L),
                        "userId", 10001L, "limit", 20, "offset", 0L));

        assertThat(sql).contains("JOIN application a ON a.id = o.application_id")
                .contains("o.offer_no LIKE").contains("j.dept_id IN")
                .doesNotContain("j.owner_hr_id = ?");
    }

    @Test
    void offerExpireScanUsesStatusAndTimeGuard() throws Exception {
        String sql = render(OfferMapper.class, "markExpired", Map.class,
                params("id", 92001L, "version", 2));

        assertThat(sql).contains("status = 'EXPIRED'").contains("version = version + 1")
                .contains("status = 'SENT' AND expire_at < CURRENT_TIMESTAMP(3)");
    }

    @Test
    void asyncPaperPageRendersScopeAndKeyword() throws Exception {
        String sql = render(AsyncPaperMapper.class, "selectPaperPage", Map.class,
                params("keyword", "张", "status", "RELEASED", "jobId", 50001L,
                        "applicationId", 51001L, "scope", "DEPARTMENT",
                        "deptIds", List.of(20001L), "userId", 10001L,
                        "limit", 20, "offset", 0L));

        assertThat(sql).contains("JOIN application a ON a.id = p.application_id")
                .contains("p.paper_no LIKE").contains("j.dept_id IN")
                .doesNotContain("j.owner_hr_id = ?");
    }

    @Test
    void asyncPaperUpdateDraftOmitsQuestionCountWhenNull() throws Exception {
        String without = render(AsyncPaperMapper.class, "updateDraft", Map.class,
                params("id", 93001L, "durationLimitSec", 1800, "settingsJson", null,
                        "questionCount", null, "version", 0));
        assertThat(without).doesNotContain("question_count")
                .contains("status = 'DRAFT'").contains("version = version + 1");

        String with = render(AsyncPaperMapper.class, "updateDraft", Map.class,
                params("id", 93001L, "durationLimitSec", 1800, "settingsJson", "{}",
                        "questionCount", 3, "version", 0));
        assertThat(with).contains("question_count = ?");
    }

    @Test
    void asyncPaperMarkScoredOmitsReviewerWhenNull() throws Exception {
        String without = render(AsyncPaperMapper.class, "markScored", Map.class,
                params("id", 93001L, "totalScore", new java.math.BigDecimal("85.00"),
                        "reviewedBy", null, "reviewedAt", null, "version", 2));
        assertThat(without).contains("status = 'SCORED'").doesNotContain("reviewed_by");

        String with = render(AsyncPaperMapper.class, "markScored", Map.class,
                params("id", 93001L, "totalScore", new java.math.BigDecimal("85.00"),
                        "reviewedBy", 10001L, "reviewedAt", LocalDateTime.now(), "version", 2));
        assertThat(with).contains("reviewed_by = ?").contains("reviewed_at = ?");
    }

    @Test
    void roomCreatedOnlyTouchesRoomPending() throws Exception {
        String sql = render(InterviewMapper.class, "markRoomCreated", Map.class,
                params("id", 91001L, "roomId", 70001L));

        assertThat(sql).contains("room_id = ?").contains("status = 'SCHEDULED'")
                .contains("WHERE id = ? AND status = 'ROOM_PENDING'");
    }

    @Test
    void roomFinishedDoesNotRevertTerminalStatus() throws Exception {
        String sql = render(InterviewMapper.class, "markCompleted", Map.class, params("id", 91001L));

        assertThat(sql).contains("status = 'COMPLETED'")
                .contains("status IN ('ROOM_PENDING','SCHEDULED','IN_PROGRESS')");
    }

    @Test
    void minutesReviewUsesVersionAndOpenStatusGuard() throws Exception {
        String sql = render(InterviewMinutesMapper.class, "review", Map.class,
                params("id", 95001L, "reviewStatus", "CONFIRMED", "comment", "ok",
                        "reviewerId", 10001L, "reviewedAt", LocalDateTime.now(), "version", 2));

        assertThat(sql).contains("review_status = ?").contains("review_comment = ?")
                .contains("version = version + 1")
                .contains("review_status IN ('PENDING','INSUFFICIENT_TRANSCRIPT')");
    }

    @Test
    void minutesAiRewriteKeepsConfirmedRowsUntouched() throws Exception {
        String sql = render(InterviewMinutesMapper.class, "updateFromAi", Map.class,
                params("id", 95001L, "aiTaskId", "task-1", "transcriptObjectKey", "k",
                        "transcriptSegmentCount", 12, "minutesMd", "# md",
                        "aiEvaluation", "{}", "reviewStatus", "PENDING",
                        "model", "mock-llm", "promptVersion", 1, "version", 0));

        assertThat(sql).contains("ai_task_id = ?").contains("transcript_segment_count = ?")
                .contains("version = version + 1")
                .contains("review_status IN ('PENDING','INSUFFICIENT_TRANSCRIPT')");
    }

    private static String render(Class<?> mapper, String methodName, Class<?> parameterType,
                                 Object parameters) throws Exception {
        Method method = findMethod(mapper, methodName);
        Select select = method.getAnnotation(Select.class);
        Update update = method.getAnnotation(Update.class);
        String script;
        if (select != null) {
            script = String.join(" ", select.value());
        } else if (update != null) {
            script = String.join(" ", update.value());
        } else {
            throw new IllegalStateException("方法没有 @Select/@Update: " + methodName);
        }
        SqlSource source = DRIVER.createSqlSource(CONFIGURATION, script, parameterType);
        BoundSql boundSql = source.getBoundSql(parameters);
        return boundSql.getSql().replaceAll("\\s+", " ").trim();
    }

    private static Method findMethod(Class<?> mapper, String name) {
        for (Method method : mapper.getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        throw new IllegalStateException("找不到 Mapper 方法: " + name);
    }

    private static Map<String, Object> params(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
