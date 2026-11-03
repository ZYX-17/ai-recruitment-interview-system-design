package com.recruit.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.auth.entity.IamAuditLog;
import com.recruit.auth.mapper.row.AuditLogRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志 Mapper。
 *
 * <p>日志只增不改；管理端只做分页查询。登录日志与操作日志共用一张表，
 * 登录日志固定 {@code action = 'LOGIN'}。</p>
 */
public interface IamAuditLogMapper extends BaseMapper<IamAuditLog> {

    /**
     * 操作日志分页（左连用户带出操作者名称）。
     *
     * @param operatorId   操作者过滤，可为 null
     * @param action       动作过滤，可为 null
     * @param resourceType 资源类型过滤，可为 null
     * @param result       结果过滤，可为 null
     * @param ip           IP 过滤，可为 null
     * @param traceId      traceId 过滤，可为 null
     * @param startTime    起始时间（含），可为 null
     * @param endTime      结束时间（含），可为 null
     * @param limit        每页条数
     * @param offset       偏移量
     * @return 当前页日志
     */
    @Select("<script>SELECT l.id, l.operator_id, u.username AS operator_username, "
            + "u.display_name AS operator_name, l.action, l.resource_type, l.resource_id, "
            + "l.result, l.ip, l.trace_id, l.detail, l.created_at "
            + "FROM iam_audit_log l LEFT JOIN iam_user u ON u.id = l.operator_id WHERE 1 = 1 "
            + "<if test=\"operatorId != null\"> AND l.operator_id = #{operatorId}</if>"
            + "<if test=\"action != null and action != ''\"> AND l.action = #{action}</if>"
            + "<if test=\"resourceType != null and resourceType != ''\">"
            + " AND l.resource_type = #{resourceType}</if>"
            + "<if test=\"result != null and result != ''\"> AND l.result = #{result}</if>"
            + "<if test=\"ip != null and ip != ''\"> AND l.ip = #{ip}</if>"
            + "<if test=\"traceId != null and traceId != ''\"> AND l.trace_id = #{traceId}</if>"
            + "<if test=\"startTime != null\"> AND l.created_at &gt;= #{startTime}</if>"
            + "<if test=\"endTime != null\"> AND l.created_at &lt;= #{endTime}</if>"
            + " ORDER BY l.created_at DESC, l.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<AuditLogRow> selectAuditPage(@Param("operatorId") Long operatorId,
                                      @Param("action") String action,
                                      @Param("resourceType") String resourceType,
                                      @Param("result") String result,
                                      @Param("ip") String ip,
                                      @Param("traceId") String traceId,
                                      @Param("startTime") LocalDateTime startTime,
                                      @Param("endTime") LocalDateTime endTime,
                                      @Param("limit") long limit,
                                      @Param("offset") long offset);

    /**
     * 操作日志分页计数，条件与 {@link #selectAuditPage} 一致。
     *
     * @return 总记录数
     */
    @Select("<script>SELECT COUNT(1) FROM iam_audit_log l WHERE 1 = 1 "
            + "<if test=\"operatorId != null\"> AND l.operator_id = #{operatorId}</if>"
            + "<if test=\"action != null and action != ''\"> AND l.action = #{action}</if>"
            + "<if test=\"resourceType != null and resourceType != ''\">"
            + " AND l.resource_type = #{resourceType}</if>"
            + "<if test=\"result != null and result != ''\"> AND l.result = #{result}</if>"
            + "<if test=\"ip != null and ip != ''\"> AND l.ip = #{ip}</if>"
            + "<if test=\"traceId != null and traceId != ''\"> AND l.trace_id = #{traceId}</if>"
            + "<if test=\"startTime != null\"> AND l.created_at &gt;= #{startTime}</if>"
            + "<if test=\"endTime != null\"> AND l.created_at &lt;= #{endTime}</if>"
            + "</script>")
    long countAuditPage(@Param("operatorId") Long operatorId,
                        @Param("action") String action,
                        @Param("resourceType") String resourceType,
                        @Param("result") String result,
                        @Param("ip") String ip,
                        @Param("traceId") String traceId,
                        @Param("startTime") LocalDateTime startTime,
                        @Param("endTime") LocalDateTime endTime);

    /**
     * 登录日志分页（固定 {@code action = 'LOGIN'}）。
     *
     * @param username  登录名/显示名关键字，可为 null
     * @param result    OK/FAIL 过滤，可为 null
     * @param ip        IP 过滤，可为 null
     * @param traceId   traceId 过滤，可为 null
     * @param startTime 起始时间（含），可为 null
     * @param endTime   结束时间（含），可为 null
     * @param limit     每页条数
     * @param offset    偏移量
     * @return 当前页登录日志
     */
    @Select("<script>SELECT l.id, l.operator_id, u.username AS operator_username, "
            + "u.display_name AS operator_name, l.action, l.resource_type, l.resource_id, "
            + "l.result, l.ip, l.trace_id, l.detail, l.created_at "
            + "FROM iam_audit_log l LEFT JOIN iam_user u ON u.id = l.operator_id "
            + "WHERE l.action = 'LOGIN' "
            + "<if test=\"username != null and username != ''\">"
            + " AND (u.username LIKE CONCAT('%', #{username}, '%') "
            + "OR u.display_name LIKE CONCAT('%', #{username}, '%'))</if>"
            + "<if test=\"result != null and result != ''\"> AND l.result = #{result}</if>"
            + "<if test=\"ip != null and ip != ''\"> AND l.ip = #{ip}</if>"
            + "<if test=\"traceId != null and traceId != ''\"> AND l.trace_id = #{traceId}</if>"
            + "<if test=\"startTime != null\"> AND l.created_at &gt;= #{startTime}</if>"
            + "<if test=\"endTime != null\"> AND l.created_at &lt;= #{endTime}</if>"
            + " ORDER BY l.created_at DESC, l.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<AuditLogRow> selectLoginPage(@Param("username") String username,
                                      @Param("result") String result,
                                      @Param("ip") String ip,
                                      @Param("traceId") String traceId,
                                      @Param("startTime") LocalDateTime startTime,
                                      @Param("endTime") LocalDateTime endTime,
                                      @Param("limit") long limit,
                                      @Param("offset") long offset);

    /**
     * 登录日志分页计数，条件与 {@link #selectLoginPage} 一致。
     *
     * @return 总记录数
     */
    @Select("<script>SELECT COUNT(1) FROM iam_audit_log l "
            + "LEFT JOIN iam_user u ON u.id = l.operator_id WHERE l.action = 'LOGIN' "
            + "<if test=\"username != null and username != ''\">"
            + " AND (u.username LIKE CONCAT('%', #{username}, '%') "
            + "OR u.display_name LIKE CONCAT('%', #{username}, '%'))</if>"
            + "<if test=\"result != null and result != ''\"> AND l.result = #{result}</if>"
            + "<if test=\"ip != null and ip != ''\"> AND l.ip = #{ip}</if>"
            + "<if test=\"traceId != null and traceId != ''\"> AND l.trace_id = #{traceId}</if>"
            + "<if test=\"startTime != null\"> AND l.created_at &gt;= #{startTime}</if>"
            + "<if test=\"endTime != null\"> AND l.created_at &lt;= #{endTime}</if>"
            + "</script>")
    long countLoginPage(@Param("username") String username,
                        @Param("result") String result,
                        @Param("ip") String ip,
                        @Param("traceId") String traceId,
                        @Param("startTime") LocalDateTime startTime,
                        @Param("endTime") LocalDateTime endTime);
}
