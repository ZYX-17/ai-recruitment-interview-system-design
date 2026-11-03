package com.recruit.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.interview.entity.Offer;
import com.recruit.interview.mapper.row.OfferRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Offer Mapper。
 *
 * <p>列表/详情 JOIN {@code application}/{@code job} 取候选人快照与数据范围字段；
 * 所有状态流转都用 {@code version} + 状态条件 CAS，影响行数 0 由 Service 转 409。</p>
 */
public interface OfferMapper extends BaseMapper<Offer> {

    /**
     * 管理端详情行。
     *
     * @param id Offer ID
     * @return 行；不存在返回 null
     */
    @Select("SELECT o.*, a.job_id, a.person_id, a.person_name, j.dept_id, j.owner_hr_id "
            + "FROM offer o JOIN application a ON a.id = o.application_id "
            + "JOIN job j ON j.id = a.job_id WHERE o.id = #{id}")
    OfferRow selectRowById(@Param("id") long id);

    /**
     * 按投递取 Offer 行（application_id 唯一）。
     *
     * @param applicationId 投递 ID
     * @return 行；不存在返回 null
     */
    @Select("SELECT o.*, a.job_id, a.person_id, a.person_name, j.dept_id, j.owner_hr_id "
            + "FROM offer o JOIN application a ON a.id = o.application_id "
            + "JOIN job j ON j.id = a.job_id WHERE o.application_id = #{applicationId}")
    OfferRow selectRowByApplicationId(@Param("applicationId") long applicationId);

    /**
     * 门户按 token 哈希查 Offer。
     *
     * @param tokenHash sha256(token)
     * @return 行；不存在返回 null
     */
    @Select("SELECT o.*, a.job_id, a.person_id, a.person_name, j.dept_id, j.owner_hr_id "
            + "FROM offer o JOIN application a ON a.id = o.application_id "
            + "JOIN job j ON j.id = a.job_id WHERE o.confirm_token_hash = #{tokenHash}")
    OfferRow selectRowByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * Offer 分页（按岗位数据范围过滤）。
     */
    @Select("<script>SELECT o.*, a.job_id, a.person_id, a.person_name, j.dept_id, j.owner_hr_id "
            + "FROM offer o JOIN application a ON a.id = o.application_id "
            + "JOIN job j ON j.id = a.job_id WHERE 1 = 1"
            + "<if test=\"keyword != null and keyword != ''\"> AND (o.offer_no LIKE CONCAT('%', #{keyword}, '%') "
            + "OR a.person_name LIKE CONCAT('%', #{keyword}, '%'))</if>"
            + "<if test=\"status != null and status != ''\"> AND o.status = #{status}</if>"
            + "<if test=\"jobId != null\"> AND a.job_id = #{jobId}</if>"
            + "<if test=\"scope == 'DEPARTMENT'\"><choose><when test=\"deptIds != null and deptIds.size() > 0\">"
            + " AND j.dept_id IN <foreach collection=\"deptIds\" item=\"d\" open=\"(\" separator=\",\" close=\")\">#{d}</foreach>"
            + "</when><otherwise> AND 1 = 0</otherwise></choose></if>"
            + "<if test=\"scope == 'SELF' or scope == 'ASSIGNED'\"> AND j.owner_hr_id = #{userId}</if> "
            + "ORDER BY o.created_at DESC, o.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<OfferRow> selectOfferPage(@Param("keyword") String keyword,
                                   @Param("status") String status,
                                   @Param("jobId") Long jobId,
                                   @Param("scope") String scope,
                                   @Param("deptIds") List<Long> deptIds,
                                   @Param("userId") long userId,
                                   @Param("limit") int limit,
                                   @Param("offset") long offset);

    /**
     * Offer 分页总数，条件与 {@link #selectOfferPage} 一致。
     */
    @Select("<script>SELECT COUNT(*) FROM offer o JOIN application a ON a.id = o.application_id "
            + "JOIN job j ON j.id = a.job_id WHERE 1 = 1"
            + "<if test=\"keyword != null and keyword != ''\"> AND (o.offer_no LIKE CONCAT('%', #{keyword}, '%') "
            + "OR a.person_name LIKE CONCAT('%', #{keyword}, '%'))</if>"
            + "<if test=\"status != null and status != ''\"> AND o.status = #{status}</if>"
            + "<if test=\"jobId != null\"> AND a.job_id = #{jobId}</if>"
            + "<if test=\"scope == 'DEPARTMENT'\"><choose><when test=\"deptIds != null and deptIds.size() > 0\">"
            + " AND j.dept_id IN <foreach collection=\"deptIds\" item=\"d\" open=\"(\" separator=\",\" close=\")\">#{d}</foreach>"
            + "</when><otherwise> AND 1 = 0</otherwise></choose></if>"
            + "<if test=\"scope == 'SELF' or scope == 'ASSIGNED'\"> AND j.owner_hr_id = #{userId}</if></script>")
    long countOfferPage(@Param("keyword") String keyword,
                        @Param("status") String status,
                        @Param("jobId") Long jobId,
                        @Param("scope") String scope,
                        @Param("deptIds") List<Long> deptIds,
                        @Param("userId") long userId);

