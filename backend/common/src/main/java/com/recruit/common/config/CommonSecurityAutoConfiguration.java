package com.recruit.common.config;

import com.recruit.common.security.DataScopeAspect;
import com.recruit.common.security.DataScopeResolver;
import com.recruit.common.security.OwnerCheckAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 数据范围与归属校验的自动配置。
 *
 * <p>只有类路径上有 AspectJ 时才生效；{@link DataScopeAspect} 还需要业务服务提供
 * {@link DataScopeResolver}。没有解析器时不会注册数据范围切面，避免用错误默认值放行。</p>
 *
 * <p>OwnerCheckAspect 不依赖解析器，只要 AOP 可用就注册，供单对象接口使用。</p>
 */
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.aop.AopAutoConfiguration")
@ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
public class CommonSecurityAutoConfiguration {

    /**
     * 注册归属校验切面。
     *
     * @return 归属校验切面
     */
    @Bean
    @ConditionalOnMissingBean
    public OwnerCheckAspect ownerCheckAspect() {
        return new OwnerCheckAspect();
    }

    /**
     * 注册数据范围切面。业务服务实现 {@link DataScopeResolver} 后才会创建。
     *
     * @param resolver 数据范围解析器
     * @return 数据范围切面
     */
    @Bean
    @ConditionalOnBean(DataScopeResolver.class)
    @ConditionalOnMissingBean
    public DataScopeAspect dataScopeAspect(DataScopeResolver resolver) {
        return new DataScopeAspect(resolver);
    }
}
