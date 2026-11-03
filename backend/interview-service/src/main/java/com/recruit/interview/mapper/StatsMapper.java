package com.recruit.interview.mapper;

import com.recruit.interview.mapper.row.StageCountRow;
import com.recruit.interview.mapper.row.StageNameRow;
import com.recruit.interview.mapper.row.TimeToHireRow;
import com.recruit.interview.mapper.row.WorkloadRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 统计查询（P11）。
 *
 * <p>数据范围与投递列表一致：ALL 不过滤、DEPARTMENT 按岗位部门子树、
 * SELF/ASSIGNED 按岗位负责人；工作量另按面试官本人过滤。
 * 漏斗/工作量排除 CANCELLED，NO_SHOW 单列（ADR-004）。</p>
 */
public interface StatsMapper {

    /**
     * 时间范围内新增投递数（漏斗第一层）。
     *
     * @param from   开始时间（UTC，含）
     * @param to     结束时间（UTC，不含）
     * @param jobId  岗位 ID，可空
     * @param scope  数据范围
     * @param deptIds 部门子树
     * @param userId 当前用户
     * @return 投递数
     */
    @Select("<script>SELECT COUNT(*) FROM application a JOIN job j ON j.id = a.job_id "
            + "WHERE a.created_at &gt;= #{from} AND a.created_at &lt; #{to}"
            + "<if test=\"jobId != null\"> AND a.job_id = #{jobId}</if>"
            + "<if test=\"scope == 'DEPARTMENT'\"><choose><when test=\"deptIds != null and deptIds.size() > 0\">"
            + " AND j.dept_id IN <foreach collection=\"deptIds\" item=\"d\" open=\"(\" separator=\",\" close=\")\">#{d}</foreach>"
            + "</when><otherwise> AND 1 = 0</otherwise></choose></if>"
            + "<if test=\"scope == 'SELF' or scope == 'ASSIGNED'\"> AND j.owner_hr_id = #{userId}</if>"
            + "</script>")
    long countApplied(@Param("from") LocalDateTime from,
                      @Param("to") LocalDateTime to,
                      @Param("jobId") Long jobId,
                      @Param("scope") String scope,
                      @Param("deptIds") List<Long> deptIds,
                      @Param("userId") long userId);

    /**
     * 时间范围内各阶段到达数（去重投递，来自流转历史）。
     *
     * @param from   开始时间（UTC，含）
     * @param to     结束时间（UTC，不含）
     * @param jobId  岗位 ID，可空
     * @param scope  数据范围
     * @param deptIds 部门子树
     * @param userId 当前用户
     * @return 阶段计数
     */
    @Select("<script>SELECT h.stage_no AS stageNo, COUNT(DISTINCT h.application_id) AS total "
            + "FROM application_stage_history h JOIN application a ON a.id = h.application_id "
            + "JOIN job j ON j.id = a.job_id "
            + "WHERE h.operated_at &gt;= #{from} AND h.operated_at &lt; #{to}"
            + "<if test=\"jobId != null\"> AND a.job_id = #{jobId}</if>"
            + "<if test=\"scope == 'DEPARTMENT'\"><choose><when test=\"deptIds != null and deptIds.size() > 0\">"
            + " AND j.dept_id IN <foreach collection=\"deptIds\" item=\"d\" open=\"(\" separator=\",\" close=\")\">#{d}</foreach>"
            + "</when><otherwise> AND 1 = 0</otherwise></choose></if>"
            + "<if test=\"scope == 'SELF' or scope == 'ASSIGNED'\"> AND j.owner_hr_id = #{userId}</if>"
            + " GROUP BY h.stage_no</script>")
    List<StageCountRow> selectStageCounts(@Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to,
                                          @Param("jobId") Long jobId,
                                          @Param("scope") String scope,
                                          @Param("deptIds") List<Long> deptIds,
                                          @Param("userId") long userId);

    /**
     * 流程模板中的阶段定义（顺序与中文名）。
     *
     * @return 阶段定义（按模板顺序）
     */
    @Select("SELECT jt.stage_no AS stageNo, MIN(jt.stage_name) AS stageName, MIN(jt.ord) AS ord "
            + "FROM job_stage_template t JOIN JSON_TABLE(t.stage_flow, '$[*]' COLUMNS ("
            + "ord FOR ORDINALITY, stage_no VARCHAR(32) PATH '$.stageNo', "
            + "stage_name VARCHAR(64) PATH '$.stageName')) jt "
            + "GROUP BY jt.stage_no ORDER BY ord")
    List<StageNameRow> selectStageNames();

