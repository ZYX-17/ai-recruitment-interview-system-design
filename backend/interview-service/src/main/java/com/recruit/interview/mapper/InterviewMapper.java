package com.recruit.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.interview.entity.Interview;
import com.recruit.interview.mapper.row.InterviewRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 面试安排 Mapper。
 *
 * <p>列表/日历统一 JOIN {@code application} 与 {@code job}：既拿到岗位/候选人快照，
 * 也让数据范围条件落在岗位的 {@code dept_id/owner_hr_id} 上。数据范围用显式 SQL 条件，
 * 不依赖全局 MyBatis 拦截器（拦截器缺失时会静默越权）。</p>
 */
public interface InterviewMapper extends BaseMapper<Interview> {

    /**
     * 详情行（含投递/岗位快照）。
     *
     * @param id 面试 ID
     * @return 行；不存在返回 null
     */
    @Select("SELECT i.*, a.job_id, a.job_title, a.person_id, a.person_name, "
            + "j.dept_id, j.owner_hr_id FROM interview i "
            + "JOIN application a ON a.id = i.application_id "
            + "JOIN job j ON j.id = a.job_id WHERE i.id = #{id}")
    InterviewRow selectRowById(@Param("id") long id);

    /**
     * 同一投递同一轮是否已有面试。
     *
     * @param applicationId 投递 ID
     * @param roundNo       轮次
     * @return 已存在数量
     */
    @Select("SELECT COUNT(*) FROM interview WHERE application_id = #{applicationId} "
            + "AND round_no = #{roundNo}")
    long countByApplicationAndRound(@Param("applicationId") Long applicationId,
                                    @Param("roundNo") Integer roundNo);

    /**
     * 版本乐观锁更新排期。
     *
     * @param interview 新值；{@code id/version} 为定位条件
     * @return 影响行数；0 表示版本冲突
     */
    @Update("UPDATE interview SET interviewer_ids = #{interviewerIds}, start_time = #{startTime}, "
            + "end_time = #{endTime}, mode = #{mode}, room_type = #{roomType}, note = #{note}, "
            + "version = version + 1, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND version = #{version}")
    int updateScheduleWithVersion(Interview interview);

    /**
     * 取消面试：仅 ROOM_PENDING/SCHEDULED 可取消，带版本条件。
     *
     * @param id      面试 ID
     * @param reason  取消原因
     * @param version 期望版本
     * @return 影响行数；0 表示版本冲突或状态不允许
     */
    @Update("UPDATE interview SET status = 'CANCELLED', cancel_reason = #{reason}, "
            + "version = version + 1, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND version = #{version} AND status IN ('ROOM_PENDING','SCHEDULED')")
    int cancelWithVersion(@Param("id") long id, @Param("reason") String reason,
                          @Param("version") int version);

    /**
     * 房间创建成功回执：ROOM_PENDING → SCHEDULED 并回填 room_id。
     *
     * <p>只更新 ROOM_PENDING：晚到的 room.created 不得把 CANCELLED/COMPLETED 拉回。</p>
     */
    @Update("UPDATE interview SET room_id = #{roomId}, status = 'SCHEDULED', "
            + "room_fail_reason = NULL, version = version + 1, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status = 'ROOM_PENDING'")
    int markRoomCreated(@Param("id") long id, @Param("roomId") long roomId);

    /**
     * 建房失败：保持 ROOM_PENDING，记录原因供人工重试。
     */
    @Update("UPDATE interview SET room_fail_reason = #{reason}, version = version + 1, "
            + "updated_at = CURRENT_TIMESTAMP(3) WHERE id = #{id} AND status = 'ROOM_PENDING'")
    int markRoomFailed(@Param("id") long id, @Param("reason") String reason);

    /**
     * 房间结束：面试置 COMPLETED（幂等；不把 CANCELLED/NO_SHOW 改回完成）。
     */
    @Update("UPDATE interview SET status = 'COMPLETED', version = version + 1, "
            + "updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status IN ('ROOM_PENDING','SCHEDULED','IN_PROGRESS')")
    int markCompleted(@Param("id") long id);

