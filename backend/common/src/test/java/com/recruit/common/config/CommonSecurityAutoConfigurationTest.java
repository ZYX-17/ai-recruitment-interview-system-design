package com.recruit.common.config;

import com.recruit.common.security.DataScopeAspect;
import com.recruit.common.security.DataScopeDecision;
import com.recruit.common.security.DataScopeResolver;
import com.recruit.common.security.DataScopeType;
import com.recruit.common.security.OwnerCheckAspect;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CommonSecurityAutoConfiguration} 的条件装配测试。
 *
 * <p>验证：归属切面默认注册；数据范围切面必须等业务服务提供解析器后才注册，
 * 避免用错误的默认范围放行。</p>
 */
class CommonSecurityAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommonSecurityAutoConfiguration.class));

    /**
     * OwnerCheckAspect 不依赖解析器，AOP 可用时应直接注册。
     */
    @Test
    void registersOwnerCheckAspect() {
        runner.run(context -> assertThat(context).hasSingleBean(OwnerCheckAspect.class));
    }

    /**
     * 没有 DataScopeResolver 时不注册数据范围切面，防止 @DataScope 静默失效。
     */
    @Test
    void skipsDataScopeAspectWithoutResolver() {
        runner.run(context -> assertThat(context).doesNotHaveBean(DataScopeAspect.class));
    }

    /**
     * 类路径上没有 AspectJ 时（未引入 starter-aop 的服务），所有切面都不注册。
     */
    @Test
    void skipsWhenAspectJMissing() {
        runner.withClassLoader(new FilteredClassLoader("org.aspectj.lang.annotation.Aspect"))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OwnerCheckAspect.class);
                    assertThat(context).doesNotHaveBean(DataScopeAspect.class);
                });
    }

    /**
     * 提供解析器后注册数据范围切面。
     */
    @Test
    void registersDataScopeAspectWhenResolverPresent() {
        DataScopeResolver resolver = (resource, userId) ->
                DataScopeDecision.of(DataScopeType.ALL, userId);

        runner.withBean(DataScopeResolver.class, () -> resolver)
                .run(context -> assertThat(context).hasSingleBean(DataScopeAspect.class));
    }
}
