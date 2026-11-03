package com.recruit.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.interview.entity.LocalMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox Mapper。
 *
 * <p>扫描器只取到期的 PENDING；状态迁移一律 CAS（{@code status='PENDING'}），
 * 多实例下最多重复投递，消费端用 {@code idempotent_consumption} 兜底。</p>
 */
public interface LocalMessageMapper extends BaseMapper<LocalMessage> {

    /**
     * 取到期待投递消息。
     *
     * @param now   当前时间（UTC）
     * @param limit 批量上限
     * @return 待投递消息
     */
    @Select("SELECT * FROM local_message WHERE status = 'PENDING' "
            + "AND (next_retry_at IS NULL OR next_retry_at <= #{now}) "
            + "ORDER BY id LIMIT #{limit}")
    List<LocalMessage> selectPendingForRelay(@Param("now") LocalDateTime now,
                                             @Param("limit") int limit);

    /**
     * 投递确认后置 SENT。
     *
     * @param id  消息 ID
     * @param now 当前时间（UTC）
     * @return 影响行数
     */
    @Update("UPDATE local_message SET status = 'SENT', version = version + 1, updated_at = #{now} "
            + "WHERE id = #{id} AND status = 'PENDING'")
    int markSent(@Param("id") long id, @Param("now") LocalDateTime now);

    /**
     * 投递失败：重试次数 +1，按退避时间重排。
     *
     * @param id          消息 ID
     * @param retryCount  新重试次数
     * @param nextRetryAt 下次重试时间（UTC）
     * @param now         当前时间（UTC）
     * @return 影响行数
     */
    @Update("UPDATE local_message SET retry_count = #{retryCount}, next_retry_at = #{nextRetryAt}, "
            + "version = version + 1, updated_at = #{now} WHERE id = #{id} AND status = 'PENDING'")
    int markRetry(@Param("id") long id, @Param("retryCount") int retryCount,
                  @Param("nextRetryAt") LocalDateTime nextRetryAt,
                  @Param("now") LocalDateTime now);

    /**
     * 超过重试上限置 DEAD。
     *
     * @param id         消息 ID
     * @param retryCount 最终重试次数
     * @param now        当前时间（UTC）
     * @return 影响行数
     */
    @Update("UPDATE local_message SET status = 'DEAD', retry_count = #{retryCount}, "
            + "next_retry_at = NULL, version = version + 1, updated_at = #{now} "
            + "WHERE id = #{id} AND status = 'PENDING'")
    int markDead(@Param("id") long id, @Param("retryCount") int retryCount,
                 @Param("now") LocalDateTime now);

    /**
     * 管理员重放：DEAD/FAILED 回到 PENDING 并立即到期。
     *
     * @param id  消息 ID
     * @param now 当前时间（UTC）
     * @return 影响行数
     */
    @Update("UPDATE local_message SET status = 'PENDING', retry_count = 0, next_retry_at = #{now}, "
            + "version = version + 1, updated_at = #{now} "
            + "WHERE id = #{id} AND status IN ('DEAD','FAILED')")
    int replay(@Param("id") long id, @Param("now") LocalDateTime now);

    /**
     * Outbox 管理页列表。
     *
     * @param status    状态过滤，可空
     * @param eventType 事件类型过滤，可空
     * @param limit     页大小
     * @param offset    偏移
     * @return 消息列表
     */
    @Select("<script>SELECT * FROM local_message WHERE 1 = 1"
            + "<if test=\"status != null and status != ''\"> AND status = #{status}</if>"
            + "<if test=\"eventType != null and eventType != ''\"> AND event_type = #{eventType}</if>"
            + " ORDER BY created_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<LocalMessage> selectOutboxPage(@Param("status") String status,
                                        @Param("eventType") String eventType,
                                        @Param("limit") int limit,
                                        @Param("offset") long offset);

    /**
     * Outbox 管理页总数。
     *
     * @param status    状态过滤，可空
     * @param eventType 事件类型过滤，可空
     * @return 总数
     */
    @Select("<script>SELECT COUNT(*) FROM local_message WHERE 1 = 1"
            + "<if test=\"status != null and status != ''\"> AND status = #{status}</if>"
            + "<if test=\"eventType != null and eventType != ''\"> AND event_type = #{eventType}</if></script>")
    long countOutboxPage(@Param("status") String status, @Param("eventType") String eventType);
}
