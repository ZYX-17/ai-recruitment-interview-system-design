package com.recruit.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据范围注解：标注在 Service/Mapper 的查询方法上，声明“这次查询要按什么列过滤”。
 *
 * <p>典型用法：</p>
 * <pre>
 * &#64;DataScope(resource = "application", deptColumn = "dept_id", ownerColumn = "owner_hr_id")
 * public PageResult&lt;ApplicationVO&gt; page(ApplicationQuery query) { ... }
 * </pre>
 *
 * <p>注解本身不拼 SQL，真正的过滤由切面 + MyBatis 拦截器完成：
 * 切面解析当前用户的数据范围并写入 {@link DataScopeContext}，
 * 拦截器读取上下文，把 {@link DataScopeSqlBuilder} 生成的条件追加到 SQL。</p>
 *
 * <p><b>安全要求：</b>列名只能来自代码常量，不能来自请求参数；切面会校验列名格式，
 * 防止把用户输入拼进 SQL。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /**
     * 资源名，用于区分不同业务对象的数据范围，例如 application / job / interview。
     *
     * <p>解析器可以按资源返回不同的范围；不能为空。</p>
     *
     * @return 资源名
     */
    String resource();

    /**
     * 部门列名，默认 dept_id。DEPARTMENT 范围使用。
     *
     * @return 部门列名
     */
    String deptColumn() default "dept_id";

    /**
     * 负责人列名，默认 owner_id。SELF 范围使用，ASSIGNED 未配置 assignedColumn 时也用它。
     *
     * @return 负责人列名
     */
    String ownerColumn() default "owner_id";

    /**
     * “与我相关”列名，默认空。ASSIGNED 范围使用。
     *
     * <p>面试场景的 interviewer_ids 是 JSON 数组，生成的条件是
     * {@code JSON_CONTAINS(interviewer_ids, CAST(? AS JSON))}。</p>
     *
     * @return 分配关系列名；为空表示回退到 ownerColumn
     */
    String assignedColumn() default "";
}