    /**
     * 发送：DRAFT/PENDING_APPROVAL → SENT，并写入 token 哈希。
     *
     * @return 影响行数；0 表示版本冲突或状态不允许
     */
    @Update("UPDATE offer SET status = 'SENT', confirm_token_hash = #{tokenHash}, "
            + "version = version + 1, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND version = #{version} AND status IN ('DRAFT','PENDING_APPROVAL')")
    int markSent(@Param("id") long id, @Param("tokenHash") String tokenHash,
                 @Param("version") int version);

    /**
     * 内部取链接时轮换 token 哈希（仅 SENT）。
     *
     * @return 影响行数；0 表示版本冲突或状态不允许
     */
    @Update("UPDATE offer SET confirm_token_hash = #{tokenHash}, version = version + 1, "
            + "updated_at = CURRENT_TIMESTAMP(3) WHERE id = #{id} AND version = #{version} "
            + "AND status = 'SENT'")
    int rotateToken(@Param("id") long id, @Param("tokenHash") String tokenHash,
                    @Param("version") int version);

    /**
     * 审批驳回：PENDING_APPROVAL/DRAFT → DRAFT。
     *
     * @return 影响行数；0 表示版本冲突或状态不允许
     */
    @Update("UPDATE offer SET status = 'DRAFT', version = version + 1, "
            + "updated_at = CURRENT_TIMESTAMP(3) WHERE id = #{id} AND version = #{version} "
            + "AND status IN ('DRAFT','PENDING_APPROVAL')")
    int markDraft(@Param("id") long id, @Param("version") int version);

    /**
     * 撤回：未确认可撤回。
     *
     * @return 影响行数；0 表示版本冲突或状态不允许
     */
    @Update("UPDATE offer SET status = 'WITHDRAWN', version = version + 1, "
            + "updated_at = CURRENT_TIMESTAMP(3) WHERE id = #{id} AND version = #{version} "
            + "AND status IN ('DRAFT','PENDING_APPROVAL','SENT')")
    int markWithdrawn(@Param("id") long id, @Param("version") int version);

    /**
     * 候选人确认：SENT → CONFIRMED。
     *
     * @return 影响行数；0 表示已处理或版本冲突
     */
    @Update("UPDATE offer SET status = 'CONFIRMED', confirmed_at = CURRENT_TIMESTAMP(3), "
            + "version = version + 1, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND version = #{version} AND status = 'SENT'")
    int markConfirmed(@Param("id") long id, @Param("version") int version);

    /**
     * 候选人拒绝：SENT → DECLINED。
     *
     * @return 影响行数；0 表示已处理或版本冲突
     */
    @Update("UPDATE offer SET status = 'DECLINED', version = version + 1, "
            + "updated_at = CURRENT_TIMESTAMP(3) WHERE id = #{id} AND version = #{version} "
            + "AND status = 'SENT'")
    int markDeclined(@Param("id") long id, @Param("version") int version);

    /**
     * 过期：SENT 且已到期 → EXPIRED（多实例/重复扫描安全）。
     *
     * @return 影响行数；0 表示已被处理或未到期
     */
    @Update("UPDATE offer SET status = 'EXPIRED', version = version + 1, "
            + "updated_at = CURRENT_TIMESTAMP(3) WHERE id = #{id} AND version = #{version} "
            + "AND status = 'SENT' AND expire_at < CURRENT_TIMESTAMP(3)")
    int markExpired(@Param("id") long id, @Param("version") int version);

    /**
     * 到期未确认的 Offer（定时扫描）。
     *
     * @param limit 单批上限
     * @return 待过期行
     */
    @Select("SELECT * FROM offer WHERE status = 'SENT' AND expire_at < CURRENT_TIMESTAMP(3) "
            + "ORDER BY expire_at ASC LIMIT #{limit}")
    List<Offer> selectExpired(@Param("limit") int limit);
}
