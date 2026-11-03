package com.recruit.notify.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.notify.entity.NotificationTemplate;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 通知模板 Mapper。发送时取 ACTIVE 的最新版本。
 */
public interface NotificationTemplateMapper extends BaseMapper<NotificationTemplate> {

    /**
     * 某 code+channel 的最新生效模板。
     */
    @Select("SELECT * FROM notification_template WHERE template_code = #{templateCode} "
            + "AND channel = #{channel} AND status = 'ACTIVE' ORDER BY version DESC LIMIT 1")
    NotificationTemplate selectActiveLatest(@Param("templateCode") String templateCode,
                                            @Param("channel") String channel);

    /**
     * 模板分页（按版本倒序）。
     */
    @Select("<script>SELECT * FROM notification_template WHERE 1 = 1"
            + "<if test=\"templateCode != null and templateCode != ''\"> AND template_code = #{templateCode}</if>"
            + "<if test=\"channel != null and channel != ''\"> AND channel = #{channel}</if>"
            + "<if test=\"status != null and status != ''\"> AND status = #{status}</if>"
            + " ORDER BY template_code ASC, channel ASC, version DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<NotificationTemplate> selectTemplatePage(@Param("templateCode") String templateCode,
                                                  @Param("channel") String channel,
                                                  @Param("status") String status,
                                                  @Param("limit") int limit,
                                                  @Param("offset") long offset);

    /**
     * 模板分页总数。
     */
    @Select("<script>SELECT COUNT(*) FROM notification_template WHERE 1 = 1"
            + "<if test=\"templateCode != null and templateCode != ''\"> AND template_code = #{templateCode}</if>"
            + "<if test=\"channel != null and channel != ''\"> AND channel = #{channel}</if>"
            + "<if test=\"status != null and status != ''\"> AND status = #{status}</if>"
            + "</script>")
    long countTemplatePage(@Param("templateCode") String templateCode,
                           @Param("channel") String channel,
                           @Param("status") String status);

    /**
     * 归档旧版本（CAS）。
     *
     * @return 影响行数；0 表示版本冲突或已归档
     */
    @Update("UPDATE notification_template SET status = 'ARCHIVED', updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND version = #{version} AND status = 'ACTIVE'")
    int archive(@Param("id") long id, @Param("version") int version);
}
