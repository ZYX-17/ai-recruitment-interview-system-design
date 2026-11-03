package com.recruit.interview.mapper;

import com.recruit.interview.entity.Job;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
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
                .contains("j.dept_id IN").doesNotContain("j.owner_hr_id");
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