    /**
     * 面试官工作量（按 interview.interviewer_ids JSON 展开）。
     *
     * @param from          开始时间（UTC，含）
     * @param to            结束时间（UTC，不含）
     * @param interviewerId 指定面试官，可空
     * @param scope         数据范围
     * @param deptIds       部门子树
     * @param userId        当前用户
     * @return 工作量行
     */
    @Select("<script>SELECT CAST(jt.interviewer_id AS CHAR) AS interviewerId, COUNT(*) AS total, "
            + "SUM(CASE WHEN i.status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed, "
            + "SUM(CASE WHEN i.status = 'NO_SHOW' THEN 1 ELSE 0 END) AS noShow "
            + "FROM interview i JOIN application a ON a.id = i.application_id "
            + "JOIN job j ON j.id = a.job_id "
            + "JOIN JSON_TABLE(i.interviewer_ids, '$[*]' COLUMNS (interviewer_id BIGINT PATH '$')) jt "
            + "WHERE i.start_time &gt;= #{from} AND i.start_time &lt; #{to} AND i.status &lt;&gt; 'CANCELLED'"
            + "<if test=\"scope == 'DEPARTMENT'\"><choose><when test=\"deptIds != null and deptIds.size() > 0\">"
            + " AND j.dept_id IN <foreach collection=\"deptIds\" item=\"d\" open=\"(\" separator=\",\" close=\")\">#{d}</foreach>"
            + "</when><otherwise> AND 1 = 0</otherwise></choose></if>"
            + "<if test=\"scope == 'SELF' or scope == 'ASSIGNED'\"> AND jt.interviewer_id = #{userId}</if>"
            + "<if test=\"interviewerId != null\"> AND jt.interviewer_id = #{interviewerId}</if>"
            + " GROUP BY jt.interviewer_id ORDER BY total DESC, jt.interviewer_id</script>")
    List<WorkloadRow> selectWorkload(@Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to,
                                     @Param("interviewerId") Long interviewerId,
                                     @Param("scope") String scope,
                                     @Param("deptIds") List<Long> deptIds,
                                     @Param("userId") long userId);

    /**
     * 招聘周期：投递到入职的天数聚合。
     *
     * @param from   开始时间（UTC，含，按入职时间过滤）
     * @param to     结束时间（UTC，不含）
     * @param jobId  岗位 ID，可空
     * @param scope  数据范围
     * @param deptIds 部门子树
     * @param userId 当前用户
     * @return 聚合行
     */
    @Select("<script>SELECT COUNT(*) AS sampleCount, "
            + "AVG(TIMESTAMPDIFF(MINUTE, a.created_at, h.hired_at) / 1440.0) AS avgDays, "
            + "MIN(TIMESTAMPDIFF(MINUTE, a.created_at, h.hired_at) / 1440.0) AS minDays, "
            + "MAX(TIMESTAMPDIFF(MINUTE, a.created_at, h.hired_at) / 1440.0) AS maxDays "
            + "FROM application a JOIN (SELECT application_id, MIN(operated_at) AS hired_at "
            + "FROM application_stage_history WHERE status = 'HIRED' OR stage_no = 'HIRED' "
            + "GROUP BY application_id) h ON h.application_id = a.id "
            + "JOIN job j ON j.id = a.job_id "
            + "WHERE h.hired_at &gt;= #{from} AND h.hired_at &lt; #{to}"
            + "<if test=\"jobId != null\"> AND a.job_id = #{jobId}</if>"
            + "<if test=\"scope == 'DEPARTMENT'\"><choose><when test=\"deptIds != null and deptIds.size() > 0\">"
            + " AND j.dept_id IN <foreach collection=\"deptIds\" item=\"d\" open=\"(\" separator=\",\" close=\")\">#{d}</foreach>"
            + "</when><otherwise> AND 1 = 0</otherwise></choose></if>"
            + "<if test=\"scope == 'SELF' or scope == 'ASSIGNED'\"> AND j.owner_hr_id = #{userId}</if>"
            + "</script>")
    TimeToHireRow selectTimeToHire(@Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to,
                                   @Param("jobId") Long jobId,
                                   @Param("scope") String scope,
                                   @Param("deptIds") List<Long> deptIds,
                                   @Param("userId") long userId);
}
