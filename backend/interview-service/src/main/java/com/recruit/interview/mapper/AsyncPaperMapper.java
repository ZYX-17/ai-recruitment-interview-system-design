package com.recruit.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.interview.entity.AsyncPaper;
import com.recruit.interview.mapper.row.AsyncPaperRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 异步试卷 Mapper。
 *
 * <p>列表/详情 JOIN {@code application}/{@code job} 取候选人快照与数据范围字段；
 * 状态流转一律 {@code status + version} CAS，影响行数 0 由 Service 转 409。</p>
 */
public interface AsyncPaperMapper extends BaseMapper<AsyncPaper> {

    /**
     * 管理端详情行。
     *
     * @param id 试卷 ID
     * @return 行；不存在返回 null
     */
    @Select("SELECT p.*, a.job_id, a.job_title, a.person_id, a.person_name, j.dept_id, j.owner_hr_id "
            + "FROM async_paper p JOIN application a ON a.id = p.application_id "
            + "JOIN job j ON j.id = a.job_id WHERE p.id = #{id}")
    AsyncPaperRow selectRowById(@Param("id") long id);

    /**
     * 按投递取试卷行（application_id 唯一）。
     *
     * @param applicationId 投递 ID
     * @return 行；不存在返回 null
     */
    @Select("SELECT p.*, a.job_id, a.job_title, a.person_id, a.person_name, j.dept_id, j.owner_hr_id "
            + "FROM async_paper p JOIN application a ON a.id = p.application_id "
            + "JOIN job j ON j.id = a.job_id WHERE p.application_id = #{applicationId}")
    AsyncPaperRow selectRowByApplicationId(@Param("applicationId") long applicationId);

    /**
     * 门户按邀请 token 哈希查试卷。
     *
     * @param tokenHash sha256(token)
     * @return 行；不存在返回 null
     */
    @Select("SELECT p.*, a.job_id, a.job_title, a.person_id, a.person_name, j.dept_id, j.owner_hr_id "
            + "FROM async_paper p JOIN application a ON a.id = p.application_id "
            + "JOIN job j ON j.id = a.job_id WHERE p.invite_token_hash = #{tokenHash}")
    AsyncPaperRow selectRowByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * 试卷分页（按岗位数据范围过滤）。
     */
    @Select("<script>SELECT p.*, a.job_id, a.job_title, a.person_id, a.person_name, j.dept_id, j.owner_hr_id "
            + "FROM async_paper p JOIN application a ON a.id = p.application_id "
            + "JOIN job j ON j.id = a.job_id WHERE 1 = 1"
            + "<if test=\"keyword != null and keyword != ''\"> AND (p.paper_no LIKE CONCAT('%', #{keyword}, '%') "
            + "OR a.person_name LIKE CONCAT('%', #{keyword}, '%') "
            + "OR a.job_title LIKE CONCAT('%', #{keyword}, '%'))</if>"
            + "<if test=\"status != null and status != ''\"> AND p.status = #{status}</if>"
            + "<if test=\"jobId != null\"> AND a.job_id = #{jobId}</if>"
            + "<if test=\"applicationId != null\"> AND p.application_id = #{applicationId}</if>"
            + "<if test=\"scope == 'DEPARTMENT'\"><choose><when test=\"deptIds != null and deptIds.size() > 0\">"
            + " AND j.dept_id IN <foreach collection=\"deptIds\" item=\"d\" open=\"(\" separator=\",\" close=\")\">#{d}</foreach>"
            + "</when><otherwise> AND 1 = 0</otherwise></choose></if>"
            + "<if test=\"scope == 'SELF' or scope == 'ASSIGNED'\"> AND j.owner_hr_id = #{userId}</if> "
            + "ORDER BY p.created_at DESC, p.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<AsyncPaperRow> selectPaperPage(@Param("keyword") String keyword,
                                        @Param("status") String status,
                                        @Param("jobId") Long jobId,
                                        @Param("applicationId") Long applicationId,
                                        @Param("scope") String scope,
                                        @Param("deptIds") List<Long> deptIds,
                                        @Param("userId") long userId,
                                        @Param("limit") int limit,
                                        @Param("offset") long offset);

    /**
     * 试卷分页总数，条件与 {@link #selectPaperPage} 一致。
     */
    @Select("<script>SELECT COUNT(*) FROM async_paper p JOIN application a ON a.id = p.application_id "
            + "JOIN job j ON j.id = a.job_id WHERE 1 = 1"
            + "<if test=\"keyword != null and keyword != ''\"> AND (p.paper_no LIKE CONCAT('%', #{keyword}, '%') "
            + "OR a.person_name LIKE CONCAT('%', #{keyword}, '%') "
            + "OR a.job_title LIKE CONCAT('%', #{keyword}, '%'))</if>"
            + "<if test=\"status != null and status != ''\"> AND p.status = #{status}</if>"
            + "<if test=\"jobId != null\"> AND a.job_id = #{jobId}</if>"
            + "<if test=\"applicationId != null\"> AND p.application_id = #{applicationId}</if>"
            + "<if test=\"scope == 'DEPARTMENT'\"><choose><when test=\"deptIds != null and deptIds.size() > 0\">"
            + " AND j.dept_id IN <foreach collection=\"deptIds\" item=\"d\" open=\"(\" separator=\",\" close=\")\">#{d}</foreach>"
            + "</when><otherwise> AND 1 = 0</otherwise></choose></if>"
            + "<if test=\"scope == 'SELF' or scope == 'ASSIGNED'\"> AND j.owner_hr_id = #{userId}</if></script>")
    long countPaperPage(@Param("keyword") String keyword,
                        @Param("status") String status,
                        @Param("jobId") Long jobId,
                        @Param("applicationId") Long applicationId,
                        @Param("scope") String scope,
                        @Param("deptIds") List<Long> deptIds,
                        @Param("userId") long userId);