    /**
     * 排期分页查询。
     *
     * @param status        状态，可为空
     * @param startFrom     开始时间下界（UTC），可为空
     * @param startTo       开始时间上界（UTC，开区间），可为空
     * @param interviewerId 面试官 ID 文本，可为空
     * @param deptId        部门 ID，可为空
     * @param scope         数据范围
     * @param deptIds       部门子树
     * @param userId        当前用户
     * @param userIdText    当前用户 ID 文本（JSON 匹配用）
     * @param limit         每页条数
     * @param offset        偏移
     * @return 分页行
     */
    @Select("<script>SELECT i.*, a.job_id, a.job_title, a.person_id, a.person_name, "
            + "j.dept_id, j.owner_hr_id FROM interview i "
            + "JOIN application a ON a.id = i.application_id "
            + "JOIN job j ON j.id = a.job_id WHERE 1 = 1"
            + "<if test=\"status != null and status != ''\"> AND i.status = #{status}</if>"
            + "<if test=\"startFrom != null\"> AND i.start_time &gt;= #{startFrom}</if>"
            + "<if test=\"startTo != null\"> AND i.start_time &lt; #{startTo}</if>"
            + "<if test=\"interviewerId != null and interviewerId != ''\">"
            + " AND JSON_CONTAINS(i.interviewer_ids, JSON_QUOTE(#{interviewerId}))</if>"
            + "<if test=\"deptId != null\"> AND j.dept_id = #{deptId}</if>"
            + "<if test=\"scope == 'DEPARTMENT'\"><choose>"
            + "<when test=\"deptIds != null and deptIds.size() > 0\">"
            + " AND j.dept_id IN <foreach collection=\"deptIds\" item=\"d\" open=\"(\" separator=\",\" close=\")\">#{d}</foreach>"
            + "</when><otherwise> AND 1 = 0</otherwise></choose></if>"
            + "<if test=\"scope == 'SELF' or scope == 'ASSIGNED'\">"
            + " AND (j.owner_hr_id = #{userId} OR JSON_CONTAINS(i.interviewer_ids, JSON_QUOTE(#{userIdText})))</if>"
            + " ORDER BY i.start_time DESC, i.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<InterviewRow> selectSchedulePage(@Param("status") String status,
                                          @Param("startFrom") LocalDateTime startFrom,
                                          @Param("startTo") LocalDateTime startTo,
                                          @Param("interviewerId") String interviewerId,
                                          @Param("deptId") Long deptId,
                                          @Param("scope") String scope,
                                          @Param("deptIds") List<Long> deptIds,
                                          @Param("userId") long userId,
                                          @Param("userIdText") String userIdText,
                                          @Param("limit") int limit,
                                          @Param("offset") long offset);

    /**
     * 排期分页总数，条件与 {@link #selectSchedulePage} 一致。
     */
    @Select("<script>SELECT COUNT(*) FROM interview i "
            + "JOIN application a ON a.id = i.application_id "
            + "JOIN job j ON j.id = a.job_id WHERE 1 = 1"
            + "<if test=\"status != null and status != ''\"> AND i.status = #{status}</if>"
            + "<if test=\"startFrom != null\"> AND i.start_time &gt;= #{startFrom}</if>"
            + "<if test=\"startTo != null\"> AND i.start_time &lt; #{startTo}</if>"
            + "<if test=\"interviewerId != null and interviewerId != ''\">"
            + " AND JSON_CONTAINS(i.interviewer_ids, JSON_QUOTE(#{interviewerId}))</if>"
            + "<if test=\"deptId != null\"> AND j.dept_id = #{deptId}</if>"
            + "<if test=\"scope == 'DEPARTMENT'\"><choose>"
            + "<when test=\"deptIds != null and deptIds.size() > 0\">"
            + " AND j.dept_id IN <foreach collection=\"deptIds\" item=\"d\" open=\"(\" separator=\",\" close=\")\">#{d}</foreach>"
            + "</when><otherwise> AND 1 = 0</otherwise></choose></if>"
            + "<if test=\"scope == 'SELF' or scope == 'ASSIGNED'\">"
            + " AND (j.owner_hr_id = #{userId} OR JSON_CONTAINS(i.interviewer_ids, JSON_QUOTE(#{userIdText})))</if>"
            + "</script>")
    long countSchedulePage(@Param("status") String status,
                           @Param("startFrom") LocalDateTime startFrom,
                           @Param("startTo") LocalDateTime startTo,
                           @Param("interviewerId") String interviewerId,
                           @Param("deptId") Long deptId,
                           @Param("scope") String scope,
                           @Param("deptIds") List<Long> deptIds,
                           @Param("userId") long userId,
                           @Param("userIdText") String userIdText);

    /**
     * 日历查询：只读、不分页，按开始时间升序。
     */
    @Select("<script>SELECT i.*, a.job_id, a.job_title, a.person_id, a.person_name, "
            + "j.dept_id, j.owner_hr_id FROM interview i "
            + "JOIN application a ON a.id = i.application_id "
            + "JOIN job j ON j.id = a.job_id WHERE i.start_time &gt;= #{startFrom} "
            + "AND i.start_time &lt; #{startTo}"
            + "<if test=\"interviewerId != null and interviewerId != ''\">"
            + " AND JSON_CONTAINS(i.interviewer_ids, JSON_QUOTE(#{interviewerId}))</if>"
            + "<if test=\"deptId != null\"> AND j.dept_id = #{deptId}</if>"
            + "<if test=\"scope == 'DEPARTMENT'\"><choose>"
            + "<when test=\"deptIds != null and deptIds.size() > 0\">"
            + " AND j.dept_id IN <foreach collection=\"deptIds\" item=\"d\" open=\"(\" separator=\",\" close=\")\">#{d}</foreach>"
            + "</when><otherwise> AND 1 = 0</otherwise></choose></if>"
            + "<if test=\"scope == 'SELF' or scope == 'ASSIGNED'\">"
            + " AND (j.owner_hr_id = #{userId} OR JSON_CONTAINS(i.interviewer_ids, JSON_QUOTE(#{userIdText})))</if>"
            + " ORDER BY i.start_time ASC, i.id ASC</script>")
    List<InterviewRow> selectCalendar(@Param("startFrom") LocalDateTime startFrom,
                                      @Param("startTo") LocalDateTime startTo,
                                      @Param("interviewerId") String interviewerId,
                                      @Param("deptId") Long deptId,
                                      @Param("scope") String scope,
                                      @Param("deptIds") List<Long> deptIds,
                                      @Param("userId") long userId,
                                      @Param("userIdText") String userIdText);
}
