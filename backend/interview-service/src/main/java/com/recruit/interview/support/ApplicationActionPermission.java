package com.recruit.interview.support;

import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 投递动作的精确权限校验（OpenAPI §7.1）。
 *
 * <p>Controller 的方法级注解只能做“具备任一投递写权限”的粗过滤；
 * 真正执行哪个动作必须按 {@code action} 再查一次权限码，避免只有“推进”权限的人调用“淘汰”。</p>
 */
@Component
@RequiredArgsConstructor
public class ApplicationActionPermission {

    public static final String ACTION_ADVANCE = "ADVANCE";
    public static final String ACTION_PASS = "PASS";
    public static final String ACTION_REJECT = "REJECT";
    public static final String ACTION_HOLD = "HOLD";
    public static final String ACTION_TRANSFER = "TRANSFER";
    public static final String ACTION_WITHDRAW = "WITHDRAW";
    public static final String ACTION_HIRE = "HIRE";

    private static final String PERM_UPDATE = "interview:application:update";
    private static final String PERM_REJECT = "interview:application:reject";
    private static final String PERM_TRANSFER = "interview:application:transfer";
    private static final String PERM_WITHDRAW = "interview:application:withdraw";

    private final PermissionChecker permissionChecker;

    /**
     * 校验动作对应权限码。
     *
     * @param action 动作（大小写不敏感）
     * @throws BizException 无权限时抛 403
     */
    public void require(String action) {
        String code = permissionCode(action);
        if (!permissionChecker.has(code)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "无权执行该投递动作");
        }
    }

    /**
     * 动作对应权限码；未知动作按“推进”权限处理，随后由业务层返回 400。
     *
     * @param action 动作
     * @return 权限码
     */
    public static String permissionCode(String action) {
        if (action == null) {
            return PERM_UPDATE;
        }
        return switch (action.trim().toUpperCase(Locale.ROOT)) {
            case ACTION_REJECT -> PERM_REJECT;
            case ACTION_TRANSFER -> PERM_TRANSFER;
            case ACTION_WITHDRAW -> PERM_WITHDRAW;
            default -> PERM_UPDATE;
        };
    }
}
