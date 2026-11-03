package com.recruit.notify.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.notify.entity.Notification;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知 Mapper。状态流转全部带状态条件，便于多实例下安全重试。
 */
public interface NotificationMapper extends BaseMapper<Notification> {

    /**
     * 本人站内信 / 通知分页。
     */
    @Select("<script>SELECT * FROM notification WHERE receiver_id = #{receiverId}"
            + "<if test=\"read != null and read == true\"> AND status = 'READ'</if>"
            + "<if test=\"read != null and read == false\"> AND status &lt;&gt; 'READ'</if>"
            + "<if test=\"status != null and status != ''\"> AND status = #{status}</if>"
            + "<if test=\"channel != null and channel != ''\"> AND channel = #{channel}</if>"
            + " ORDER BY created_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Notification> selectMessagePage(@Param("receiverId") long receiverId,
                                         @Param("read") Boolean read,
                                         @Param("status") String status,
                                         @Param("channel") String channel,
                                         @Param("limit") int limit,
                                         @Param("offset") long offset);

    /**
     * 分页总数，条件与 {@link #selectMessagePage} 一致。
     */
    @Select("<script>SELECT COUNT(*) FROM notification WHERE receiver_id = #{receiverId}"
            + "<if test=\"read != null and read == true\"> AND status = 'READ'</if>"
            + "<if test=\"read != null and read == false\"> AND status &lt;&gt; 'READ'</if>"
            + "<if test=\"status != null and status != ''\"> AND status = #{status}</if>"
            + "<if test=\"channel != null and channel != ''\"> AND channel = #{channel}</if>"
            + "</script>")
    long countMessagePage(@Param("receiverId") long receiverId,
                          @Param("read") Boolean read,
                          @Param("status") String status,
                          @Param("channel") String channel);

    /**
     * 未读数（READ 之前的已送达通知）。
     */
    @Select("SELECT COUNT(*) FROM notification WHERE receiver_id = #{receiverId} AND status = 'SENT'")
    long countUnread(@Param("receiverId") long receiverId);

    /**
     * 幂等已读。
     *
     * @return 影响行数；0 表示不是本人、不存在或已读
     */
    @Update("UPDATE notification SET status = 'READ', read_at = COALESCE(read_at, CURRENT_TIMESTAMP(3)), "
            + "version = version + 1, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND receiver_id = #{receiverId} AND status = 'SENT'")
    int markRead(@Param("id") long id, @Param("receiverId") long receiverId);

    /**
     * 发送成功：QUEUED/FAILED → SENT。
     */
    @Update("UPDATE notification SET status = 'SENT', sent_at = CURRENT_TIMESTAMP(3), error_msg = NULL, "
            + "next_retry_at = NULL, version = version + 1, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status IN ('QUEUED','FAILED')")
    int markSent(@Param("id") long id);

    /**
     * 发送失败：记录错误与下次重试时间。
     */
    @Update("UPDATE notification SET status = 'FAILED', error_msg = #{errorMsg}, retry_count = #{retryCount}, "
            + "next_retry_at = #{nextRetryAt}, version = version + 1, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status IN ('QUEUED','FAILED')")
    int markFailed(@Param("id") long id, @Param("errorMsg") String errorMsg,
                   @Param("retryCount") int retryCount,
                   @Param("nextRetryAt") LocalDateTime nextRetryAt);

    /**
     * 管理员重发前把 FAILED 改回 QUEUED（CAS 防并发）。
     */
    @Update("UPDATE notification SET status = 'QUEUED', error_msg = NULL, retry_count = 0, "
            + "next_retry_at = NULL, version = version + 1, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND version = #{version} AND status IN ('FAILED','QUEUED')")
    int resetForResend(@Param("id") long id, @Param("version") int version);

    /**
     * 到期重试候选（EMAIL、失败且尚未达上限）。
     */
    @Select("SELECT * FROM notification WHERE status = 'FAILED' AND channel = 'EMAIL' "
            + "AND retry_count < 3 AND next_retry_at IS NOT NULL AND next_retry_at <= CURRENT_TIMESTAMP(3) "
            + "ORDER BY next_retry_at ASC LIMIT #{limit}")
    List<Notification> selectRetryCandidates(@Param("limit") int limit);

    /**
     * 24 小时内是否已有同业务通知（dedupKey 缺失时的业务去重）。
     */
    @Select("SELECT COUNT(*) FROM notification WHERE biz_type = #{bizType} AND biz_id = #{bizId} "
            + "AND receiver_id = #{receiverId} AND channel = #{channel} "
            + "AND created_at >= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 24 HOUR)")
    long countRecentByBiz(@Param("bizType") String bizType, @Param("bizId") String bizId,
                          @Param("receiverId") long receiverId, @Param("channel") String channel);

    /**
     * 按 msg_no 查询（dedupKey 确定性去重）。
     */
    @Select("SELECT * FROM notification WHERE msg_no = #{msgNo}")
    Notification selectByMsgNo(@Param("msgNo") String msgNo);
}
