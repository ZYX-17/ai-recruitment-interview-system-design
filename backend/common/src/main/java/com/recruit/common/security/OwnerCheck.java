package com.recruit.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 对象级归属校验：标注在“按 ID 查单个对象”的 Service 方法上，防止水平越权。
 *
 * <p>行为约定（与开发文档 §7.3.2 一致）：</p>
 * <ul>
 *   <li>方法返回 null：抛 404（默认 SYS404）；</li>
 *   <li>对象存在，但 owner 不是当前用户且当前用户不在放行角色里：抛 403（默认 AUTH1101）；</li>
 *   <li>owner 等于当前用户，或命中 {@link #bypassRoles()}：正常返回。</li>
 * </ul>
 *
 * <p>典型用法：</p>
 * <pre>
 * &#64;OwnerCheck(resource = "application", ownerProperty = "ownerHrId")
 * public ApplicationVO getById(String id) { ... }
 * </pre>
 *
 * <p><b>限制：</b>只能用于返回单个对象的方法。列表接口请用 {@link DataScope}，
 * 因为列表无法逐条判断归属，且逐条过滤会泄露总数。</p>
 *
 * <p>切面会在方法执行之后检查返回值，所以“查不到”仍应由业务方法返回 null 或抛业务异常，
 * 而不是在切面里再查一次数据库（避免重复查询）。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OwnerCheck {

    /**
     * 资源名，用于错误文案和日志，例如 application / offer。
     *
     * @return 资源名
     */
    String resource() default "";

    /**
     * 返回值里表示“归属人”的属性名，支持嵌套路径，例如 ownerHrId、application.ownerHrId。
     *
     * @return 属性名
     */
    String ownerProperty() default "ownerId";

    /**
     * 路径参数名，仅用于日志排查（例如 id）。
     *
     * @return 参数名
     */
    String idParam() default "id";

    /**
     * 对象不存在时使用的错误码，必须是 {@link com.recruit.common.result.ErrorCode} 中已登记的值。
     *
     * @return 错误码
     */
    String notFoundCode() default "SYS404";

    /**
     * 无权访问时使用的错误码，默认 AUTH1101（403）。
     *
     * @return 错误码
     */
    String forbiddenCode() default "AUTH1101";

    /**
     * 可以跳过归属校验的角色，默认只有超级管理员。
     *
     * <p>不要把 HR_ADMIN 默认放进来：管理员也应受数据范围约束，
     * 需要放行的场景请显式配置。</p>
     *
     * @return 角色编码数组
     */
    String[] bypassRoles() default {"SUPER_ADMIN"};
}
