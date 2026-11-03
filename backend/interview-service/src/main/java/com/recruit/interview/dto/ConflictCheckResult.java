package com.recruit.interview.dto;

import java.util.List;

/**
 * 冲突预检结果。
 *
 * @param conflict  是否冲突
 * @param conflicts 冲突明细；无冲突时为空列表
 */
public record ConflictCheckResult(boolean conflict, List<ConflictItemView> conflicts) {

    /**
     * 由明细构造结果。
     *
     * @param conflicts 冲突明细
     * @return 结果
     */
    public static ConflictCheckResult of(List<ConflictItemView> conflicts) {
        List<ConflictItemView> items = conflicts == null ? List.of() : List.copyOf(conflicts);
        return new ConflictCheckResult(!items.isEmpty(), items);
    }
}
