package com.recruit.common.security;

import com.recruit.common.context.UserContext;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link DataScopeAspect} 的测试：用 AspectJProxyFactory 代理一个带注解的目标对象，
 * 验证上下文写入、SQL 片段生成、异常清理和用户一致性校验。
 */
class DataScopeAspectTest {

    @AfterEach
    void cleanup() {
        UserContext.clear();
        DataScopeContext.clear();
    }

    /**
     * 部门范围：方法内部能读到 dept_id IN (?, ?)，方法结束后上下文被清理。
     */
    @Test
    void setsDepartmentContextAndClearsAfterward() {
        UserContext.set("10001", List.of("RECRUITER"));
        DataScopeResolver resolver = (resource, userId) ->
                DataScopeDecision.department(userId, List.of("10", "11"));

        Target target = new Target();
        Target proxy = proxy(target, resolver);

        assertEquals("ok", proxy.query());
        assertNotNull(target.captured);
        assertEquals("dept_id IN (?, ?)", target.captured.sql());
        assertEquals(List.of("10", "11"), target.captured.params());
        assertFalse(DataScopeContext.isActive());
    }

    /**
     * 解析器返回的 userId 与当前用户不一致时，拒绝执行（防止实现错误导致越权）。
     */
    @Test
    void rejectsResolverReturningAnotherUser() {
        UserContext.set("10001", List.of("RECRUITER"));
        DataScopeResolver resolver = (resource, userId) ->
                DataScopeDecision.of(DataScopeType.ALL, "99999");

        Target proxy = proxy(new Target(), resolver);

        BizException ex = assertThrows(BizException.class, proxy::query);
        assertEquals(ErrorCode.AUTH_FORBIDDEN, ex.getErrorCode());
        assertFalse(DataScopeContext.isActive());
    }

    /**
     * 未认证时直接 401，不能以匿名身份执行带数据范围的查询。
     */
    @Test
    void requiresAuthentication() {
        DataScopeResolver resolver = (resource, userId) ->
                DataScopeDecision.of(DataScopeType.ALL, userId);

        Target proxy = proxy(new Target(), resolver);

        BizException ex = assertThrows(BizException.class, proxy::query);
        assertEquals(ErrorCode.GW_UNAUTHENTICATED, ex.getErrorCode());
    }

    /**
     * 创建代理。
     *
     * @param target   目标对象
     * @param resolver 解析器
     * @return 代理对象
     */
    private Target proxy(Target target, DataScopeResolver resolver) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new DataScopeAspect(resolver));
        return factory.getProxy();
    }

    /** 测试目标：记录方法执行时上下文里的 SQL 片段。 */
    static class Target {

        private DataScopeSql captured;

        @DataScope(resource = "application", deptColumn = "dept_id", ownerColumn = "owner_hr_id")
        public String query() {
            captured = DataScopeContext.sql();
            return "ok";
        }
    }
}
