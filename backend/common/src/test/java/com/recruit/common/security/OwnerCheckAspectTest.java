package com.recruit.common.security;

import com.recruit.common.context.UserContext;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link OwnerCheckAspect} 的测试：覆盖本人放行、他人 403、空对象 404、管理员放行、
 * Result 包装解包与列表误用。
 */
class OwnerCheckAspectTest {

    @AfterEach
    void cleanup() {
        UserContext.clear();
    }

    /**
     * 归属人等于当前用户：正常返回。
     */
    @Test
    void allowsSameOwner() {
        UserContext.set("10001", List.of("RECRUITER"));
        Target target = new Target(new ApplicationView("10001"));

        assertSame(target.view, proxy(target).get());
    }

    /**
     * 归属人是别人：抛 AUTH1101（403）。
     */
    @Test
    void rejectsDifferentOwner() {
        UserContext.set("10001", List.of("RECRUITER"));
        Target proxy = proxy(new Target(new ApplicationView("20002")));

        BizException ex = assertThrows(BizException.class, proxy::get);
        assertEquals(ErrorCode.AUTH_FORBIDDEN, ex.getErrorCode());
    }

    /**
     * 方法返回 null：抛 SYS404（404）。
     */
    @Test
    void returnsNotFoundWhenObjectMissing() {
        UserContext.set("10001", List.of("RECRUITER"));
        Target proxy = proxy(new Target(null));

        BizException ex = assertThrows(BizException.class, proxy::get);
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
    }

    /**
     * 命中 bypassRoles（默认 SUPER_ADMIN）时放行。
     */
    @Test
    void allowsBypassRole() {
        UserContext.set("90001", List.of("SUPER_ADMIN"));
        Target target = new Target(new ApplicationView("20002"));

        assertSame(target.view, proxy(target).get());
    }

    /**
     * 返回 Result 时先解包 data 再校验，成功响应仍按归属判断。
     */
    @Test
    void unwrapsResultBeforeChecking() {
        UserContext.set("10001", List.of("RECRUITER"));
        ResultTarget target = new ResultTarget(new ApplicationView("10001"));

        Result<?> result = resultProxy(target).get();
        assertEquals("0", result.getCode());
    }

    /**
     * 列表返回值是注解误用，必须尽早报配置错误，而不是逐条判断。
     */
    @Test
    void rejectsCollectionReturnType() {
        UserContext.set("10001", List.of("RECRUITER"));
        ListTarget proxy = listProxy(new ListTarget());

        IllegalStateException ex = assertThrows(IllegalStateException.class, proxy::list);
        assertTrue(ex.getMessage().contains("@OwnerCheck"));
    }

    private Target proxy(Target target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new OwnerCheckAspect());
        return factory.getProxy();
    }

    private ResultTarget resultProxy(ResultTarget target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new OwnerCheckAspect());
        return factory.getProxy();
    }

    private ListTarget listProxy(ListTarget target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new OwnerCheckAspect());
        return factory.getProxy();
    }

    /** 普通 JavaBean，确保 BeanWrapper 能按 getter 读取属性。 */
    public static class ApplicationView {

        private final String ownerHrId;

        public ApplicationView(String ownerHrId) {
            this.ownerHrId = ownerHrId;
        }

        public String getOwnerHrId() {
            return ownerHrId;
        }
    }

    static class Target {

        private final ApplicationView view;

        Target(ApplicationView view) {
            this.view = view;
        }

        @OwnerCheck(resource = "application", ownerProperty = "ownerHrId")
        public ApplicationView get() {
            return view;
        }
    }

    static class ResultTarget {

        private final ApplicationView view;

        ResultTarget(ApplicationView view) {
            this.view = view;
        }

        @OwnerCheck(resource = "application", ownerProperty = "ownerHrId")
        public Result<ApplicationView> get() {
            return Result.ok(view);
        }
    }

    static class ListTarget {

        @OwnerCheck(resource = "application", ownerProperty = "ownerHrId")
        public List<ApplicationView> list() {
            return List.of();
        }
    }
}
