package com.recruit.notify.mapper;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * notify Mapper 注解 SQL 渲染测试（本机无 MySQL，至少保证动态标签能展开）。
 */
class MapperSqlRenderingTest {

    private static final Configuration CONFIGURATION = new Configuration();
    private static final XMLLanguageDriver DRIVER = new XMLLanguageDriver();

    @Test
    void messagePageRendersReadAndChannelFilters() throws Exception {
        String sql = render(NotificationMapper.class, "selectMessagePage", Map.class,
                params("receiverId", 60001L, "read", Boolean.FALSE, "status", "SENT",
                        "channel", "INAPP", "limit", 20, "offset", 0L));

        assertThat(sql).contains("receiver_id = ?").contains("status <> 'READ'")
                .contains("status = ?").contains("channel = ?")
                .contains("ORDER BY created_at DESC");
    }

    @Test
    void templatePageRendersOptionalFilters() throws Exception {
        String sql = render(NotificationTemplateMapper.class, "countTemplatePage", Map.class,
                params("templateCode", "offer_send", "channel", null, "status", "ACTIVE"));

        assertThat(sql).contains("template_code = ?").contains("status = ?")
                .doesNotContain("channel = ?");
    }

    @Test
    void retryCandidatesHaveStatusAndTimeGuard() throws Exception {
        String sql = render(NotificationMapper.class, "selectRetryCandidates", Map.class,
                params("limit", 50));

        assertThat(sql).contains("status = 'FAILED'").contains("retry_count < 3")
                .contains("next_retry_at <= CURRENT_TIMESTAMP(3)");
    }

    @Test
    void markReadIsScopedToReceiver() throws Exception {
        String sql = render(NotificationMapper.class, "markRead", Map.class,
                params("id", 9001L, "receiverId", 60001L));

        assertThat(sql).contains("receiver_id = ?").contains("status = 'SENT'")
                .contains("status = 'READ'");
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
