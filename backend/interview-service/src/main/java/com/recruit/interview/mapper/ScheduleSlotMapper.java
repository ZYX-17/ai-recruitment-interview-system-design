package com.recruit.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.interview.entity.ScheduleSlot;
import com.recruit.interview.mapper.row.ScheduleConflictRow;
import com.recruit.interview.support.ScheduleParticipant;
import com.recruit.interview.support.ScheduleSlotKey;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 排期格子 Mapper。
 *
 * <p>冲突查询按“格子 × 参与者”精确匹配，避免把同一天其他时段误判为冲突。
 * 改期/取消先物理删除旧行，唯一索引随之释放。</p>
 */
public interface ScheduleSlotMapper extends BaseMapper<ScheduleSlot> {

    /**
     * 删除某场面试的全部格子。
     *
     * @param interviewId 面试 ID
     * @return 删除行数
     */
    @Delete("DELETE FROM schedule_slot WHERE interview_id = #{interviewId}")
    int deleteByInterviewId(@Param("interviewId") long interviewId);

    /**
     * 查询某场面试的全部格子。
     *
     * @param interviewId 面试 ID
     * @return 格子列表
     */
    @Select("SELECT * FROM schedule_slot WHERE interview_id = #{interviewId} "
            + "ORDER BY slot_date ASC, slot_no ASC")
    List<ScheduleSlot> selectByInterviewId(@Param("interviewId") long interviewId);

    /**
     * 冲突预检：给定格子与参与者，查已占用的行。
     *
     * @param keys                待占用格子
     * @param participants        参与者
     * @param excludeInterviewIds 需要排除的面试（改期时排除自身、批量时排除本批已插入的）
     * @return 冲突行，带已有面试时间
     */
    @Select("<script>SELECT s.participant_type, s.participant_id, s.slot_date, s.slot_no, "
            + "s.interview_id, i.start_time, i.end_time FROM schedule_slot s "
            + "JOIN interview i ON i.id = s.interview_id WHERE ("
            + "<foreach collection=\"keys\" item=\"k\" separator=\" OR \">"
            + "(s.slot_date = #{k.slotDate} AND s.slot_no = #{k.slotNo})</foreach>)"
            + " AND ("
            + "<foreach collection=\"participants\" item=\"p\" separator=\" OR \">"
            + "(s.participant_type = #{p.participantType} AND s.participant_id = #{p.participantId})"
            + "</foreach>)"
            + "<if test=\"excludeInterviewIds != null and excludeInterviewIds.size() > 0\">"
            + " AND s.interview_id NOT IN "
            + "<foreach collection=\"excludeInterviewIds\" item=\"x\" open=\"(\" separator=\",\" close=\")\">#{x}</foreach>"
            + "</if>"
            + " ORDER BY s.slot_date ASC, s.slot_no ASC, s.participant_type ASC</script>")
    List<ScheduleConflictRow> selectConflicts(@Param("keys") List<ScheduleSlotKey> keys,
                                              @Param("participants") List<ScheduleParticipant> participants,
                                              @Param("excludeInterviewIds") List<Long> excludeInterviewIds);
}
