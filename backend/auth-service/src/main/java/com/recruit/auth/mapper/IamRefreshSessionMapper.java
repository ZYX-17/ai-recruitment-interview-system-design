package com.recruit.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.auth.entity.IamRefreshSession;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * 查询用户当前仍有效的会话。
     *
     * <p>登出全部/改密/禁用时按 DB 反查 token_hash 与 family_id，
     * 再定向删除 Redis Key，避免使用 {@code KEYS auth:refresh:*} 全库扫描。</p>
     *
     * @param userId 用户 ID
     * @return ACTIVE 会话
     */
    @Select("SELECT * FROM iam_refresh_session WHERE user_id = #{userId} AND status = 'ACTIVE'")
    List<IamRefreshSession> selectActiveByUserId(@Param("userId") Long userId);

    /**
     * 撤销用户全部有效会话。
     *
     * @param userId    用户 ID
     * @param status    新状态（REVOKED）
     * @param revokedAt 撤销时间
     * @return 影响行数
     */
    @Update("UPDATE iam_refresh_session SET status = #{status}, revoked_at = #{revokedAt} "
            + "WHERE user_id = #{userId} AND status = 'ACTIVE'")
    int revokeActiveByUserId(@Param("userId") Long userId,
                             @Param("status") String status,
                             @Param("revokedAt") LocalDateTime revokedAt);
}
