package com.recruit.common.security;

import com.recruit.common.context.UserContext;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.Collection;

/**
 * {@link OwnerCheck} 切面：方法返回单个对象后，校验对象归属人是否是当前用户。
 *
 * <p>处理顺序：</p>
 * <ol>
 *   <li>先执行原方法（查库）；</li>
 *   <li>返回 null → 404；</li>
 *   <li>返回集合/数组 → 配置错误（列表应使用 {@link DataScope}）；</li>
 *   <li>读取 owner 属性，与当前用户比较；命中放行角色也放行；</li>
 *   <li>其余情况 → 403，且不区分“对象不存在”与“无权限”之外的信息。</li>
 * </ol>
 *
 * <p>本切面不查库、不缓存，避免重复查询和缓存不一致。</p>
 */
@Slf4j
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
public class OwnerCheckAspect {

    private static final String UNREADABLE_TEMPLATE =
            "@OwnerCheck.ownerProperty 不存在或不可读: %s (返回值类型 %s)";

    /**
     * 拦截标注了 {@link OwnerCheck} 的方法。
     *
     * @param joinPoint  连接点
     * @param ownerCheck 注解配置
     * @return 原方法返回值
     * @throws Throwable 原方法异常
     */
    @Around("@annotation(ownerCheck)")
    public Object around(ProceedingJoinPoint joinPoint, OwnerCheck ownerCheck) throws Throwable {
        Object result = joinPoint.proceed();

        // 兼容直接返回 Result 的写法：失败响应原样返回，成功则取 data 继续校验。
        Object target = result;
        if (result instanceof Result<?> wrapper) {
            if (!wrapper.isSuccess()) {
                return result;
            }
            target = wrapper.getData();
        }

        if (target == null) {
            throw notFound(ownerCheck);
        }
        if (target instanceof Collection<?> || target.getClass().isArray()) {
            throw new IllegalStateException("@OwnerCheck 不支持列表返回值，请改用 @DataScope: "
                    + joinPoint.getSignature());
        }

        String ownerId = readOwner(target, ownerCheck.ownerProperty(), joinPoint);
        String userId = UserContext.requireUserId();

        boolean sameOwner = ownerId != null && !ownerId.isBlank() && ownerId.equals(userId);
        boolean bypass = UserContext.hasAnyRole(ownerCheck.bypassRoles());

        if (sameOwner || bypass) {
            return result;
        }

        log.debug("归属校验拒绝: resource={}, owner={}, current={}, signature={}",
                ownerCheck.resource(), ownerId, userId, joinPoint.getSignature());
        throw forbidden(ownerCheck);
    }

    /**
     * 读取归属人属性，支持嵌套路径。
     *
     * @param target    返回值对象
     * @param property  属性名
     * @param joinPoint 连接点，用于报错
     * @return 归属人字符串；属性值为 null 时返回 null
     */
    private String readOwner(Object target, String property, ProceedingJoinPoint joinPoint) {
        BeanWrapper wrapper = new BeanWrapperImpl(target);
        if (property == null || property.isBlank() || !wrapper.isReadableProperty(property)) {
            throw new IllegalStateException(String.format(UNREADABLE_TEMPLATE, property,
                    target.getClass().getName()) + " @" + joinPoint.getSignature());
        }
        Object value = wrapper.getPropertyValue(property);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 构造 404 异常。
     *
     * @param ownerCheck 注解配置
     * @return 业务异常
     */
    private BizException notFound(OwnerCheck ownerCheck) {
        ErrorCode errorCode = ErrorCode.fromCode(ownerCheck.notFoundCode());
        return new BizException(errorCode, prefix(ownerCheck) + errorCode.getDefaultMessage());
    }

    /**
     * 构造 403 异常。
     *
     * @param ownerCheck 注解配置
     * @return 业务异常
     */
    private BizException forbidden(OwnerCheck ownerCheck) {
        ErrorCode errorCode = ErrorCode.fromCode(ownerCheck.forbiddenCode());
        return new BizException(errorCode, prefix(ownerCheck) + errorCode.getDefaultMessage());
    }

    /**
     * 错误文案前缀，带上资源名便于定位。
     *
     * @param ownerCheck 注解配置
     * @return 前缀，可能为空
     */
    private String prefix(OwnerCheck ownerCheck) {
        return ownerCheck.resource() == null || ownerCheck.resource().isBlank()
                ? ""
                : ownerCheck.resource() + " ";
    }
}
