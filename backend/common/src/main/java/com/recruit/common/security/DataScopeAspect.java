package com.recruit.common.security;

import com.recruit.common.context.UserContext;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * {@link DataScope} 切面：解析当前用户的数据范围，并把过滤片段放进 {@link DataScopeContext}。
 *
 * <p>职责边界：切面只负责“算条件 + 放上下文”，不直接改 SQL。
 * 真正的 SQL 改写由 MyBatis 拦截器读取 {@link DataScopeContext#sql()} 后完成，
 * 这样切面不依赖 MyBatis，也可以被非 MyBatis 的查询复用。</p>
 *
 * <p>执行顺序：尽量靠前，保证进入业务方法时上下文已就绪；方法结束（含异常）必须清理。</p>
 */
@Slf4j
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class DataScopeAspect {

    private final DataScopeResolver resolver;

    /**
     * @param resolver 数据范围解析器，由业务服务提供
     */
    public DataScopeAspect(DataScopeResolver resolver) {
        if (resolver == null) {
            throw new IllegalArgumentException("DataScopeResolver 不能为空");
        }
        this.resolver = resolver;
    }

    /**
     * 拦截标注了 {@link DataScope} 的方法。
     *
     * @param joinPoint 连接点
     * @param dataScope 注解配置
     * @return 原方法返回值
     * @throws Throwable 原方法异常
     */
    @Around("@annotation(dataScope)")
    public Object around(ProceedingJoinPoint joinPoint, DataScope dataScope) throws Throwable {
        String resource = dataScope.resource();
        if (resource == null || resource.isBlank()) {
            throw new IllegalStateException("@DataScope.resource 不能为空: " + joinPoint.getSignature());
        }

        // 未登录直接 401；数据范围必须以网关注入的用户为准，不能从参数取。
        String userId = UserContext.requireUserId();

        DataScopeDecision decision = resolver.resolve(resource, userId);
        if (decision == null || decision.type() == null) {
            // fail-closed：解析失败绝不能退化成“查全部”。
            throw new IllegalStateException("数据范围解析失败: resource=" + resource + ", userId=" + userId);
        }
        if (decision.userId() != null && !decision.userId().equals(userId)) {
            // 防止解析器实现错误（例如误用请求里的 userId）导致查到别人的数据。
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "数据范围与当前用户不一致");
        }

        DataScopeSql sql = DataScopeSqlBuilder.build(
                decision,
                dataScope.deptColumn(),
                dataScope.ownerColumn(),
                dataScope.assignedColumn());

        if (log.isDebugEnabled()) {
            log.debug("数据范围: resource={}, type={}, sql={}, params={}",
                    resource, decision.type(), sql.sql(), sql.params());
        }

        DataScopeContext.set(decision, sql);
        try {
            return joinPoint.proceed();
        } finally {
            DataScopeContext.clear();
        }
    }
}