    /**
     * 修改 DRAFT 试卷（题目数可选更新）。
     */
    @Update("<script>UPDATE async_paper SET duration_limit_sec = #{durationLimitSec}, "
            + "settings_json = #{settingsJson}, version = version + 1"
            + "<if test=\"questionCount != null\">, question_count = #{questionCount}</if> "
            + "WHERE id = #{id} AND status = 'DRAFT' AND version = #{version}</script>")
    int updateDraft(@Param("id") long id,
                    @Param("durationLimitSec") int durationLimitSec,
                    @Param("settingsJson") String settingsJson,
                    @Param("questionCount") Integer questionCount,
                    @Param("version") int version);

    /**
     * 发布试卷并写入邀请 token 哈希。
     */
    @Update("UPDATE async_paper SET status = 'RELEASED', invite_token_hash = #{tokenHash}, "
            + "invite_expire_at = #{expireAt}, version = version + 1 "
            + "WHERE id = #{id} AND status = 'DRAFT' AND version = #{version}")
    int publish(@Param("id") long id,
                @Param("tokenHash") String tokenHash,
                @Param("expireAt") LocalDateTime expireAt,
                @Param("version") int version);

    /**
     * 重新生成邀请 token（旧链接立即失效）。
     */
    @Update("UPDATE async_paper SET invite_token_hash = #{tokenHash}, invite_expire_at = #{expireAt}, "
            + "version = version + 1 WHERE id = #{id} AND status IN ('RELEASED', 'IN_PROGRESS') "
            + "AND version = #{version}")
    int rotateInvite(@Param("id") long id,
                     @Param("tokenHash") String tokenHash,
                     @Param("expireAt") LocalDateTime expireAt,
                     @Param("version") int version);

    /**
     * 候选人开始作答（RELEASED → IN_PROGRESS）。
     */
    @Update("UPDATE async_paper SET status = 'IN_PROGRESS', started_at = #{startedAt}, "
            + "version = version + 1 WHERE id = #{id} AND status = 'RELEASED' AND version = #{version}")
    int markStarted(@Param("id") long id,
                    @Param("startedAt") LocalDateTime startedAt,
                    @Param("version") int version);

    /**
     * 候选人提交（IN_PROGRESS → SUBMITTED）。
     */
    @Update("UPDATE async_paper SET status = 'SUBMITTED', submitted_at = #{submittedAt}, "
            + "version = version + 1 WHERE id = #{id} AND status = 'IN_PROGRESS' AND version = #{version}")
    int markSubmitted(@Param("id") long id,
                      @Param("submittedAt") LocalDateTime submittedAt,
                      @Param("version") int version);

    /**
     * 评分完成（SUBMITTED/SCORED → SCORED）；复核人/时间只有显式传入时才覆盖。
     */
    @Update("<script>UPDATE async_paper SET status = 'SCORED', total_score = #{totalScore}, "
            + "version = version + 1"
            + "<if test=\"reviewedBy != null\">, reviewed_by = #{reviewedBy}</if>"
            + "<if test=\"reviewedAt != null\">, reviewed_at = #{reviewedAt}</if> "
            + "WHERE id = #{id} AND status IN ('SUBMITTED', 'SCORED') AND version = #{version}</script>")
    int markScored(@Param("id") long id,
                   @Param("totalScore") BigDecimal totalScore,
                   @Param("reviewedBy") Long reviewedBy,
                   @Param("reviewedAt") LocalDateTime reviewedAt,
                   @Param("version") int version);

    /**
     * HR 重置作答（SUBMITTED/SCORED → RELEASED），清空开始/提交/评分痕迹。
     */
    @Update("UPDATE async_paper SET status = 'RELEASED', started_at = NULL, submitted_at = NULL, "
            + "total_score = NULL, reviewed_by = NULL, reviewed_at = NULL, version = version + 1 "
            + "WHERE id = #{id} AND status IN ('SUBMITTED', 'SCORED') AND version = #{version}")
    int reset(@Param("id") long id, @Param("version") int version);

    /**
     * 按题目表重算题目数（仅 DRAFT）。
     */
    @Update("UPDATE async_paper SET question_count = "
            + "(SELECT COUNT(*) FROM async_question WHERE paper_id = #{id}) "
            + "WHERE id = #{id} AND status = 'DRAFT'")
    int refreshQuestionCount(@Param("id") long id);
}
