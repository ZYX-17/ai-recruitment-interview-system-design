package com.recruit.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.auth.entity.IamRefreshSession;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 刷新会话 Mapper。
 *
 * <p>状态迁移用定向 UPDATE，不整行覆盖：避免并发下把别的字段写回旧值。</p>
 */
public interface IamRefreshSessionMapper extends BaseMapper<IamRefreshSession> {

    /**
     * 按令牌哈希更新状态（轮换时旧令牌置 CONSUMED）。
     *
     * @param tokenHash 令牌哈希
     * @param status    新状态
     * @param revokedAt 撤销时间；CONSUMED 传 null
     * @return 影响行数
     */
    @Update("UPDATE iam_refresh_session SET status = #{status}, revoked_at = #{revokedAt} "
            + "WHERE token_hash = #{tokenHash}")
    int updateStatusByTokenHash(@Param("tokenHash") String tokenHash,
                                @Param("status") String status,
                                @Param("revokedAt") LocalDateTime revokedAt);

    /**
     * 按令牌族批量撤销（重放/登出/禁用）。
     *
     * @param familyId  令牌族
     * @param status    新状态（REVOKED / REPLAYED）
     * @param revokedAt 撤销时间
     * @return 影响行数
     */
    @Update("UPDATE iam_refresh_session SET status = #{status}, revoked_at = #{revokedAt} "
            + "WHERE family_id = #{familyId} AND status = 'ACTIVE'")
    int updateStatusByFamilyId(@Param("familyId") String familyId,
                               @Param("status") String status,
                               @Param("revokedAt") LocalDateTime revokedAt);
}
